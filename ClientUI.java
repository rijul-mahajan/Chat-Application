import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class ClientUI extends JFrame {
    private JTextField messageField;
    private JTextArea chatArea;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;
    private JTabbedPane sidePanel;
    private String username;
    private String currentRoom = "General";
    private boolean isAnonymous = false;
    private Socket client;
    private PrintWriter out;
    private BufferedReader in;
    private JPanel headerPanel;
    private final Color PRIMARY_COLOR = new Color(70, 130, 180); // Steel blue
    private final Font MAIN_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    public ClientUI() {
        // Set application icon
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("icon.png"));
            setIconImage(icon.getImage());
        } catch (Exception e) {
            System.err.println("Could not load icon: " + e.getMessage());
        }
        showLoginDialog();
    }

    private void showLoginDialog() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Logo/Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel logoLabel = new JLabel("OpenChat");
        logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        logoLabel.setForeground(PRIMARY_COLOR);
        headerPanel.add(logoLabel);

        // Tab panel for login/register options
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(MAIN_FONT);

        // Login panel
        JPanel loginPanel = new JPanel(new GridLayout(0, 1, 5, 10));
        JTextField loginUsernameField = new JTextField();
        JPasswordField loginPasswordField = new JPasswordField();
        JButton loginButton = new JButton("Login");
        styleButton(loginButton);

        loginPanel.add(new JLabel("Username:"));
        loginPanel.add(loginUsernameField);
        loginPanel.add(new JLabel("Password:"));
        loginPanel.add(loginPasswordField);
        loginPanel.add(Box.createVerticalStrut(10));
        loginPanel.add(loginButton);

        // Register panel
        JPanel registerPanel = new JPanel(new GridLayout(0, 1, 5, 10));
        JTextField regUsernameField = new JTextField();
        JPasswordField regPasswordField = new JPasswordField();
        JPasswordField regConfirmPasswordField = new JPasswordField();
        JButton registerButton = new JButton("Register");
        styleButton(registerButton);

        registerPanel.add(new JLabel("Username:"));
        registerPanel.add(regUsernameField);
        registerPanel.add(new JLabel("Password:"));
        registerPanel.add(regPasswordField);
        registerPanel.add(new JLabel("Confirm Password:"));
        registerPanel.add(regConfirmPasswordField);
        registerPanel.add(Box.createVerticalStrut(10));
        registerPanel.add(registerButton);

        // Anonymous login
        JPanel anonymousPanel = new JPanel(new GridLayout(0, 1, 5, 10));
        JLabel anonInfoLabel = new JLabel(
                "<html>Join anonymously without creating an account.<br>Your temporary username will be assigned by the server.</html>");
        anonInfoLabel.setFont(MAIN_FONT);
        JButton anonymousButton = new JButton("Join Anonymously");
        styleButton(anonymousButton);

        anonymousPanel.add(anonInfoLabel);
        anonymousPanel.add(Box.createVerticalStrut(10));
        anonymousPanel.add(anonymousButton);

        // Add panels to tabbed pane
        tabbedPane.addTab("Login", loginPanel);
        tabbedPane.addTab("Register", registerPanel);
        tabbedPane.addTab("Anonymous", anonymousPanel);

        // Add components to main panel
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(tabbedPane, BorderLayout.CENTER);

        // Create dialog
        JDialog dialog = new JDialog((Frame) null, "OpenChat Login", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setSize(400, 450);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);

        // Login button action
        loginButton.addActionListener(e -> {
            String username = loginUsernameField.getText().trim();
            String password = new String(loginPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Username and password are required",
                        "Login Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            dialog.dispose();
            getServerInfo(username, password, "LOGIN");
        });

        // Register button action
        registerButton.addActionListener(e -> {
            String username = regUsernameField.getText().trim();
            String password = new String(regPasswordField.getPassword());
            String confirmPassword = new String(regConfirmPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "All fields are required",
                        "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(dialog, "Passwords do not match",
                        "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            dialog.dispose();
            getServerInfo(username, password, "REGISTER");
        });

        // Anonymous button action
        anonymousButton.addActionListener(e -> {
            dialog.dispose();
            isAnonymous = true;
            getServerInfo("", "", "ANONYMOUS");
        });

        dialog.setVisible(true);
    }

    private void styleButton(JButton button) {
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(MAIN_FONT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void getServerInfo(String username, String password, String loginType) {
        // Create a panel for connection dialog
        JPanel panel = new JPanel(new GridLayout(0, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create text field for server address
        JTextField serverField = new JTextField("localhost");

        // Add components to panel
        panel.add(new JLabel("Server IP:"));
        panel.add(serverField);

        // Show the connection dialog
        int result = JOptionPane.showConfirmDialog(null, panel,
                "Connect to Chat Server", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String serverAddress = serverField.getText().trim();
            this.username = username;

            // Validate input
            if (serverAddress.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Server address is required!",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                getServerInfo(username, password, loginType);
                return;
            }

            try {
                int port = 5000; // Hardcode the port to match Server.java
                connectToServer(serverAddress, port, username, password, loginType);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid server configuration!",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                getServerInfo(username, password, loginType);
            }
        } else {
            System.exit(0); // User cancelled
        }
    }

    private void connectToServer(String serverAddress, int port, String username, String password, String loginType) {
        try {
            client = new Socket(serverAddress, port);
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // Send login type first
            out.println(loginType);

            // For anonymous login, server will generate username
            if (loginType.equals("ANONYMOUS")) {
                // Receive login response
                String response = in.readLine();

                if (response.equals("LOGIN_SUCCESS")) {
                    // For anonymous users, server sends the generated username
                    this.username = in.readLine();
                    setupUI();
                    startMessageListener();
                } else {
                    JOptionPane.showMessageDialog(this, "Anonymous login failed. Please try again.",
                            "Login Error", JOptionPane.ERROR_MESSAGE);
                    showLoginDialog();
                }
            }
            // For regular login
            else if (loginType.equals("LOGIN")) {
                out.println(username);
                out.println(password);

                String response = in.readLine();

                if (response.equals("LOGIN_SUCCESS")) {
                    this.username = username;
                    setupUI();
                    startMessageListener();
                } else if (response.equals("USERNAME_TAKEN")) {
                    JOptionPane.showMessageDialog(this, "This user is already logged in.",
                            "Login Error", JOptionPane.ERROR_MESSAGE);
                    showLoginDialog();
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username or password. Please try again.",
                            "Login Error", JOptionPane.ERROR_MESSAGE);
                    showLoginDialog();
                }
            }
            // For registration
            else if (loginType.equals("REGISTER")) {
                out.println(username);
                out.println(password);

                String response = in.readLine();

                if (response.equals("REGISTER_SUCCESS")) {
                    this.username = username;
                    setupUI();
                    startMessageListener();
                } else {
                    JOptionPane.showMessageDialog(this, "Username already exists. Please choose another one.",
                            "Registration Error", JOptionPane.ERROR_MESSAGE);
                    showLoginDialog();
                }
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server at " + serverAddress + ":" + port,
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            showLoginDialog();
        }
    }

    private void setupUI() {
        String userStatus = isAnonymous ? " (Anonymous)" : "";
        setTitle("OpenChat - Connected as " + username + userStatus);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main container with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(240, 240, 240));

        // Header panel
        headerPanel = new JPanel(new BorderLayout()); // Assign to field instead of local variable
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel("OpenChat");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        JLabel usernameLabel = new JLabel("Logged in as: " + username + userStatus);
        usernameLabel.setFont(MAIN_FONT);
        usernameLabel.setForeground(new Color(230, 230, 230));

        JLabel roomLabel = new JLabel("Room: " + currentRoom);
        roomLabel.setFont(MAIN_FONT);
        roomLabel.setForeground(new Color(230, 230, 230));

        JPanel userInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        userInfoPanel.setOpaque(false);
        userInfoPanel.add(roomLabel);
        userInfoPanel.add(usernameLabel);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(userInfoPanel, BorderLayout.EAST);

        // Chat panel (center)
        JPanel chatPanel = new JPanel(new BorderLayout(0, 10));
        chatPanel.setBackground(new Color(240, 240, 240));

        chatArea = new JTextArea();
        chatArea.setFont(MAIN_FONT);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        // Message input panel
        JPanel messagePanel = new JPanel(new BorderLayout(10, 0));
        messagePanel.setBackground(new Color(240, 240, 240));

        messageField = new JTextField();
        messageField.setFont(MAIN_FONT);
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JButton sendButton = new JButton("Send");
        sendButton.setFont(MAIN_FONT);
        sendButton.setBackground(PRIMARY_COLOR);
        sendButton.setForeground(Color.WHITE);
        sendButton.setOpaque(true);
        sendButton.setBorderPainted(false);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        // Side panel with tabs for rooms and users
        sidePanel = new JTabbedPane();
        sidePanel.setFont(MAIN_FONT);
        sidePanel.setPreferredSize(new Dimension(200, 0));

        // Rooms tab
        JPanel roomsPanel = new JPanel(new BorderLayout(0, 10));
        roomsPanel.setBackground(new Color(240, 240, 240));

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setFont(MAIN_FONT);
        roomList.setBackground(Color.WHITE);
        roomList.setBorder(new EmptyBorder(5, 5, 5, 5));
        roomList.setCellRenderer(new RoomCellRenderer());

        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        JPanel roomButtonsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton createRoomButton = new JButton("Create");
        JButton joinRoomButton = new JButton("Join");
        styleButton(createRoomButton);
        styleButton(joinRoomButton);
        roomButtonsPanel.add(createRoomButton);
        roomButtonsPanel.add(joinRoomButton);

        roomsPanel.add(roomScrollPane, BorderLayout.CENTER);
        roomsPanel.add(roomButtonsPanel, BorderLayout.SOUTH);

        // Users tab
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBackground(new Color(240, 240, 240));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(MAIN_FONT);
        userList.setBackground(Color.WHITE);
        userList.setBorder(new EmptyBorder(5, 5, 5, 5));
        userList.setCellRenderer(new UserCellRenderer());

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        usersPanel.add(userScrollPane, BorderLayout.CENTER);

        // Add tabs to side panel
        sidePanel.addTab("Rooms", roomsPanel);
        sidePanel.addTab("Users", usersPanel);

        // Add components to chat panel
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(messagePanel, BorderLayout.SOUTH);

        // Add all panels to main container
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(chatPanel, BorderLayout.CENTER);
        mainPanel.add(sidePanel, BorderLayout.EAST);

        // Create room button action
        createRoomButton.addActionListener(e -> showCreateRoomDialog());

        // Join room button action
        joinRoomButton.addActionListener(e -> {
            String selected = roomList.getSelectedValue();
            if (selected != null) {
                // Parse room name and lock status
                String roomName = selected.split(" ")[0];
                boolean isLocked = selected.contains("🔒");

                if (isLocked) {
                    showJoinRoomWithCodeDialog(roomName);
                } else {
                    out.println("/join " + roomName);
                }
            }
        });

        // Send message actions
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // Add window listener to handle closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (out != null) {
                    out.println("/exit");
                }
                System.exit(0);
            }
        });

        add(mainPanel);
        setVisible(true);
        messageField.requestFocus();
    }

    private void showCreateRoomDialog() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextField roomNameField = new JTextField();
        JPasswordField accessCodeField = new JPasswordField();
        JCheckBox publicRoomCheckbox = new JCheckBox("Public Room (no access code required)");

        panel.add(new JLabel("Room Name:"));
        panel.add(roomNameField);
        panel.add(new JLabel("Access Code (optional):"));
        panel.add(accessCodeField);
        panel.add(publicRoomCheckbox);

        // Disable access code field when public room is checked
        publicRoomCheckbox.addActionListener(e -> {
            accessCodeField.setEnabled(!publicRoomCheckbox.isSelected());
        });
        publicRoomCheckbox.setSelected(true);
        accessCodeField.setEnabled(false);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Create New Room", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String roomName = roomNameField.getText().trim();

            if (roomName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Room name is required.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Check for spaces in room name
            if (roomName.contains(" ")) {
                JOptionPane.showMessageDialog(this, "Room name cannot contain spaces.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String accessCode = publicRoomCheckbox.isSelected() ? "public" : new String(accessCodeField.getPassword());

            // Send create room command
            out.println("/create " + roomName + " " + accessCode);
        }
    }

    private void showJoinRoomWithCodeDialog(String roomName) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPasswordField accessCodeField = new JPasswordField();

        panel.add(new JLabel("Enter access code for room '" + roomName + "':"));
        panel.add(accessCodeField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Join Private Room", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String accessCode = new String(accessCodeField.getPassword());
            out.println("/join " + roomName + " " + accessCode);
        }
    }

    private void startMessageListener() {
        new Thread(() -> {
            try {
                String message;
                boolean collectingUsers = false;
                boolean collectingRooms = false;
                boolean collectingHistory = false;

                while ((message = in.readLine()) != null) {
                    // Handle user list updates
                    if (message.equals("USER_LIST_BEGIN")) {
                        collectingUsers = true;
                        SwingUtilities.invokeLater(() -> userListModel.clear());
                        continue;
                    } else if (message.equals("USER_LIST_END")) {
                        collectingUsers = false;
                        continue;
                    }

                    // Handle room list updates
                    if (message.equals("ROOM_LIST_BEGIN")) {
                        collectingRooms = true;
                        SwingUtilities.invokeLater(() -> roomListModel.clear());
                        continue;
                    } else if (message.equals("ROOM_LIST_END")) {
                        collectingRooms = false;
                        continue;
                    }

                    // Handle chat history
                    if (message.equals("CHAT_HISTORY_BEGIN")) {
                        collectingHistory = true;
                        SwingUtilities.invokeLater(() -> chatArea.setText(""));
                        continue;
                    } else if (message.equals("CHAT_HISTORY_END")) {
                        collectingHistory = false;
                        continue;
                    }

                    final String finalMessage = message;

                    // Process based on collection state
                    if (collectingUsers) {
                        SwingUtilities.invokeLater(() -> userListModel.addElement(finalMessage));
                    } else if (collectingRooms) {
                        if (!finalMessage.startsWith("Available rooms:")) {
                            // Store the full message (with emoji) for renderer
                            SwingUtilities.invokeLater(() -> roomListModel.addElement(finalMessage));
                        }
                    } else if (collectingHistory) {
                        SwingUtilities.invokeLater(() -> displayMessage(finalMessage, false));
                    } else {
                        // Regular message
                        SwingUtilities.invokeLater(() -> {
                            // Update room label if room changed
                            if (finalMessage.startsWith("You have joined room ")) {
                                currentRoom = finalMessage.replace("You have joined room '", "")
                                        .replace("'.", "");
                                ((JLabel) ((JPanel) ((BorderLayout) headerPanel.getLayout())
                                        .getLayoutComponent(BorderLayout.EAST)).getComponent(0))
                                        .setText("Room: " + currentRoom);
                            }

                            // Display the message
                            displayMessage(finalMessage, true);

                            // Request updated lists if needed
                            if (finalMessage.contains("has joined the chat") ||
                                    finalMessage.contains("has left the chat") ||
                                    finalMessage.contains("has joined the room") ||
                                    finalMessage.contains("has left the room")) {
                                out.println("/users");
                            }
                        });
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    displayMessage("Server connection lost. Please restart the application.", true);
                    JOptionPane.showMessageDialog(ClientUI.this,
                            "Lost connection to the server. The application will now close.",
                            "Connection Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                });
            }
        }).start();

        // Request room list and user list on startup
        out.println("/rooms");
        out.println("/users");
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
        messageField.requestFocus();
    }

    private void displayMessage(String message, boolean addTimestamp) {
        if (addTimestamp) {
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            chatArea.append("[" + timestamp + "] " + message + "\n");
        } else {
            chatArea.append(message + "\n");
        }

        // Auto-scroll to bottom
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // Custom cell renderer for the user list
    private class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String text = value.toString();
            if (text.contains("(online)")) {
                label.setIcon(new ColorIcon(10, 10, Color.GREEN));
            } else {
                label.setIcon(new ColorIcon(10, 10, Color.LIGHT_GRAY));
            }

            label.setBorder(new EmptyBorder(5, 5, 5, 0));
            return label;
        }
    }

    // Custom cell renderer for the room list
    private class RoomCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // Room name formatting
            String text = value.toString();
            String displayText = text.split(" ")[0]; // Get room name without emoji

            // Set icon based on lock status
            if (text.contains("🔓")) {
                label.setIcon(new ColorIcon(10, 10, new Color(100, 180, 100)));
            } else if (text.contains("🔒")) {
                label.setIcon(new ColorIcon(10, 10, new Color(180, 100, 100)));
            }

            label.setText(displayText); // Display only the room name
            label.setBorder(new EmptyBorder(5, 5, 5, 0));
            return label;
        }
    }

    // Simple icon for status indicators
    private static class ColorIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;

        public ColorIcon(int width, int height, Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.fillOval(x, y + (height / 4), width / 2, height / 2);
            g2d.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(ClientUI::new);
    }
}