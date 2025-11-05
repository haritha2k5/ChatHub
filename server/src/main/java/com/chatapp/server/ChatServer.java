package com.chatapp.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public void start(int port) throws Exception {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(clientSocket);
            handler.start();
        }
    }

    private class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }

        public String getUsername() {
            return username;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read login
                String loginMsg = in.readLine();
                if (loginMsg != null && loginMsg.startsWith("LOGIN:")) {
                    String[] parts = loginMsg.split(":");
                    username = parts[1];
                    String password = parts[2];

                    if (isValidUser(username, password)) {
                        clients.put(username, this);
                        out.println("SUCCESS");
                        System.out.println(username + " logged in.");

                        // Listen for messages
                        String clientMsg;
                        while ((clientMsg = in.readLine()) != null) {
                            
                            // Handle GET_HISTORY command (fetch offline messages)
                            if (clientMsg.startsWith("GET_HISTORY:")) {
                                String[] parts2 = clientMsg.split(":", 2);
                                String recipient = parts2[1];
                                String history = getMessageHistory(recipient);
                                out.println("HISTORY:" + history);
                                System.out.println("Sent message history to " + recipient);
                            }
                            
                            // Handle MARK_READ command
                            else if (clientMsg.startsWith("MARK_READ:")) {
                                String[] parts2 = clientMsg.split(":", 2);
                                String sender = parts2[1];
                                markMessagesAsRead(username, sender);
                                System.out.println("Marked messages from " + sender + " to " + username + " as read.");
                            }
                            
                            // Handle GET_UNREAD_COUNT command
                            else if (clientMsg.startsWith("GET_UNREAD_COUNT:")) {
                                String[] parts2 = clientMsg.split(":", 2);
                                String sender = parts2[1];
                                int unreadCount = getUnreadCount(username, sender);
                                out.println("UNREAD_COUNT:" + sender + ":" + unreadCount);
                            }
                            
                            // Handle GET_ALL_UNREAD command (for chat list)
                            else if (clientMsg.equals("GET_ALL_UNREAD")) {
                                String unreadData = getAllUnreadCounts(username);
                                out.println("ALL_UNREAD:" + unreadData);
                            }
                            
                            // Handle MSG command
                            else if (clientMsg.startsWith("MSG:")) {
                                // Format: MSG:recipient:message_text
                                String[] msgParts = clientMsg.split(":", 3);
                                if (msgParts.length == 3) {
                                    String recipient = msgParts[1];
                                    String msgText = msgParts[2];

                                    // Save to database
                                    saveMessageToDatabase(username, recipient, msgText);

                                    // Send to recipient if online
                                    ClientHandler recipientHandler = clients.get(recipient);
                                    if (recipientHandler != null) {
                                        String fullMsg = "[" + getCurrentTime() + "] " + username + ": " + msgText;
                                        recipientHandler.sendMessage("RECEIVE:" + username + ":" + fullMsg);
                                    }
                                }
                            }
                        }
                    } else {
                        out.println("FAIL");
                        socket.close();
                    }
                }

            } catch (Exception e) {
                System.out.println("Client " + username + " disconnected.");
            } finally {
                if (username != null) {
                    clients.remove(username);
                }
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }

        private String getMessageHistory(String recipient) {
            StringBuilder history = new StringBuilder();
            try (Connection conn = DatabaseConfig.getConnection()) {
                String sql = "SELECT sender_name, content, timestamp FROM messages WHERE recipient = ? ORDER BY timestamp ASC";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, recipient);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String sender = rs.getString("sender_name");
                            String content = rs.getString("content");
                            String timestamp = rs.getString("timestamp");
                            
                            // Format: sender###content###timestamp###read_status|sender###...
                            history.append(sender).append("###")
                                   .append(content).append("###")
                                   .append(formatTimestamp(timestamp)).append("###")
                                   .append("0|");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error getting message history: " + e.getMessage());
            }
            return history.toString();
        }

        private String formatTimestamp(String dbTimestamp) {
            try {
                // DB format: 2025-11-05 15:30:45.123
                // Convert to: 3:30 pm
                if (dbTimestamp != null && dbTimestamp.length() >= 19) {
                    String time = dbTimestamp.substring(11, 19);
                    String[] parts = time.split(":");
                    int hour = Integer.parseInt(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    
                    String ampm = hour >= 12 ? "pm" : "am";
                    if (hour > 12) hour -= 12;
                    if (hour == 0) hour = 12;
                    
                    return String.format("%d:%02d %s", hour, min, ampm);
                }
            } catch (Exception e) {
                System.out.println("Error formatting timestamp: " + e.getMessage());
            }
            return "0:00 am";
        }

        private void saveMessageToDatabase(String sender, String recipient, String content) {
            try (Connection conn = DatabaseConfig.getConnection()) {
                String sql = "INSERT INTO messages (sender_id, sender_name, recipient, content, timestamp, read) " +
                        "VALUES ((SELECT id FROM users WHERE username = ?), ?, ?, ?, CURRENT_TIMESTAMP(), FALSE)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, sender);
                    stmt.setString(2, sender);
                    stmt.setString(3, recipient);
                    stmt.setString(4, content);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                System.out.println("Error saving message: " + e.getMessage());
            }
        }

        private void markMessagesAsRead(String recipient, String sender) {
            try (Connection conn = DatabaseConfig.getConnection()) {
                String sql = "UPDATE messages SET read = TRUE " +
                        "WHERE recipient = ? AND sender_name = ? AND read = FALSE";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, recipient);
                    stmt.setString(2, sender);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                System.out.println("Error marking messages as read: " + e.getMessage());
            }
        }

        private int getUnreadCount(String recipient, String sender) {
            try (Connection conn = DatabaseConfig.getConnection()) {
                String sql = "SELECT COUNT(*) FROM messages WHERE recipient = ? AND sender_name = ? AND read = FALSE";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, recipient);
                    stmt.setString(2, sender);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error getting unread count: " + e.getMessage());
            }
            return 0;
        }

        private String getAllUnreadCounts(String recipient) {
            StringBuilder result = new StringBuilder();
            try (Connection conn = DatabaseConfig.getConnection()) {
                String sql = "SELECT sender_name, COUNT(*) as count FROM messages " +
                        "WHERE recipient = ? AND read = FALSE GROUP BY sender_name";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, recipient);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String sender = rs.getString("sender_name");
                            int count = rs.getInt("count");
                            result.append(sender).append(":").append(count).append(";");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error getting all unread counts: " + e.getMessage());
            }
            return result.toString();
        }

        private boolean isValidUser(String username, String password) {
            try (Connection conn = DatabaseConfig.getConnection()) {
                String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, password);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (Exception e) {
                System.out.println("Error validating user: " + e.getMessage());
            }
            return false;
        }

        private String getCurrentTime() {
            return java.time.LocalTime.now().toString();
        }
    }

    public static void main(String[] args) throws Exception {
        ChatServer server = new ChatServer();
        server.start(8080);
    }
}