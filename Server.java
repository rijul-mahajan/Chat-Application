import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    // Stores connected clients and their names - using concurrent map for thread
    // safety
    private static final ConcurrentHashMap<Socket, String> clients = new ConcurrentHashMap<>();
    // Set to keep track of usernames - helps avoid duplicate names
    private static final Set<String> usernames = Collections.synchronizedSet(new HashSet<>());

    // Listens for new client connections
    public static void listenIncomingConnections(ServerSocket serverSocket) throws IOException {
        System.out.println("Server started on port 5000");
        System.out.println("Waiting for clients to connect...");

        while (true) {
            Socket connection = serverSocket.accept();
            System.out.println(
                    "New connection from " + connection.getInetAddress().getHostAddress() + ":" + connection.getPort());

            // Start a new thread to handle this client
            new Thread(() -> handleClient(connection)).start();
        }
    }

    // Handles a single client connection
    private static void handleClient(Socket connection) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

            // Get the client's username
            String username = in.readLine();

            // If username already exists, add a number suffix
            String originalUsername = username;
            int counter = 1;
            while (usernames.contains(username)) {
                username = originalUsername + counter;
                counter++;
            }

            // Add username to the set
            usernames.add(username);

            // Notify others that a new client has joined
            broadcast(username + " has joined the chat!", null);

            // Add client to the map
            clients.put(connection, username);

            // Send the current user list to the new client
            sendUserList(connection);

            // Process messages from this client
            String message;
            String finalUsername = username; // Needed for lambda expressions

            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("exit")) {
                    break;
                } else if (message.equals("GET_USER_LIST")) {
                    // Client is requesting an updated user list
                    sendUserList(connection);
                    continue;
                }

                System.out.println(finalUsername + ": " + message);
                broadcast(message, finalUsername);
            }

            // Client has disconnected
            handleDisconnect(connection, finalUsername);

        } catch (SocketException e) {
            // Connection was lost
            String username = clients.get(connection);
            if (username != null) {
                handleDisconnect(connection, username);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle client disconnection
    private static void handleDisconnect(Socket connection, String username) {
        System.out.println(username + " has disconnected.");
        clients.remove(connection);
        usernames.remove(username);
        try {
            broadcast(username + " has left the chat!", null);
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcasts a message to all connected clients
    public static void broadcast(String message, String senderName) throws IOException {
        for (Map.Entry<Socket, String> entry : clients.entrySet()) {
            try {
                PrintWriter out = new PrintWriter(entry.getKey().getOutputStream(), true);
                if (senderName != null) {
                    out.println(senderName + ": " + message);
                } else {
                    out.println(message);
                }
            } catch (SocketException e) {
                // Skip clients with connection issues
            }
        }
    }

    // Sends the current user list to a specific client
    private static void sendUserList(Socket client) {
        try {
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);

            // Begin user list marker
            out.println("USER_LIST_BEGIN");

            // Send each username
            for (String username : usernames) {
                out.println(username);
            }

            // End user list marker
            out.println("USER_LIST_END");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            ServerSocket server = new ServerSocket(5000);
            listenIncomingConnections(server);
        } catch (IOException e) {
            System.out.println("Could not start server on port 5000");
            e.printStackTrace();
        }
    }
}