package src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Client extends JFrame {
    private static Font INTER_REGULAR_FONT;
    static {
        try (InputStream is = Client.class.getResourceAsStream("/fonts/Inter-Regular.ttf")) {
            if (is != null) {
                INTER_REGULAR_FONT = Font.createFont(Font.TRUETYPE_FONT, is);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(INTER_REGULAR_FONT);
            } else {
                System.err.println("Font file not found: /fonts/Inter-Regular.ttf. Falling back to SansSerif.");
                INTER_REGULAR_FONT = new Font("SansSerif", Font.PLAIN, 14);
            }
        } catch (FontFormatException | IOException e) {
            System.err.println("Error loading font: " + e.getMessage() + ". Falling back to SansSerif.");
            INTER_REGULAR_FONT = new Font("SansSerif", Font.PLAIN, 14);
        }
    }
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
    private JLabel dynamicRoomLabel;
    private final Color PRIMARY_COLOR = new Color(37, 99, 235); // Modern blue
    private final Color HOVER_COLOR = new Color(29, 78, 216); // Darker blue for hover
    private final Color BACKGROUND_COLOR = new Color(249, 250, 251); // Light gray
    private final Color CARD_COLOR = Color.WHITE;
    private final Color BORDER_COLOR = new Color(229, 231, 235);
    private final Color TEXT_COLOR = new Color(55, 65, 81);
    private final Font MAIN_FONT = INTER_REGULAR_FONT.deriveFont(Font.PLAIN, 14f);
    private final Font TITLE_FONT = INTER_REGULAR_FONT.deriveFont(Font.BOLD, 24f);

    private static final int HARDCODED_PORT = 5000;

    public Client() {
        // Load the icon
        URL iconURL = getClass().getResource("images/chat.png");
        if (iconURL != null) {
            ImageIcon icon = new ImageIcon(iconURL);
            this.setIconImage(icon.getImage());
        } else {
            System.err.println("Icon not found.");
        }
        showLoginDialog();
    }

    private class RoundedButton extends JButton {
        private Color backgroundColor = PRIMARY_COLOR;
        private Color hoverColor = HOVER_COLOR;
        private boolean isHovered = false;

        public RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setFont(MAIN_FONT);
            setForeground(Color.WHITE);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Paint background with rounded corners
            g2.setColor(isHovered ? hoverColor : backgroundColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private void showLoginDialog() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        // Logo/Header with better spacing
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headerPanel.setBackground(BACKGROUND_COLOR);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));

        JLabel logoLabel = new JLabel("OpenChat");
        logoLabel.setFont(TITLE_FONT);
        logoLabel.setForeground(PRIMARY_COLOR);
        headerPanel.add(logoLabel);

        // Enhanced tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(MAIN_FONT);
        tabbedPane.setBackground(CARD_COLOR);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        // Enhanced Login panel with better layout
        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBackground(CARD_COLOR);
        loginPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 8, 0);

        JTextField loginUsernameField = new JTextField(20);
        JPasswordField loginPasswordField = new JPasswordField(20);
        RoundedButton loginButton = new RoundedButton("Login");

        styleTextField(loginUsernameField);
        styleTextField(loginPasswordField);
        loginPasswordField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Add components with better spacing
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setFont(MAIN_FONT);
        usernameLabel.setForeground(TEXT_COLOR);
        loginPanel.add(usernameLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 0, 16, 0);
        loginPanel.add(loginUsernameField, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(8, 0, 8, 0);
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(MAIN_FONT);
        passwordLabel.setForeground(TEXT_COLOR);
        loginPanel.add(passwordLabel, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(4, 0, 24, 0);
        loginPanel.add(loginPasswordField, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(8, 0, 0, 0);
        loginPanel.add(loginButton, gbc);

        // Enhanced Register panel
        JPanel registerPanel = new JPanel(new GridBagLayout());
        registerPanel.setBackground(CARD_COLOR);
        registerPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JTextField regUsernameField = new JTextField(20);
        JPasswordField regPasswordField = new JPasswordField(20);
        JPasswordField regConfirmPasswordField = new JPasswordField(20);
        RoundedButton registerButton = new RoundedButton("Create Account");

        styleTextField(regUsernameField);
        styleTextField(regPasswordField);
        styleTextField(regConfirmPasswordField);

        regPasswordField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        regConfirmPasswordField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.insets = new Insets(8, 0, 8, 0);
        gbc2.weightx = 1.0;

        // Add register components
        gbc2.gridx = 0;
        gbc2.gridy = 0;
        JLabel regUsernameLabel = new JLabel("Username");
        regUsernameLabel.setFont(MAIN_FONT);
        regUsernameLabel.setForeground(TEXT_COLOR);
        registerPanel.add(regUsernameLabel, gbc2);

        gbc2.gridy = 1;
        gbc2.insets = new Insets(4, 0, 16, 0);
        registerPanel.add(regUsernameField, gbc2);

        gbc2.gridy = 2;
        gbc2.insets = new Insets(8, 0, 8, 0);
        JLabel regPasswordLabel = new JLabel("Password");
        regPasswordLabel.setFont(MAIN_FONT);
        regPasswordLabel.setForeground(TEXT_COLOR);
        registerPanel.add(regPasswordLabel, gbc2);

        gbc2.gridy = 3;
        gbc2.insets = new Insets(4, 0, 16, 0);
        registerPanel.add(regPasswordField, gbc2);

        gbc2.gridy = 4;
        gbc2.insets = new Insets(8, 0, 8, 0);
        JLabel regConfirmLabel = new JLabel("Confirm Password");
        regConfirmLabel.setFont(MAIN_FONT);
        regConfirmLabel.setForeground(TEXT_COLOR);
        registerPanel.add(regConfirmLabel, gbc2);

        gbc2.gridy = 5;
        gbc2.insets = new Insets(4, 0, 24, 0);
        registerPanel.add(regConfirmPasswordField, gbc2);

        gbc2.gridy = 6;
        gbc2.insets = new Insets(8, 0, 0, 0);
        registerPanel.add(registerButton, gbc2);
        // Enhanced Anonymous panel
        JPanel anonymousPanel = new JPanel(new GridBagLayout());
        anonymousPanel.setBackground(CARD_COLOR);
        anonymousPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gbc3 = new GridBagConstraints();
        gbc3.fill = GridBagConstraints.HORIZONTAL;
        gbc3.insets = new Insets(0, 0, 24, 0);

        JLabel anonInfoLabel = new JLabel(
                "<html><div style='text-align: center; color: #6B7280;'>" +
                        "Join anonymously without creating an account.<br><br>" +
                        "Your temporary username will be assigned by the server." +
                        "</div></html>");
        anonInfoLabel.setFont(MAIN_FONT);
        anonInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        RoundedButton anonymousButton = new RoundedButton("Join Anonymously");

        gbc3.gridx = 0;
        gbc3.gridy = 0;
        anonymousPanel.add(anonInfoLabel, gbc3);

        gbc3.gridy = 1;
        gbc3.insets = new Insets(0, 0, 0, 0);
        anonymousPanel.add(anonymousButton, gbc3);

        // Add panels to tabbed pane
        tabbedPane.addTab("Login", loginPanel);
        tabbedPane.addTab("Register", registerPanel);
        tabbedPane.addTab("Anonymous", anonymousPanel);

        // Add components to main panel
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Create enhanced dialog
        JDialog dialog = new JDialog((Frame) null, "OpenChat", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // Auto-focus the first input field
                SwingUtilities.invokeLater(() -> loginUsernameField.requestFocus());
            }
        });

        dialog.getContentPane().add(mainPanel);
        dialog.setSize(500, 640);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.getContentPane().setBackground(BACKGROUND_COLOR);

        // Enhanced action listeners with the same logic but better UX
        loginButton.addActionListener(_ -> {
            String username = loginUsernameField.getText().trim();
            String password = new String(loginPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                showErrorDialog(dialog, "Username and password are required", "Login Error");
                return;
            }

            dialog.dispose();
            getServerInfo(username, password, "LOGIN");
        });

        registerButton.addActionListener(_ -> {
            String username = regUsernameField.getText().trim();
            String password = new String(regPasswordField.getPassword());
            String confirmPassword = new String(regConfirmPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                showErrorDialog(dialog, "All fields are required", "Registration Error");
                return;
            }

            if (!password.equals(confirmPassword)) {
                showErrorDialog(dialog, "Passwords do not match", "Registration Error");
                return;
            }

            dialog.dispose();
            getServerInfo(username, password, "REGISTER");
        });

        anonymousButton.addActionListener(_ -> {
            dialog.dispose();
            isAnonymous = true;
            getServerInfo("", "", "ANONYMOUS");
        });

        // Auto-focus management for tabs
        tabbedPane.addChangeListener(_ -> {
            SwingUtilities.invokeLater(() -> {
                int selectedIndex = tabbedPane.getSelectedIndex();
                switch (selectedIndex) {
                    case 0: // Login tab
                        loginUsernameField.requestFocus();
                        break;
                    case 1: // Register tab
                        regUsernameField.requestFocus();
                        break;
                    case 2: // Anonymous tab
                        anonymousButton.requestFocus();
                        break;
                }
            });
        });

        dialog.setVisible(true);
    }

    // Enhanced error dialog
    private void showErrorDialog(Component parent, String message, String title) {
        JOptionPane optionPane = new JOptionPane(message, JOptionPane.ERROR_MESSAGE);
        optionPane.setFont(MAIN_FONT);

        JDialog dialog = optionPane.createDialog(parent, title);
        dialog.setVisible(true);
    }

    private void styleTextField(JTextField textField) {
        textField.setFont(MAIN_FONT);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        textField.setBackground(Color.WHITE);
        textField.setForeground(TEXT_COLOR);

        // Remove focus border and add subtle focus effect
        textField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                textField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(PRIMARY_COLOR, 2),
                        BorderFactory.createEmptyBorder(11, 15, 11, 15)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                textField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1),
                        BorderFactory.createEmptyBorder(12, 16, 12, 16)));
            }
        });
    }

    private void getServerInfo(String username, String password, String loginType) {
        // Use the hardcoded port directly
        int port = HARDCODED_PORT; //
        String serverAddress = "localhost"; // Default to localhost

        // Prompt for server IP address only, as port is hardcoded
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 8, 0);

        JTextField serverField = new JTextField("localhost", 20);
        styleTextField(serverField);

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel serverLabel = new JLabel("Server IP Address");
        serverLabel.setFont(MAIN_FONT);
        serverLabel.setForeground(TEXT_COLOR);
        panel.add(serverLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 0, 0, 0);
        panel.add(serverField, gbc);

        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(null, "Connect to Server");

        // Auto-focus server field
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    serverField.requestFocus();
                    serverField.selectAll();
                });
            }
        });

        dialog.setVisible(true);

        Object result = optionPane.getValue();
        if (result != null && result.equals(JOptionPane.OK_OPTION)) {
            serverAddress = serverField.getText().trim();
            this.username = username;

            if (serverAddress.isEmpty()) {
                showErrorDialog(null, "Server address is required!", "Input Error");
                getServerInfo(username, password, loginType);
                return;
            }

            try {
                // Validate if the serverAddress is a valid host or IP
                InetAddress.getByName(serverAddress); // This will throw UnknownHostException if invalid
            } catch (UnknownHostException e) {
                showErrorDialog(null,
                        "Invalid server IP address or hostname. Please enter a valid address (e.g., localhost or 127.0.0.1).",
                        "Input Error");
                getServerInfo(username, password, loginType);
                return;
            }
        } else {
            System.exit(0);
        }

        this.username = username;
        connectToServer(serverAddress, port, username, password, loginType);
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
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main container with modern styling
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(BACKGROUND_COLOR);

        // Enhanced header panel
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(20, 30, 20, 30)));

        JLabel titleLabel = new JLabel("OpenChat");
        titleLabel.setFont(INTER_REGULAR_FONT.deriveFont(Font.BOLD, 20f));
        titleLabel.setForeground(PRIMARY_COLOR);

        JLabel staticUserPrefixLabel = new JLabel("Logged in as: ");
        staticUserPrefixLabel.setFont(INTER_REGULAR_FONT.deriveFont(Font.PLAIN, 14f));
        staticUserPrefixLabel.setForeground(new Color(107, 114, 128));

        JLabel dynamicUsernameLabel = new JLabel(username + userStatus);
        dynamicUsernameLabel.setFont(INTER_REGULAR_FONT.deriveFont(Font.BOLD, 14f));
        dynamicUsernameLabel.setForeground(PRIMARY_COLOR);

        JPanel usernameDisplayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        usernameDisplayPanel.setOpaque(false);
        usernameDisplayPanel.add(staticUserPrefixLabel);
        usernameDisplayPanel.add(dynamicUsernameLabel);

        JLabel staticRoomPrefixLabel = new JLabel("Room: ");
        staticRoomPrefixLabel.setFont(INTER_REGULAR_FONT.deriveFont(Font.PLAIN, 14f));
        staticRoomPrefixLabel.setForeground(new Color(107, 114, 128));

        dynamicRoomLabel = new JLabel(currentRoom);
        dynamicRoomLabel.setFont(INTER_REGULAR_FONT.deriveFont(Font.BOLD, 14f));
        dynamicRoomLabel.setForeground(TEXT_COLOR);

        JPanel roomDisplayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        roomDisplayPanel.setOpaque(false);
        roomDisplayPanel.add(staticRoomPrefixLabel);
        roomDisplayPanel.add(dynamicRoomLabel);

        JPanel userInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        userInfoPanel.setOpaque(false);
        userInfoPanel.add(roomDisplayPanel);
        userInfoPanel.add(usernameDisplayPanel);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(userInfoPanel, BorderLayout.EAST);

        // Enhanced chat panel
        JPanel chatContainer = new JPanel(new BorderLayout(20, 20));
        chatContainer.setBackground(BACKGROUND_COLOR);
        chatContainer.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 20));

        JPanel chatPanel = new JPanel(new BorderLayout(0, 16));
        chatPanel.setBackground(Color.WHITE);
        chatPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        // Enhanced chat area
        chatArea = new JTextArea();
        chatArea.setFont(MAIN_FONT);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        chatArea.setForeground(TEXT_COLOR);

        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(null);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Enhanced message input panel
        JPanel messagePanel = new JPanel(new BorderLayout(12, 0));
        messagePanel.setBackground(Color.WHITE);
        messagePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(16, 20, 20, 20)));

        messageField = new JTextField();
        messageField.setFont(MAIN_FONT);
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        messageField.setBackground(BACKGROUND_COLOR);
        messageField.setForeground(TEXT_COLOR);

        // Enhanced send button
        RoundedButton sendButton = new RoundedButton("Send");
        sendButton.setPreferredSize(new Dimension(90, 46));

        // Focus styling for message field
        messageField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                messageField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(PRIMARY_COLOR, 2),
                        BorderFactory.createEmptyBorder(11, 15, 11, 15)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                messageField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1),
                        BorderFactory.createEmptyBorder(12, 16, 12, 16)));
            }
        });

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        // Enhanced side panel
        sidePanel = new JTabbedPane();
        sidePanel.setFont(MAIN_FONT);
        sidePanel.setPreferredSize(new Dimension(300, 0));
        sidePanel.setBackground(Color.WHITE);
        sidePanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 30));

        // Enhanced rooms panel
        JPanel roomsContainer = new JPanel(new BorderLayout(0, 16));
        roomsContainer.setBackground(Color.WHITE);
        roomsContainer.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setFont(MAIN_FONT);
        roomList.setBackground(BACKGROUND_COLOR);
        roomList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        roomList.setCellRenderer(new EnhancedRoomCellRenderer());
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        roomScrollPane.setBackground(BACKGROUND_COLOR);

        JPanel roomButtonsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        roomButtonsPanel.setBackground(Color.WHITE);

        RoundedButton createRoomButton = new RoundedButton("Create");
        RoundedButton joinRoomButton = new RoundedButton("Join");

        createRoomButton.setPreferredSize(new Dimension(0, 40));
        joinRoomButton.setPreferredSize(new Dimension(0, 40));

        roomButtonsPanel.add(createRoomButton);
        roomButtonsPanel.add(joinRoomButton);

        roomsContainer.add(roomScrollPane, BorderLayout.CENTER);
        roomsContainer.add(roomButtonsPanel, BorderLayout.SOUTH);

        // Enhanced users panel
        JPanel usersContainer = new JPanel(new BorderLayout());
        usersContainer.setBackground(Color.WHITE);
        usersContainer.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(MAIN_FONT);
        userList.setBackground(BACKGROUND_COLOR);
        userList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        userList.setCellRenderer(new EnhancedUserCellRenderer());

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        userScrollPane.setBackground(BACKGROUND_COLOR);
        userScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        usersContainer.add(userScrollPane, BorderLayout.CENTER);

        // Add enhanced tabs
        sidePanel.addTab("Rooms", roomsContainer);
        sidePanel.addTab("Users", usersContainer);

        // Assemble chat panel
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(messagePanel, BorderLayout.SOUTH);

        chatContainer.add(chatPanel, BorderLayout.CENTER);

        // Add all panels to main container
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(chatContainer, BorderLayout.CENTER);
        mainPanel.add(sidePanel, BorderLayout.EAST);

        // Enhanced button actions (same logic, better UX)
        createRoomButton.addActionListener(_ -> showEnhancedCreateRoomDialog());
        joinRoomButton.addActionListener(_ -> {
            String selected = roomList.getSelectedValue();
            if (selected != null) {
                String roomName = selected.split(" ")[0];
                boolean isLocked = selected.contains("🔒");

                if (isLocked) {
                    showEnhancedJoinRoomWithCodeDialog(roomName);
                } else {
                    out.println("/join " + roomName);
                }
            }
        });

        sendButton.addActionListener(_ -> sendMessage());
        messageField.addActionListener(_ -> sendMessage());

        add(mainPanel);
        setVisible(true);
        messageField.requestFocus();
    }

    private void showEnhancedCreateRoomDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 8, 0);

        JTextField roomNameField = new JTextField(20);
        JPasswordField accessCodeField = new JPasswordField(20);
        JCheckBox publicRoomCheckbox = new JCheckBox("Public Room (no access code required)");

        styleTextField(roomNameField);
        styleTextField(accessCodeField);
        accessCodeField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        publicRoomCheckbox.setFont(MAIN_FONT);
        publicRoomCheckbox.setForeground(TEXT_COLOR);
        publicRoomCheckbox.setBackground(CARD_COLOR);

        // Room name label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel roomNameLabel = new JLabel("Room Name");
        roomNameLabel.setFont(MAIN_FONT);
        roomNameLabel.setForeground(TEXT_COLOR);
        panel.add(roomNameLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 0, 16, 0);
        panel.add(roomNameField, gbc);

        // Access code label and field
        gbc.gridy = 2;
        gbc.insets = new Insets(8, 0, 8, 0);
        JLabel accessCodeLabel = new JLabel("Access Code (optional)");
        accessCodeLabel.setFont(MAIN_FONT);
        accessCodeLabel.setForeground(TEXT_COLOR);
        panel.add(accessCodeLabel, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(4, 0, 16, 0);
        panel.add(accessCodeField, gbc);

        // Public room checkbox
        gbc.gridy = 4;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(publicRoomCheckbox, gbc);

        // Disable access code field when public room is checked
        publicRoomCheckbox.addActionListener(_ -> {
            accessCodeField.setEnabled(!publicRoomCheckbox.isSelected());
            if (publicRoomCheckbox.isSelected()) {
                accessCodeField.setText("");
            }
        });
        publicRoomCheckbox.setSelected(true);
        accessCodeField.setEnabled(false);

        // Create custom dialog
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(this, "Create New Room");

        // Auto-focus room name field
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> roomNameField.requestFocus());
            }
        });

        dialog.setVisible(true);

        Object result = optionPane.getValue();
        if (result != null && result.equals(JOptionPane.OK_OPTION)) {
            String roomName = roomNameField.getText().trim();

            if (roomName.isEmpty()) {
                showErrorDialog(this, "Room name is required.", "Input Error");
                return;
            }

            // Check for spaces in room name
            if (roomName.contains(" ")) {
                showErrorDialog(this, "Room name cannot contain spaces.", "Input Error");
                return;
            }

            String accessCode = publicRoomCheckbox.isSelected() ? "public" : new String(accessCodeField.getPassword());

            // Send create room command
            out.println("/create " + roomName + " " + accessCode);
        }
    }

    private void showEnhancedJoinRoomWithCodeDialog(String roomName) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 8, 0);

        JPasswordField accessCodeField = new JPasswordField(20);
        styleTextField(accessCodeField);
        accessCodeField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Info label
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel infoLabel = new JLabel("Enter access code for room '" + roomName + "':");
        infoLabel.setFont(MAIN_FONT);
        infoLabel.setForeground(TEXT_COLOR);
        panel.add(infoLabel, gbc);

        // Access code field
        gbc.gridy = 1;
        gbc.insets = new Insets(16, 0, 0, 0);
        panel.add(accessCodeField, gbc);

        // Create custom dialog
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(this, "Join Private Room");

        // Auto-focus access code field
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> accessCodeField.requestFocus());
            }
        });

        dialog.setVisible(true);

        Object result = optionPane.getValue();
        if (result != null && result.equals(JOptionPane.OK_OPTION)) {
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
                        // Clear chat area when history begins
                        SwingUtilities.invokeLater(() -> chatArea.setText(""));
                        continue;
                    } else if (message.equals("CHAT_HISTORY_END")) {
                        collectingHistory = false;
                        SwingUtilities.invokeLater(() -> {
                            displayMessage("You have joined room '" + currentRoom + "'.", true);
                        });
                        continue;
                    }

                    final String finalMessage = message;

                    // Process based on collection state
                    if (collectingUsers) {
                        SwingUtilities.invokeLater(() -> userListModel.addElement(finalMessage));
                    } else if (collectingRooms) {
                        if (!finalMessage.startsWith("Available rooms:")) {
                            SwingUtilities.invokeLater(() -> roomListModel.addElement(finalMessage));
                        }
                    } else if (collectingHistory) { // Messages that are part of history
                        SwingUtilities.invokeLater(() -> displayMessage(finalMessage, false));
                    } else {
                        // Regular message handling
                        SwingUtilities.invokeLater(() -> {
                            if (finalMessage.startsWith("You have joined room ")) {
                                currentRoom = finalMessage.replace("You have joined room '", "")
                                        .replace("'.", "");
                                dynamicRoomLabel.setText(currentRoom); // Update header room name
                            } else {
                                // For all other regular messages (not join confirmations), display them as
                                // usual
                                displayMessage(finalMessage, true);
                            }

                            // Request updated lists if needed (for user/room changes like joins/leaves)
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
                    JOptionPane.showMessageDialog(Client.this,
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
            if (message.equals("/exit")) {
                out.println(message);
                System.exit(0);
            } else {
                out.println(message);
            }
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
    private class EnhancedUserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String text = value.toString();
            if (text.contains("(online)")) {
                label.setIcon(new ModernStatusIcon(Color.decode("#10B981"))); // Green
            } else {
                label.setIcon(new ModernStatusIcon(Color.decode("#D1D5DB"))); // Gray
            }

            label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            label.setFont(MAIN_FONT);

            if (isSelected) {
                label.setBackground(new Color(239, 246, 255));
                label.setForeground(PRIMARY_COLOR);
            } else {
                label.setBackground(Color.WHITE);
                label.setForeground(TEXT_COLOR);
            }

            return label;
        }
    }

    // Custom cell renderer for the room list
    private class EnhancedRoomCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String text = value.toString();
            String displayText = text.split(" ")[0];

            if (text.contains("🔓")) {
                label.setIcon(new ModernStatusIcon(Color.decode("#10B981"))); // Green for unlocked
            } else if (text.contains("🔒")) {
                label.setIcon(new ModernStatusIcon(Color.decode("#EF4444"))); // Red for locked
            }

            label.setText(displayText);
            label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            label.setFont(MAIN_FONT);

            if (isSelected) {
                label.setBackground(new Color(239, 246, 255));
                label.setForeground(PRIMARY_COLOR);
            } else {
                label.setBackground(Color.WHITE);
                label.setForeground(TEXT_COLOR);
            }

            return label;
        }
    }

    private static class ModernStatusIcon implements Icon {
        private final Color color;
        private final int size = 8;

        public ModernStatusIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.fillOval(x + 2, y + (getIconHeight() - size) / 2, size, size);
            g2d.dispose();
        }

        @Override
        public int getIconWidth() {
            return size + 4;
        }

        @Override
        public int getIconHeight() {
            return size + 4;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(Client::new);
    }
}