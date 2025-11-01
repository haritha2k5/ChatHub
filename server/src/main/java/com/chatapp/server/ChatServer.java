package com.chatapp.server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {

    private static final int PORT = 8080;
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("   ChatHub Server Starting...      ");
        System.out.println("====================================");

        // Initialize database
        System.out.println("[INIT] Initializing database...");
        DatabaseConfig.initializeDatabase();
        System.out.println("[INIT] Database ready!");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] Listening on port " + PORT);
            System.out.println("[SERVER] Waiting for clients to connect...");
            System.out.println("====================================");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();

                System.out.println("[CONNECTION] New client from: " + clientAddress);
                System.out.println("[CONNECTION] Total connections: " + (clients.size() + 1));

                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== CLIENT HANDLER (One per client) ====================

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private int userId;
        private boolean isAuthenticated = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Set up streams
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                System.out.println("[HANDLER] Streams initialized for client");

                // Wait for login message
                String loginMsg = in.readLine();

                if (loginMsg == null) {
                    System.out.println("[AUTH] Client disconnected before authentication");
                    return;
                }

                System.out.println("[AUTH] Received: " + loginMsg);

                // Handle authentication
                if (loginMsg.startsWith("LOGIN:")) {
                    String[] parts = loginMsg.split(":", 3);

                    if (parts.length == 3) {
                        username = parts[1];
                        String password = parts[2];

                        System.out.println("[AUTH] Attempting login for: " + username);

                        userId = authenticate(username, password);

                        if (userId > 0) {
                            isAuthenticated = true;
                            out.println("SUCCESS");

                            System.out.println("[AUTH] ✓ Login successful for: " + username + " (ID: " + userId + ")");

                            // Add client to active clients list
                            clients.add(this);
                            System.out.println("[CLIENTS] Active clients: " + clients.size());

                            // Send chat history to the newly connected client
                            sendChatHistory();

                            // Broadcast join message to all clients
                            String joinMsg = "[" + timeFormat.format(new java.util.Date()) + "] System: " + username + " joined the chat";
                            broadcastMessage(joinMsg, this);
                            System.out.println("[BROADCAST] " + joinMsg);

                            // Listen for messages from this client
                            listenForMessages();

                        } else {
                            out.println("FAIL: Invalid credentials");
                            System.out.println("[AUTH] ✗ Invalid credentials for: " + username);
                            socket.close();
                            return;
                        }

                    } else {
                        out.println("FAIL: Invalid login format");
                        System.out.println("[AUTH] ✗ Invalid login format");
                        socket.close();
                        return;
                    }

                } else {
                    out.println("FAIL: Expected LOGIN message");
                    System.out.println("[AUTH] ✗ No login message received");
                    socket.close();
                    return;
                }

            } catch (IOException e) {
                if (isAuthenticated) {
                    System.out.println("[DISCONNECT] " + username + " disconnected unexpectedly");
                } else {
                    System.out.println("[ERROR] Connection error during authentication: " + e.getMessage());
                }
            } finally {
                cleanup();
            }
        }

        // ==================== AUTHENTICATION ====================

        private int authenticate(String username, String password) {
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id FROM users WHERE username = ? AND password = ?")) {

                stmt.setString(1, username);
                stmt.setString(2, password);

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int id = rs.getInt("id");
                    System.out.println("[DB] User found in database: " + username + " (ID: " + id + ")");
                    return id;
                }

                System.out.println("[DB] User not found or password incorrect: " + username);
                return -1;

            } catch (SQLException e) {
                System.err.println("[DB ERROR] Authentication failed: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
        }

        // ==================== SEND CHAT HISTORY ====================

        private void sendChatHistory() {
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.username, m.content, m.timestamp " +
                     "FROM messages m " +
                     "JOIN users u ON m.sender_id = u.id " +
                     "ORDER BY m.timestamp DESC " +
                     "LIMIT 50")) {

                ResultSet rs = stmt.executeQuery();
                List<String> history = new ArrayList<>();

                while (rs.next()) {
                    String msgUsername = rs.getString("username");
                    String content = rs.getString("content");
                    Timestamp timestamp = rs.getTimestamp("timestamp");

                    // Format: [HH:mm:ss] username: message
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    String formattedMsg = "[" + sdf.format(timestamp) + "] " + msgUsername + ": " + content;

                    history.add(0, formattedMsg); // Add to beginning (reverse order)
                }

                System.out.println("[HISTORY] Sending " + history.size() + " messages to " + username);

                // Send history messages
                for (String msg : history) {
                    out.println(msg);
                }

                if (!history.isEmpty()) {
                    out.println("--- Chat history loaded ---");
                }

            } catch (SQLException e) {
                System.err.println("[DB ERROR] Failed to load chat history: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ==================== LISTEN FOR MESSAGES ====================

        private void listenForMessages() throws IOException {
            System.out.println("[LISTENER] Listening for messages from: " + username);

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("[RECEIVED] From " + username + ": " + message);

                if (message.startsWith("MSG:")) {
                    // Extract message content
                    String content = message.substring(4).trim();

                    if (!content.isEmpty()) {
                        // Save message to database
                        saveMessage(userId, content);

                        // Format message with timestamp and username
                        String timestamp = timeFormat.format(new java.util.Date());
                        String formattedMsg = "[" + timestamp + "] " + username + ": " + content;

                        // Broadcast to all connected clients
                        broadcastMessage(formattedMsg, null);

                        System.out.println("[BROADCAST] Message sent to all clients: " + formattedMsg);
                    }
                } else {
                    System.out.println("[WARNING] Unknown message format from " + username + ": " + message);
                }
            }

            System.out.println("[DISCONNECT] " + username + " closed connection");
        }

        // ==================== SAVE MESSAGE TO DATABASE ====================

        private void saveMessage(int senderId, String content) {
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO messages (sender_id, content, timestamp) VALUES (?, ?, CURRENT_TIMESTAMP())")) {

                stmt.setInt(1, senderId);
                stmt.setString(2, content);

                int rowsInserted = stmt.executeUpdate();

                if (rowsInserted > 0) {
                    System.out.println("[DB] Message saved from user ID " + senderId);
                }

            } catch (SQLException e) {
                System.err.println("[DB ERROR] Failed to save message: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ==================== BROADCAST MESSAGE TO ALL CLIENTS ====================

        private void broadcastMessage(String message, ClientHandler exclude) {
            int successCount = 0;
            int failCount = 0;

            for (ClientHandler client : clients) {
                if (client != exclude && client.out != null && client.isAuthenticated) {
                    try {
                        client.out.println(message);
                        successCount++;
                    } catch (Exception e) {
                        System.err.println("[BROADCAST ERROR] Failed to send to " + client.username);
                        failCount++;
                    }
                }
            }

            if (exclude != null) {
                System.out.println("[BROADCAST] Sent to " + successCount + " clients (excluded sender)");
            } else {
                System.out.println("[BROADCAST] Sent to " + successCount + " clients");
            }

            if (failCount > 0) {
                System.out.println("[BROADCAST] Failed to send to " + failCount + " clients");
            }
        }

        // ==================== CLEANUP ON DISCONNECT ====================

        private void cleanup() {
            clients.remove(this);

            if (isAuthenticated && username != null) {
                String leaveMsg = "[" + timeFormat.format(new java.util.Date()) + "] System: " + username + " left the chat";
                broadcastMessage(leaveMsg, this);

                System.out.println("[DISCONNECT] " + username + " removed from active clients");
                System.out.println("[CLIENTS] Active clients remaining: " + clients.size());
            }

            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                System.out.println("[CLEANUP] Resources closed for " + 
                    (username != null ? username : "unauthenticated client"));
            } catch (IOException e) {
                System.err.println("[CLEANUP ERROR] " + e.getMessage());
            }
        }

        // ==================== UTILITY METHODS ====================

        public void sendMessage(String message) {
            if (out != null && isAuthenticated) {
                out.println(message);
            }
        }

        public String getUsername() {
            return username;
        }

        public boolean isAuthenticated() {
            return isAuthenticated;
        }
    }
}