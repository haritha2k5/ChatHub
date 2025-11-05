package com.chatapp.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {

    private static final String URL = "jdbc:h2:./chatdb;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASS = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
            // Create users table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) UNIQUE, " +
                    "password VARCHAR(256))");
            
            // Create messages table with recipient and read status
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "sender_id INT, " +
                    "sender_name VARCHAR(50), " +
                    "recipient VARCHAR(50), " +
                    "content TEXT, " +
                    "timestamp TIMESTAMP, " +
                    "read BOOLEAN DEFAULT FALSE, " +
                    "FOREIGN KEY (sender_id) REFERENCES users(id))");
            
            System.out.println("Database initialized successfully!");
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        initializeDatabase();
        Connection conn = null;
        try {
            conn = getConnection();
            var clearStmt = conn.createStatement();
            clearStmt.execute("DELETE FROM users");
            System.out.println("Old users cleared!");
            
            var stmt = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)");
            String[][] users = {
                {"haritha", "pass"},
                {"aakash", "pass"},
                {"kaniska", "pass"},
                {"kabilan", "pass"},
                {"srivinay", "pass"}
            };
            
            for (String[] user : users) {
                stmt.setString(1, user[0]);
                stmt.setString(2, user[1]);
                stmt.executeUpdate();
                System.out.println(user[0] + " inserted!");
            }
            
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("Database connection closed.");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}