import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    // Client data structure - stores client socket, username, and current room
    private static class ClientInfo {
        Socket socket;
        String username;
        String currentRoomId;
        boolean isAnonymous;

        public ClientInfo(Socket socket, String username, String currentRoomId, boolean isAnonymous) {
            this.socket = socket;
            this.username = username;
            this.currentRoomId = currentRoomId;
            this.isAnonymous = isAnonymous;
        }
    }

    // Map to store all connected clients
    private static final ConcurrentHashMap<Socket, ClientInfo> clients = new ConcurrentHashMap<>();

    // Set of all usernames to prevent duplicates
    private static final Set<String> usernames = Collections.synchronizedSet(new HashSet<>());

    // Database manager
    private static DatabaseManager dbManager;

    // Main method - start the server
    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(5000)) {
            // Initialize database
            dbManager = new DatabaseManager();

            System.out.println("Server started on port 5000");
            System.out.println("Waiting for clients to connect...");

            // Listen for connections
            while (true) {
                Socket connection = server.accept();
                System.out.println("New connection from " + connection.getInetAddress().getHostAddress() + ":"
                        + connection.getPort());

                // Handle client in a new thread
                new Thread(() -> handleClient(connection)).start();
            }
        } catch (IOException e) {
            System.out.println("Could not start server on port 5000");
            e.printStackTrace();
        } finally {
            if (dbManager != null) {
                dbManager.close();
            }
        }
    }

    // Handle a client connection
    private static void handleClient(Socket connection) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

            // Authentication protocol
            String loginType = in.readLine();
            String username = null;
            boolean isAnonymous = false;

            if ("ANONYMOUS".equals(loginType)) {
                // Anonymous login
                username = "Anon-" + UUID.randomUUID().toString().substring(0, 8);
                isAnonymous = true;

                // Register anonymous user
                dbManager.registerUser(username, "", true);

                // Send success response
                out.println("LOGIN_SUCCESS");
                out.println(username);

            } else if ("LOGIN".equals(loginType)) {
                // Regular login
                username = in.readLine();
                String password = in.readLine();

                if (dbManager.isValidUser(username, password)) {
                    out.println("LOGIN_SUCCESS");
                } else {
                    out.println("LOGIN_FAILED");
                    connection.close();
                    return;
                }

            } else if ("REGISTER".equals(loginType)) {
                // New user registration
                username = in.readLine();
                String password = in.readLine();

                if (dbManager.usernameExists(username)) {
                    out.println("REGISTER_FAILED");
                    connection.close();
                    return;
                } else {
                    dbManager.registerUser(username, password, false);
                    out.println("REGISTER_SUCCESS");
                }

            } else {
                // Invalid login type
                out.println("INVALID_LOGIN_TYPE");
                connection.close();
                return;
            }

            // Check if username is already connected
            if (usernames.contains(username)) {
                String originalUsername = username;
                int counter = 1;

                // For non-anonymous users, reject the connection
                if (!isAnonymous) {
                    out.println("USERNAME_TAKEN");
                    connection.close();
                    return;
                }

                // For anonymous users, append a number
                while (usernames.contains(username)) {
                    username = originalUsername + counter;
                    counter++;
                }
            }

            // Add username to active set
            usernames.add(username);

            // Get or create "General" room if it doesn't exist
            Room generalRoom = dbManager.getRoomByName("General");
            if (generalRoom == null) {
                generalRoom = dbManager.createRoom("General", "public");
            }

            // Add user to room
            dbManager.addUserToRoom(username, generalRoom.getId());

            // Create client info
            ClientInfo clientInfo = new ClientInfo(connection, username, generalRoom.getId(), isAnonymous);
            clients.put(connection, clientInfo);

            // Notify everyone in the room
            broadcastToRoom(username + " has joined the chat!", generalRoom.getId(), null);

            // Send room list to client
            sendRoomList(connection);

            // Send recent messages from this room
            sendRecentMessages(connection, generalRoom.getId());

            // Send user list for this room
            sendRoomUserList(connection, generalRoom.getId());

            // Process messages from this client
            String message;
            while ((message = in.readLine()) != null) {
                processClientMessage(connection, message);
            }

        } catch (SocketException e) {
            // Client disconnected unexpectedly
            handleDisconnect(connection);
        } catch (IOException e) {
            e.printStackTrace();
            handleDisconnect(connection);
        }
    }

    // Process a message from a client
    private static void processClientMessage(Socket connection, String message) throws IOException {
        ClientInfo clientInfo = clients.get(connection);
        if (clientInfo == null)
            return;

        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

        // Check for commands
        if (message.startsWith("/")) {
            String[] parts = message.split("\\s+", 2);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "/exit":
                    handleDisconnect(connection);
                    break;

                case "/rooms":
                    sendRoomList(connection);
                    break;

                case "/join":
                    if (parts.length > 1) {
                        joinRoom(connection, parts[1]);
                    } else {
                        out.println("Usage: /join <room_name> [access_code]");
                    }
                    break;

                case "/create":
                    if (parts.length > 1) {
                        String[] roomParts = parts[1].split("\\s+", 2);
                        String roomName = roomParts[0];
                        String accessCode = roomParts.length > 1 ? roomParts[1] : "public";
                        createRoom(connection, roomName, accessCode);
                    } else {
                        out.println("Usage: /create <room_name> [access_code]");
                    }
                    break;

                case "/users":
                    sendRoomUserList(connection, clientInfo.currentRoomId);
                    break;

                case "/help":
                    sendHelpMessage(connection);
                    break;

                default:
                    out.println("Unknown command. Type /help for available commands.");
                    break;
            }
        } else {
            // Regular message - broadcast to room
            broadcastToRoom(message, clientInfo.currentRoomId, clientInfo.username);

            // Store message in database
            dbManager.storeMessage(clientInfo.currentRoomId, clientInfo.username, message);
        }
    }

    // Send help information to client
    private static void sendHelpMessage(Socket connection) throws IOException {
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);
        out.println("Available commands:");
        out.println("/rooms - List all available rooms");
        out.println("/join <room_name> [access_code] - Join a room (provide access code if required)");
        out.println("/create <room_name> [access_code] - Create a new room with optional access code");
        out.println("/users - Show users in current room");
        out.println("/exit - Disconnect from server");
        out.println("/help - Show this help message");
    }

    // Handle client disconnection
    private static void handleDisconnect(Socket connection) {
        ClientInfo clientInfo = clients.get(connection);
        if (clientInfo == null)
            return;

        System.out.println(clientInfo.username + " has disconnected.");

        // Notify everyone in the room
        try {
            broadcastToRoom(clientInfo.username + " has left the chat!", clientInfo.currentRoomId, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Remove user from room in database
        dbManager.removeUserFromRoom(clientInfo.username, clientInfo.currentRoomId);

        // Update last seen timestamp
        dbManager.updateUserLastSeen(clientInfo.username);

        // Clean up collections
        clients.remove(connection);
        usernames.remove(clientInfo.username);

        // Close connection
        try {
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create a new room
    private static void createRoom(Socket connection, String roomName, String accessCode) throws IOException {
        ClientInfo clientInfo = clients.get(connection);
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

        // Check if room name already exists
        if (dbManager.getRoomByName(roomName) != null) {
            out.println("Room name already exists. Please choose another name.");
            return;
        }

        // Create room in database
        Room room = dbManager.createRoom(roomName, accessCode);
        if (room == null) {
            out.println("Failed to create room.");
            return;
        }

        out.println("Room '" + roomName + "' created successfully!");

        // Broadcast updated room list to all clients
        for (Socket clientSocket : clients.keySet()) {
            try {
                sendRoomList(clientSocket);
            } catch (IOException e) {
                // Skip clients with connection issues
                System.err.println("Failed to send room list to a client: " + e.getMessage());
            }
        }

        // Join the newly created room
        joinRoom(connection, roomName + " " + accessCode);
    }

    // Join a room
    private static void joinRoom(Socket connection, String params) throws IOException {
        ClientInfo clientInfo = clients.get(connection);
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

        String[] parts = params.split("\\s+", 2);
        String roomName = parts[0];
        String accessCode = parts.length > 1 ? parts[1] : "";

        // Find room by name
        Room room = dbManager.getRoomByName(roomName);
        if (room == null) {
            out.println("Room '" + roomName + "' does not exist.");
            return;
        }

        // Check access code if not "public"
        if (!"public".equals(room.getAccessCode()) && !room.verifyAccessCode(accessCode)) {
            out.println("Invalid access code for room '" + roomName + "'.");
            return;
        }

        // Leave current room
        String oldRoomId = clientInfo.currentRoomId;
        Room oldRoom = dbManager.getRoomById(oldRoomId);

        // Notify users in old room
        broadcastToRoom(clientInfo.username + " has left the room.", oldRoomId, null);

        // Remove from old room in database
        dbManager.removeUserFromRoom(clientInfo.username, oldRoomId);

        // Update client's current room
        clientInfo.currentRoomId = room.getId();

        // Add to new room in database
        dbManager.addUserToRoom(clientInfo.username, room.getId());

        // Notify user
        out.println("You have joined room '" + roomName + "'.");

        // Notify users in new room
        broadcastToRoom(clientInfo.username + " has joined the room.", room.getId(), null);

        // Send recent messages from this room
        sendRecentMessages(connection, room.getId());

        // Send updated user list for new room
        sendRoomUserList(connection, room.getId());
    }

    // Send list of all available rooms
    private static void sendRoomList(Socket connection) throws IOException {
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

        List<Room> rooms = dbManager.getAllRooms();

        out.println("ROOM_LIST_BEGIN");
        out.println("Available rooms:");

        for (Room room : rooms) {
            String lockStatus = "public".equals(room.getAccessCode()) ? "🔓" : "🔒";
            out.println(room.getName() + " " + lockStatus);
        }

        out.println("ROOM_LIST_END");
    }

    // Send recent messages from a room
    private static void sendRecentMessages(Socket connection, String roomId) throws IOException {
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

        List<String> messages = dbManager.getRecentMessages(roomId, 20);

        out.println("CHAT_HISTORY_BEGIN");

        if (messages.isEmpty()) {
            out.println("No previous messages in this room.");
        } else {
            for (String message : messages) {
                out.println(message);
            }
        }

        out.println("CHAT_HISTORY_END");
    }

    // Send list of users in a room
    private static void sendRoomUserList(Socket connection, String roomId) throws IOException {
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

        // Get all usernames in the room from database
        List<String> roomUsers = dbManager.getUsersInRoom(roomId);

        // Add currently connected users
        Set<String> onlineUsers = new HashSet<>();
        for (ClientInfo info : clients.values()) {
            if (roomId.equals(info.currentRoomId)) {
                onlineUsers.add(info.username);
            }
        }

        out.println("USER_LIST_BEGIN");

        // First send online users
        for (String user : roomUsers) {
            if (onlineUsers.contains(user)) {
                out.println(user + " (online)");
            }
        }

        // Then send offline users
        for (String user : roomUsers) {
            if (!onlineUsers.contains(user)) {
                out.println(user + " (offline)");
            }
        }

        out.println("USER_LIST_END");
    }

    // Broadcast a message to all clients in a room
    private static void broadcastToRoom(String message, String roomId, String senderName) throws IOException {
        String formattedMessage;

        if (senderName != null) {
            formattedMessage = senderName + ": " + message;
        } else {
            formattedMessage = message;
        }

        for (Map.Entry<Socket, ClientInfo> entry : clients.entrySet()) {
            ClientInfo info = entry.getValue();

            // Only send to clients in the same room
            if (roomId.equals(info.currentRoomId)) {
                try {
                    PrintWriter out = new PrintWriter(info.socket.getOutputStream(), true);
                    out.println(formattedMessage);
                } catch (SocketException e) {
                    // Skip clients with connection issues
                }
            }
        }
    }
}