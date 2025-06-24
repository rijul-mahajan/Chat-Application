package src;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Server extends JFrame {
    // Client data structure - stores client socket, username, and current room
    private static class ClientInfo {
        Socket socket;
        String username;
        String currentRoomId;
        @SuppressWarnings("unused")
        boolean isAnonymous;

        public ClientInfo(Socket socket, String username, String currentRoomId, boolean isAnonymous) {
            this.socket = socket;
            this.username = username;
            this.currentRoomId = currentRoomId;
            this.isAnonymous = isAnonymous;
        }
    }

    private static final ConcurrentHashMap<Socket, ClientInfo> clients = new ConcurrentHashMap<>();
    private static final Set<String> usernames = Collections.synchronizedSet(new HashSet<>());
    private static DatabaseManager dbManager;

    // GUI Components
    private JTextArea logArea;
    private JTextField portField;
    private JTextField ipField;
    private JButton startButton;
    private JButton stopButton;
    private JButton restartButton;
    private JLabel statusLabel;
    private JLabel connectionCountLabel;

    // Server state
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private int currentPort = 5000;
    private String currentIP = "localhost";
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Server() {
        // Load the icon
        URL iconURL = getClass().getResource("images/server.png");
        if (iconURL != null) {
            ImageIcon icon = new ImageIcon(iconURL);
            this.setIconImage(icon.getImage());
        } else {
            System.err.println("Icon not found.");
        }

        initializeGUI();
        // Initialize database
        try {
            dbManager = new DatabaseManager();
        } catch (Exception e) {
            logMessage("ERROR: Failed to initialize database: " + e.getMessage());
        }
    }

    private void initializeGUI() {
        setTitle("OpenChat Server - Control Panel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel for controls
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Server Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // IP Address field
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(new JLabel("IP Address:"), gbc);
        gbc.gridx = 1;
        ipField = new JTextField(currentIP, 15);
        controlPanel.add(ipField, gbc);

        // Port field
        gbc.gridx = 2;
        gbc.gridy = 0;
        controlPanel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 3;
        portField = new JTextField(String.valueOf(currentPort), 8);
        controlPanel.add(portField, gbc);

        // Buttons
        gbc.gridx = 0;
        gbc.gridy = 1;
        startButton = new JButton("Start Server");
        startButton.addActionListener(_ -> startServer());
        controlPanel.add(startButton, gbc);

        gbc.gridx = 1;
        stopButton = new JButton("Stop Server");
        stopButton.addActionListener(_ -> stopServer());
        stopButton.setEnabled(false);
        controlPanel.add(stopButton, gbc);

        gbc.gridx = 2;
        restartButton = new JButton("Restart Server");
        restartButton.addActionListener(_ -> restartServer());
        restartButton.setEnabled(false);
        controlPanel.add(restartButton, gbc);

        gbc.gridx = 3;
        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(_ -> logArea.setText(""));
        controlPanel.add(clearLogButton, gbc);

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Server Status"));
        statusLabel = new JLabel("Status: Stopped");
        statusLabel.setForeground(Color.RED);
        connectionCountLabel = new JLabel("Connections: 0");
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalStrut(20));
        statusPanel.add(connectionCountLabel);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        scrollPane.setPreferredSize(new Dimension(780, 400));

        // Add components to main panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(controlPanel, BorderLayout.NORTH);
        topPanel.add(statusPanel, BorderLayout.SOUTH);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        add(mainPanel);

        // Initial log message
        logMessage("OpenChat Server Control Panel initialized");
        logMessage("Ready to start server...");
    }

    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(dateFormatter);
            String logEntry = "[" + timestamp + "] " + message + "\n";
            logArea.append(logEntry);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void updateConnectionCount() {
        SwingUtilities.invokeLater(() -> {
            connectionCountLabel.setText("Connections: " + clients.size());
        });
    }

    private void startServer() {
        if (isRunning)
            return;

        try {
            currentPort = Integer.parseInt(portField.getText().trim());
            currentIP = ipField.getText().trim();

            if (currentPort < 1024 || currentPort > 65535) {
                throw new NumberFormatException("Port must be between 1024 and 65535");
            }

            // Create server socket
            if ("localhost".equals(currentIP) || "127.0.0.1".equals(currentIP)) {
                serverSocket = new ServerSocket(currentPort);
            } else {
                InetAddress bindAddr = InetAddress.getByName(currentIP);
                serverSocket = new ServerSocket(currentPort, 50, bindAddr);
            }

            isRunning = true;

            // Update GUI
            SwingUtilities.invokeLater(() -> {
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                restartButton.setEnabled(true);
                statusLabel.setText("Status: Running");
                statusLabel.setForeground(Color.GREEN);
                portField.setEnabled(false);
                ipField.setEnabled(false);
            });

            logMessage("Server started successfully");
            logMessage("Listening on " + currentIP + ":" + currentPort);
            logMessage("Waiting for client connections...");

            // Start accepting connections in a separate thread
            new Thread(this::acceptConnections).start();

        } catch (NumberFormatException e) {
            logMessage("ERROR: Invalid port number - " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Invalid port number. Please enter a valid port (1024-65535).",
                    "Invalid Port", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            logMessage("ERROR: Failed to start server - " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to start server: " + e.getMessage(),
                    "Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (!isRunning)
            return;

        isRunning = false;

        try {
            // Close all client connections
            for (Socket clientSocket : clients.keySet()) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logMessage("ERROR: Failed to close client connection - " + e.getMessage());
                }
            }
            clients.clear();
            usernames.clear();

            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Update GUI
            SwingUtilities.invokeLater(() -> {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                restartButton.setEnabled(false);
                statusLabel.setText("Status: Stopped");
                statusLabel.setForeground(Color.RED);
                portField.setEnabled(true);
                ipField.setEnabled(true);
            });

            updateConnectionCount();
            logMessage("Server stopped successfully");

        } catch (IOException e) {
            logMessage("ERROR: Error while stopping server - " + e.getMessage());
        }
    }

    private void restartServer() {
        logMessage("Restarting server...");
        stopServer();

        // Wait a moment for cleanup
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        startServer();
    }

    private void acceptConnections() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket connection = serverSocket.accept();
                String clientInfo = connection.getInetAddress().getHostAddress() + ":" + connection.getPort();
                logMessage("New connection from " + clientInfo);

                // Handle client in a new thread
                new Thread(() -> handleClient(connection)).start();

            } catch (IOException e) {
                if (isRunning) {
                    logMessage("ERROR: Error accepting connection - " + e.getMessage());
                }
            }
        }
    }

    // Handle a client connection
    private void handleClient(Socket connection) {
        String clientAddress = connection.getInetAddress().getHostAddress() + ":" + connection.getPort();

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

                logMessage("Anonymous user '" + username + "' logged in from " + clientAddress);

            } else if ("LOGIN".equals(loginType)) {
                // Regular login
                username = in.readLine();
                String password = in.readLine();

                if (dbManager.isValidUser(username, password)) {
                    out.println("LOGIN_SUCCESS");
                    logMessage("User '" + username + "' logged in from " + clientAddress);
                } else {
                    out.println("LOGIN_FAILED");
                    logMessage("Failed login attempt for '" + username + "' from " + clientAddress);
                    connection.close();
                    return;
                }

            } else if ("REGISTER".equals(loginType)) {
                // New user registration
                username = in.readLine();
                String password = in.readLine();

                if (dbManager.usernameExists(username)) {
                    out.println("REGISTER_FAILED");
                    logMessage("Registration failed - username '" + username + "' already exists (from " + clientAddress
                            + ")");
                    connection.close();
                    return;
                } else {
                    dbManager.registerUser(username, password, false);
                    out.println("REGISTER_SUCCESS");
                    logMessage("New user '" + username + "' registered from " + clientAddress);
                }

            } else {
                // Invalid login type
                out.println("INVALID_LOGIN_TYPE");
                logMessage("Invalid login type from " + clientAddress);
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
                    logMessage(
                            "User '" + username + "' already connected - rejecting connection from " + clientAddress);
                    connection.close();
                    return;
                }

                // For anonymous users, append a number
                while (usernames.contains(username)) {
                    username = originalUsername + counter;
                    counter++;
                }
                logMessage("Anonymous username collision resolved: " + originalUsername + " -> " + username);
            }

            // Add username to active set
            usernames.add(username);

            // Get or create "General" room if it doesn't exist
            Room generalRoom = dbManager.getRoomByName("General");
            if (generalRoom == null) {
                generalRoom = dbManager.createRoom("General", "public");
                logMessage("Created default 'General' room");
            }

            // Add user to room
            dbManager.addUserToRoom(username, generalRoom.getId());

            // Create client info
            ClientInfo clientInfo = new ClientInfo(connection, username, generalRoom.getId(), isAnonymous);
            clients.put(connection, clientInfo);

            updateConnectionCount();
            logMessage("User '" + username + "' joined General room");

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
            logMessage("ERROR: I/O error with client " + clientAddress + " - " + e.getMessage());
            handleDisconnect(connection);
        }
    }

    // Process a message from a client
    private void processClientMessage(Socket connection, String message) throws IOException {
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
                    logMessage("User '" + clientInfo.username + "' requested room list");
                    break;

                case "/join":
                    if (parts.length > 1) {
                        logMessage("User '" + clientInfo.username + "' attempting to join room: " + parts[1]);
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
                        logMessage("User '" + clientInfo.username + "' creating room: " + roomName);
                        createRoom(connection, roomName, accessCode);
                    } else {
                        out.println("Usage: /create <room_name> [access_code]");
                    }
                    break;

                case "/users":
                    sendRoomUserList(connection, clientInfo.currentRoomId);
                    logMessage("User '" + clientInfo.username + "' requested user list");
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

            // Log message (truncate if too long)
            String logMsg = message.length() > 50 ? message.substring(0, 50) + "..." : message;
            logMessage("Message from '" + clientInfo.username + "': " + logMsg);
        }
    }

    // Send help information to client
    private void sendHelpMessage(Socket connection) throws IOException {
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
    private void handleDisconnect(Socket connection) {
        ClientInfo clientInfo = clients.get(connection);
        if (clientInfo == null)
            return;

        String clientAddress = connection.getInetAddress().getHostAddress() + ":" + connection.getPort();
        logMessage("User '" + clientInfo.username + "' disconnected from " + clientAddress);

        // Notify everyone in the room
        try {
            broadcastToRoom(clientInfo.username + " has left the chat!", clientInfo.currentRoomId, null);
        } catch (IOException e) {
            logMessage("ERROR: Failed to broadcast disconnect message - " + e.getMessage());
        }

        // Remove user from room in database
        dbManager.removeUserFromRoom(clientInfo.username, clientInfo.currentRoomId);

        // Update last seen timestamp
        dbManager.updateUserLastSeen(clientInfo.username);

        // Clean up collections
        clients.remove(connection);
        usernames.remove(clientInfo.username);

        updateConnectionCount();

        // Close connection
        try {
            connection.close();
        } catch (IOException e) {
            logMessage("ERROR: Failed to close connection - " + e.getMessage());
        }
    }

    // Create a new room
    private void createRoom(Socket connection, String roomName, String accessCode) throws IOException {
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
            logMessage("ERROR: Failed to create room '" + roomName + "'");
            return;
        }

        out.println("Room '" + roomName + "' created successfully!");
        logMessage("Room '" + roomName + "' created successfully with access code: " + accessCode);

        // Broadcast updated room list to all clients
        for (Socket clientSocket : clients.keySet()) {
            try {
                sendRoomList(clientSocket);
            } catch (IOException e) {
                // Skip clients with connection issues
                logMessage("ERROR: Failed to send room list to a client - " + e.getMessage());
            }
        }

        // Join the newly created room
        joinRoom(connection, roomName + " " + accessCode);
    }

    // Join a room
    private void joinRoom(Socket connection, String params) throws IOException {
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
            logMessage(
                    "User '" + clientInfo.username + "' failed to join room '" + roomName + "' - invalid access code");
            return;
        }

        // Leave current room
        String oldRoomId = clientInfo.currentRoomId;
        Room oldRoom = dbManager.getRoomById(oldRoomId);
        String oldRoomName = oldRoom != null ? oldRoom.getName() : "Unknown";

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
        logMessage("User '" + clientInfo.username + "' moved from '" + oldRoomName + "' to '" + roomName + "'");

        // Notify users in new room
        broadcastToRoom(clientInfo.username + " has joined the room.", room.getId(), null);

        // Send recent messages from this room
        sendRecentMessages(connection, room.getId());

        // Send updated user list for new room
        sendRoomUserList(connection, room.getId());
    }

    // Send list of all available rooms
    private void sendRoomList(Socket connection) throws IOException {
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
    private void sendRecentMessages(Socket connection, String roomId) throws IOException {
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
    private void sendRoomUserList(Socket connection, String roomId) throws IOException {
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
    private void broadcastToRoom(String message, String roomId, String senderName) throws IOException {
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
                    logMessage("ERROR: Failed to send message to client - connection issue");
                }
            }
        }
    }

    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel
        }

        SwingUtilities.invokeLater(() -> {
            Server server = new Server();
            server.setVisible(true);
        });
    }
}