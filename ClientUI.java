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
    private String username;
    private Socket client;
    private PrintWriter out;
    private BufferedReader in;
    private final Color PRIMARY_COLOR = new Color(70, 130, 180); // Steel blue
    private final Color ACCENT_COLOR = new Color(95, 158, 160); // Cadet blue
    private final Font MAIN_FONT = new Font("Inter", Font.PLAIN, 14);
    private final Font HEADER_FONT = new Font("Inter", Font.BOLD, 16);

    public ClientUI() {
        setupUI();
        connectToServer();
    }

    private void setupUI() {
        setTitle("OpenChat");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Get username
        username = JOptionPane.showInputDialog(this, "Enter your username:", "Login", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            username = "User" + (int) (Math.random() * 1000);
        }

        // Main container with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(240, 240, 240));

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel("OpenChat");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        JLabel usernameLabel = new JLabel("Logged in as: " + username);
        usernameLabel.setFont(MAIN_FONT);
        usernameLabel.setForeground(new Color(230, 230, 230));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(usernameLabel, BorderLayout.EAST);

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
        sendButton.setBackground(ACCENT_COLOR);
        sendButton.setForeground(Color.WHITE);
        sendButton.setOpaque(true);
        sendButton.setBorderPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        // User list panel (right)
        JPanel userPanel = new JPanel(new BorderLayout(0, 10));
        userPanel.setPreferredSize(new Dimension(200, 0));
        userPanel.setBackground(new Color(240, 240, 240));

        JLabel usersHeaderLabel = new JLabel("Online Users");
        usersHeaderLabel.setFont(HEADER_FONT);
        usersHeaderLabel.setForeground(PRIMARY_COLOR);
        usersHeaderLabel.setBorder(new EmptyBorder(0, 5, 5, 0));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(MAIN_FONT);
        userList.setBackground(Color.WHITE);
        userList.setBorder(new EmptyBorder(5, 5, 5, 5));
        userList.setCellRenderer(new UserCellRenderer());

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        userPanel.add(usersHeaderLabel, BorderLayout.NORTH);
        userPanel.add(userScrollPane, BorderLayout.CENTER);

        // Add components to chat panel
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(messagePanel, BorderLayout.SOUTH);

        // Add all panels to main container
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(chatPanel, BorderLayout.CENTER);
        mainPanel.add(userPanel, BorderLayout.EAST);

        // Add action listeners
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // Add window listener to handle closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (out != null) {
                    out.println("exit");
                }
                System.exit(0);
            }
        });

        add(mainPanel);
        setVisible(true);
        messageField.requestFocus();
    }

    private void connectToServer() {
        try {
            client = new Socket("localhost", 5000);
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // Send username to server
            out.println(username);

            // Start listening for messages from server
            new Thread(() -> {
                try {
                    String message;
                    boolean collectingUsers = false;
                    ArrayList<String> usersList = new ArrayList<>();

                    while ((message = in.readLine()) != null) {
                        // Special protocol for user list
                        if (message.equals("USER_LIST_BEGIN")) {
                            collectingUsers = true;
                            usersList.clear();
                            continue;
                        } else if (message.equals("USER_LIST_END")) {
                            collectingUsers = false;

                            // Update UI with the complete user list
                            SwingUtilities.invokeLater(() -> {
                                userListModel.clear();
                                for (String user : usersList) {
                                    if (!user.equals(username)) {
                                        userListModel.addElement(user);
                                    }
                                }
                                // Add yourself to the top of the list
                                userListModel.add(0, username + " (You)");
                            });
                            continue;
                        }

                        if (collectingUsers) {
                            usersList.add(message);
                            continue;
                        }

                        // Regular message handling
                        final String finalMessage = message;
                        SwingUtilities.invokeLater(() -> {
                            // Skip messages that are our own (already displayed locally)
                            if (!finalMessage.startsWith(username + ": ")) {
                                displayMessage(finalMessage);
                            }

                            // Request updated user list when users join/leave
                            if (finalMessage.contains("has joined the chat")
                                    || finalMessage.contains("has left the chat")) {
                                // Ask server for current user list
                                out.println("GET_USER_LIST");
                            }
                        });
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(
                            () -> displayMessage("Server connection lost. Please restart the application."));
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server!", "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);

            // Display own message with "You:" prefix
            if (!message.equalsIgnoreCase("exit") && !message.equals("GET_USER_LIST")) {
                displayMessage("You: " + message);
            }

            messageField.setText("");
        }
        messageField.requestFocus();
    }

    private void displayMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        chatArea.append("[" + timestamp + "] " + message + "\n");

        // Auto-scroll to bottom
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // Custom cell renderer for the user list
    private class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // Add online indicator
            label.setIcon(new ColorIcon(10, 10, Color.GREEN));
            label.setBorder(new EmptyBorder(5, 5, 5, 0));

            return label;
        }
    }

    // Simple icon for online status
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