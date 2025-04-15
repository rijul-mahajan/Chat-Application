import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private Connection connection;
    private final String dbUrl = "jdbc:sqlite:chat_app.db";

    public DatabaseManager() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            connect();
            initializeTables();
        } catch (ClassNotFoundException e) {
            System.err.println(
                    "SQLite JDBC Driver not found. Make sure you have sqlite-jdbc-3.x.x.jar in your classpath.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connect() throws SQLException {
        connection = DriverManager.getConnection(dbUrl);
        connection.setAutoCommit(true);
    }

    private void initializeTables() throws SQLException {
        // Create users table
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY NOT NULL, " +
                "password TEXT, " +
                "is_anonymous BOOLEAN NOT NULL DEFAULT 0, " +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        // Create rooms table
        String createRoomsTable = "CREATE TABLE IF NOT EXISTS rooms (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "name TEXT NOT NULL, " +
                "access_code TEXT NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        // Create messages table
        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "room_id TEXT NOT NULL, " +
                "username TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (room_id) REFERENCES rooms(id), " +
                "FOREIGN KEY (username) REFERENCES users(username)" +
                ")";

        // Create room members table
        String createRoomMembersTable = "CREATE TABLE IF NOT EXISTS room_members (" +
                "room_id TEXT NOT NULL, " +
                "username TEXT NOT NULL, " +
                "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (room_id, username), " +
                "FOREIGN KEY (room_id) REFERENCES rooms(id), " +
                "FOREIGN KEY (username) REFERENCES users(username)" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createRoomsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createRoomMembersTable);
        }

        // Create default general room if it doesn't exist
        if (getRoomByName("General") == null) {
            createRoom("General", "public");
        }
    }

    // User methods
    public boolean registerUser(String username, String password, boolean isAnonymous) {
        String sql = "INSERT INTO users(username, password, is_anonymous) VALUES(?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setBoolean(3, isAnonymous);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error registering user: " + e.getMessage());
            return false;
        }
    }

    public boolean isValidUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ? AND is_anonymous = 0";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error validating user: " + e.getMessage());
            return false;
        }
    }

    public boolean usernameExists(String username) {
        String sql = "SELECT username FROM users WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error checking username: " + e.getMessage());
            return false;
        }
    }

    public void updateUserLastSeen(String username) {
        String sql = "UPDATE users SET last_seen = CURRENT_TIMESTAMP WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user last seen: " + e.getMessage());
        }
    }

    // Room methods
    public Room createRoom(String name, String accessCode) {
        String sql = "INSERT INTO rooms(id, name, access_code) VALUES(?, ?, ?)";

        Room room = new Room(name, accessCode);

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, room.getId());
            pstmt.setString(2, name);
            pstmt.setString(3, accessCode);
            pstmt.executeUpdate();
            return room;
        } catch (SQLException e) {
            System.err.println("Error creating room: " + e.getMessage());
            return null;
        }
    }

    public Room getRoomById(String roomId) {
        String sql = "SELECT id, name, access_code FROM rooms WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomId);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Room(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("access_code"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting room: " + e.getMessage());
        }

        return null;
    }

    public Room getRoomByName(String roomName) {
        String sql = "SELECT id, name, access_code FROM rooms WHERE name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Room(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("access_code"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting room by name: " + e.getMessage());
        }

        return null;
    }

    public List<Room> getAllRooms() {
        String sql = "SELECT id, name, access_code FROM rooms";
        List<Room> rooms = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                rooms.add(new Room(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("access_code")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all rooms: " + e.getMessage());
        }

        return rooms;
    }

    // Room membership methods
    public boolean addUserToRoom(String username, String roomId) {
        String sql = "INSERT INTO room_members(room_id, username) VALUES(?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding user to room: " + e.getMessage());
            return false;
        }
    }

    public boolean removeUserFromRoom(String username, String roomId) {
        String sql = "DELETE FROM room_members WHERE room_id = ? AND username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error removing user from room: " + e.getMessage());
            return false;
        }
    }

    public List<String> getUsersInRoom(String roomId) {
        String sql = "SELECT username FROM room_members WHERE room_id = ?";
        List<String> users = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomId);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting users in room: " + e.getMessage());
        }

        return users;
    }

    public List<Room> getRoomsForUser(String username) {
        String sql = "SELECT r.id, r.name, r.access_code FROM rooms r " +
                "INNER JOIN room_members rm ON r.id = rm.room_id " +
                "WHERE rm.username = ?";
        List<Room> rooms = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                rooms.add(new Room(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("access_code")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting rooms for user: " + e.getMessage());
        }

        return rooms;
    }

    // Message methods
    public boolean storeMessage(String roomId, String username, String content) {
        String sql = "INSERT INTO messages(room_id, username, content) VALUES(?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomId);
            pstmt.setString(2, username);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error storing message: " + e.getMessage());
            return false;
        }
    }

    public List<String> getRecentMessages(String roomId, int limit) {
        String sql = "SELECT username, content, timestamp FROM messages " +
                "WHERE room_id = ? ORDER BY timestamp DESC LIMIT ?";
        List<String> messages = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roomId);
            pstmt.setInt(2, limit);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("timestamp");
                String username = rs.getString("username");
                String content = rs.getString("content");

                // Format: [timestamp] username: content
                messages.add(0, "[" + timestamp + "] " + username + ": " + content);
            }
        } catch (SQLException e) {
            System.err.println("Error getting recent messages: " + e.getMessage());
        }

        return messages;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}