package com.chatapp.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
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

                    // TODO: check username/password from DB or dummy check:
                    if (isValidUser(username, password)) {
                        clients.put(username, this);
                        out.println("SUCCESS");
                        System.out.println(username + " logged in.");

                        // Listen for messages
                        String clientMsg;
                        while ((clientMsg = in.readLine()) != null) {
                            if (clientMsg.startsWith("MSG:")) {
                                // Format: MSG:recipient:message_text
                                String[] msgParts = clientMsg.split(":", 3);
                                if (msgParts.length == 3) {
                                    String recipient = msgParts[1];
                                    String msgText = msgParts[2];
                                    ClientHandler recipientHandler = clients.get(recipient);
                                    if (recipientHandler != null) {
                                        recipientHandler.sendMessage("[" + getCurrentTime() + "] " + username + ": " + msgText);
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

        private boolean isValidUser(String username, String password) {
            // Implement your user validation logic here or dummy allow all for demo
            return true;
        }
    }

    private String getCurrentTime() {
        return java.time.LocalTime.now().toString();
    }

    public static void main(String[] args) throws Exception {
        ChatServer server = new ChatServer();
        server.start(8080);
    }
}
