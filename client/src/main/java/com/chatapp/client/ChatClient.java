package com.chatapp.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatClient extends Application {
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String currentUsername;
    private String currentChatContact;
    private Stage primaryStage;
    private ListView<ChatPreview> chatListView;
    private ListView<MessageItem> messagesListView;
    private ObservableList<ChatPreview> chats = FXCollections.observableArrayList();
    private Map<String, ObservableList<MessageItem>> conversationHistory = new HashMap<>();
    private Thread messageReceiver;
    private boolean isConnected = false;
    
    public static final Map<String, String> PROFILE_COLORS = new HashMap<>();
    static {
        PROFILE_COLORS.put("haritha", "#FF6B6B");
        PROFILE_COLORS.put("aakash", "#4ECDC4");
        PROFILE_COLORS.put("kaniska", "#FFE66D");
        PROFILE_COLORS.put("kabilan", "#95E1D3");
        PROFILE_COLORS.put("srivinay", "#F38181");
    }
    
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("ChatHub");
        primaryStage.setOnCloseRequest(e -> closeConnection());
        showLoginScreen();
        primaryStage.show();
    }

    private void showLoginScreen() {
        primaryStage.setWidth(400);
        primaryStage.setHeight(350);
        primaryStage.setTitle("ChatHub - Login");
        
        Label titleLabel = new Label("ChatHub");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #25D366;");
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");
        passwordField.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));
        
        Button loginButton = new Button("Login");
        loginButton.setPrefWidth(150);
        loginButton.setPrefHeight(40);
        loginButton.setStyle("-fx-font-size: 14px; -fx-background-color: #25D366; -fx-text-fill: white; -fx-font-weight: bold;");
        loginButton.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText()));
        
        Label credentialsLabel = new Label("Try: haritha/pass or aakash/pass");
        credentialsLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
        
        VBox loginBox = new VBox(15);
        loginBox.setStyle("-fx-background-color: #0B141A; -fx-padding: 30;");
        loginBox.getChildren().addAll(titleLabel, usernameField, passwordField, loginButton, credentialsLabel);
        
        Scene loginScene = new Scene(loginBox, 400, 350);
        loginScene.getStylesheets().add(getClass().getResource("/whatsapp-style.css").toExternalForm());
        primaryStage.setScene(loginScene);
    }

    private void handleLogin(String username, String password) {
        currentUsername = username.trim().toLowerCase();
        if (currentUsername.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please enter both username and password");
            return;
        }
        
        try {
            socket = new Socket("localhost", 8080);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            out.println("LOGIN:" + currentUsername + ":" + password);
            String response = in.readLine();
            
            if (response != null && response.equals("SUCCESS")) {
                isConnected = true;
                startMessageReceiver();
                showChatListScreen();
            } else {
                showAlert("Login Failed", "Invalid credentials");
                closeConnection();
            }
        } catch (Exception e) {
            showAlert("Connection Error", e.getMessage());
        }
    }

    private void showChatListScreen() {
        primaryStage.setWidth(450);
        primaryStage.setHeight(700);
        primaryStage.setTitle("ChatHub - " + currentUsername);
        
        HBox topBar = createTopBar();
        
        chatListView = new ListView<>(chats);
        chatListView.setCellFactory(param -> new ChatPreviewCell());
        chatListView.setStyle("-fx-background-color: #0B141A; -fx-control-inner-background: #0B141A;");
        VBox.setVgrow(chatListView, Priority.ALWAYS);
        
        chatListView.setOnMouseClicked(e -> {
            ChatPreview selected = chatListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentChatContact = selected.getContactName();
                showChatWindow(selected.getContactName());
            }
        });
        
        loadChats();
        
        VBox mainLayout = new VBox(0);
        mainLayout.setStyle("-fx-background-color: #0B141A;");
        mainLayout.getChildren().addAll(topBar, new Separator(), chatListView);
        
        Scene chatListScene = new Scene(mainLayout, 450, 700);
        chatListScene.getStylesheets().add(getClass().getResource("/whatsapp-style.css").toExternalForm());
        primaryStage.setScene(chatListScene);
    }

    private HBox createTopBar() {
        Label titleLabel = new Label("ChatHub");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #25D366;");
        
        TextField searchField = new TextField();
        searchField.setPromptText("Search contacts...");
        searchField.setStyle("-fx-font-size: 12px; -fx-padding: 8px; -fx-background-color: #1F2C33; -fx-text-fill: #E9EDEF; -fx-prompt-text-fill: #8696A0;");
        searchField.setPrefWidth(150);
        
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterChats(newVal));
        
        Button logoutButton = new Button("Logout");
        logoutButton.setStyle("-fx-font-size: 12px; -fx-background-color: #25D366; -fx-text-fill: white; -fx-padding: 6px 12px;");
        logoutButton.setOnAction(e -> handleLogout());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox topBar = new HBox(10);
        topBar.setStyle("-fx-background-color: #111B21; -fx-padding: 10;");
        topBar.getChildren().addAll(titleLabel, searchField, spacer, logoutButton);
        
        return topBar;
    }

    private void filterChats(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            loadChats();
            return;
        }
        
        chats.clear();
        String[] defaultUsers = {"haritha", "aakash", "kaniska", "kabilan", "srivinay"};
        String searchLower = searchText.toLowerCase();
        
        for (String user : defaultUsers) {
            if (!user.equals(currentUsername) && user.toLowerCase().contains(searchLower)) {
                ObservableList<MessageItem> messages = conversationHistory.getOrDefault(user, FXCollections.observableArrayList());
                
                String lastMessage = "Click to open chat...";
                if (messages.size() > 0) {
                    lastMessage = messages.get(messages.size() - 1).getContent();
                }
                
                ChatPreview chat = new ChatPreview(user, lastMessage, getCurrentTime());
                chats.add(chat);
                
                if (!conversationHistory.containsKey(user)) {
                    conversationHistory.put(user, messages);
                }
            }
        }
    }

    private void loadChats() {
        chats.clear();
        String[] defaultUsers = {"haritha", "aakash", "kaniska", "kabilan", "srivinay"};
        
        List<ChatPreview> chatList = new ArrayList<>();
        for (String user : defaultUsers) {
            if (!user.equals(currentUsername)) {
                ObservableList<MessageItem> messages = conversationHistory.getOrDefault(user, FXCollections.observableArrayList());
                
                String lastMessage = "Click to open chat...";
                if (messages.size() > 0) {
                    lastMessage = messages.get(messages.size() - 1).getContent();
                }
                
                ChatPreview chat = new ChatPreview(user, lastMessage, getCurrentTime());
                chatList.add(chat);
                
                if (!conversationHistory.containsKey(user)) {
                    conversationHistory.put(user, messages);
                }
            }
        }
        
        // Sort by most recent message (reverse order)
        chatList.sort((a, b) -> {
            ObservableList<MessageItem> aMessages = conversationHistory.get(a.getContactName());
            ObservableList<MessageItem> bMessages = conversationHistory.get(b.getContactName());
            
            long aTime = aMessages.size() > 0 ? aMessages.size() : 0;
            long bTime = bMessages.size() > 0 ? bMessages.size() : 0;
            
            return Long.compare(bTime, aTime); // Descending order
        });
        
        chats.addAll(chatList);
    }

    private void showChatWindow(String contactName) {
        currentChatContact = contactName;
        
        for (ChatPreview chat : chats) {
            if (chat.getContactName().equals(contactName)) {
                chat.setUnread(false);
                chatListView.refresh();
                break;
            }
        }

        primaryStage.setWidth(500);
        primaryStage.setHeight(700);
        primaryStage.setTitle("ChatHub - " + contactName);
        
        HBox chatTopBar = createChatTopBar(contactName);
        
        messagesListView = new ListView<>();
        messagesListView.setCellFactory(param -> new MessageCell());
        
        ObservableList<MessageItem> messages = conversationHistory.getOrDefault(contactName, FXCollections.observableArrayList());
        conversationHistory.put(contactName, messages);
        
        messagesListView.setItems(messages);
        messagesListView.setStyle("-fx-background-color: #0B141A; -fx-control-inner-background: #0B141A;");
        VBox.setVgrow(messagesListView, Priority.ALWAYS);
        
        messages.addListener((ListChangeListener<MessageItem>) c -> {
            if (messagesListView.getItems().size() > 0) {
                messagesListView.scrollTo(messagesListView.getItems().size() - 1);
            }
        });
        
        HBox inputBox = createInputBox(contactName, messages);
        
        VBox chatLayout = new VBox(0);
        chatLayout.setStyle("-fx-background-color: #0B141A;");
        chatLayout.getChildren().addAll(chatTopBar, new Separator(), messagesListView, inputBox);
        
        Scene chatScene = new Scene(chatLayout, 500, 700);
        chatScene.getStylesheets().add(getClass().getResource("/whatsapp-style.css").toExternalForm());
        primaryStage.setScene(chatScene);
    }

    private HBox createChatTopBar(String contactName) {
        StackPane profilePic = createProfilePicture(contactName, 40);
        
        Label contactLabel = new Label(contactName);
        contactLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #E9EDEF;");
        
        Label statusLabel = new Label("Online");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8696A0;");
        
        VBox contactInfo = new VBox(2);
        contactInfo.getChildren().addAll(contactLabel, statusLabel);
        
        Button backButton = new Button("[Back]");
        backButton.setStyle("-fx-font-size: 12px; -fx-background-color: transparent; -fx-text-fill: #25D366;");
        backButton.setOnAction(e -> showChatListScreen());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox topBar = new HBox(10);
        topBar.setStyle("-fx-background-color: #111B21; -fx-padding: 10;");
        topBar.getChildren().addAll(backButton, profilePic, contactInfo, spacer);
        
        return topBar;
    }

    private HBox createInputBox(String contactName, ObservableList<MessageItem> messages) {
        TextField inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.setStyle("-fx-font-size: 12px; -fx-padding: 10px; -fx-background-color: #1F2C33; -fx-text-fill: #E9EDEF;");
        
        inputField.setOnAction(e -> sendMessage(inputField, contactName, messages));
        HBox.setHgrow(inputField, Priority.ALWAYS);
        
        Button sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-padding: 8px 16px;");
        sendButton.setOnAction(e -> sendMessage(inputField, contactName, messages));
        
        HBox inputBox = new HBox(8);
        inputBox.setStyle("-fx-background-color: #111B21; -fx-padding: 10;");
        inputBox.getChildren().addAll(inputField, sendButton);
        
        return inputBox;
    }

    private void sendMessage(TextField inputField, String contactName, ObservableList<MessageItem> messages) {
        String msgText = inputField.getText().trim();
        if (!msgText.isEmpty()) {
            MessageItem sentMessage = new MessageItem(msgText, "You", true, getCurrentTime(), false);
            messages.add(sentMessage);
            out.println("MSG:" + msgText);
            updateChatPreview(contactName, msgText);
            inputField.clear();
            
            Platform.runLater(() -> {
                sentMessage.setDelivered(true);
                messagesListView.refresh();
            });
        }
    }

    private void updateChatPreview(String contactName, String lastMessage) {
        for (ChatPreview chat : chats) {
            if (chat.getContactName().equals(contactName)) {
                chat.setLastMessage(lastMessage);
                chat.setTimestamp(getCurrentTime());
                chatListView.refresh();
                break;
            }
        }
    }

    private void startMessageReceiver() {
        messageReceiver = new Thread(() -> {
            try {
                String message;
                while (isConnected && (message = in.readLine()) != null) {
                    System.out.println("[RECEIVER] Got: " + message);
                    
                    if (message.startsWith("[")) {
                        int firstCloseBracket = message.indexOf("]");
                        String timeAndMessage = message.substring(firstCloseBracket + 2);
                        
                        int colonIndex = timeAndMessage.indexOf(": ");
                        if (colonIndex > -1) {
                            String sender = timeAndMessage.substring(0, colonIndex);
                            String content = timeAndMessage.substring(colonIndex + 2);
                            String time = message.substring(1, firstCloseBracket);
                            
                            System.out.println("[DEBUG] Sender: " + sender + " | Content: " + content);
                            System.out.println("[DEBUG] Current Contact: " + currentChatContact);
                            
                            if (sender.equals("System")) {
                                continue;
                            }
                            
                            Platform.runLater(() -> {
                                ObservableList<MessageItem> msgs = conversationHistory.get(sender);
                                
                                if (msgs == null) {
                                    msgs = FXCollections.observableArrayList();
                                    conversationHistory.put(sender, msgs);
                                }
                                
                                MessageItem receivedMessage = new MessageItem(content, sender, false, time, true);
                                msgs.add(receivedMessage);
                                
                                System.out.println("[DEBUG] Message added. Total: " + msgs.size());
                                
                                if (currentChatContact != null && currentChatContact.equals(sender) && messagesListView != null) {
                                    System.out.println("[DEBUG] Chat open - refreshing UI");
                                    messagesListView.setItems(msgs);
                                } else {
                                    System.out.println("[DEBUG] Chat closed - saved to history");
                                }
                                
                                updateChatPreview(sender, content);
                                
                                if (currentChatContact == null || !currentChatContact.equals(sender)) {
                                    for (ChatPreview chat : chats) {
                                        if (chat.getContactName().equals(sender)) {
                                            chat.setUnread(true);
                                            chatListView.refresh();
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                if (isConnected) {
                    System.err.println("[RECEIVER ERROR] " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        
        messageReceiver.setDaemon(true);
        messageReceiver.start();
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a");
        return sdf.format(new Date());
    }

    private void handleLogout() {
        closeConnection();
        showLoginScreen();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeConnection() {
        if (isConnected) {
            isConnected = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private StackPane createProfilePicture(String name, int size) {
        Circle circle = new Circle(size / 2);
        String color = PROFILE_COLORS.getOrDefault(name.toLowerCase(), "#999999");
        circle.setFill(Color.web(color));
        
        Label initials = new Label(name.substring(0, 1).toUpperCase());
        initials.setFont(new Font(size / 2));
        initials.setTextFill(Color.WHITE);
        
        StackPane stack = new StackPane(circle, initials);
        stack.setPrefSize(size, size);
        return stack;
    }

    @Override
    public void stop() {
        closeConnection();
    }
}

class ChatPreviewCell extends ListCell<ChatPreview> {
    private HBox container;
    private Label nameLabel;
    private Label messageLabel;
    private Label timeLabel;
    
    public ChatPreviewCell() {
        nameLabel = new Label();
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #E9EDEF;");
        
        messageLabel = new Label();
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8696A0;");
        
        timeLabel = new Label();
        timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8696A0;");
        
        VBox leftBox = new VBox(3);
        leftBox.getChildren().addAll(nameLabel, messageLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        container = new HBox(10);
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: #111B21; -fx-border-color: #1F2C33; -fx-border-width: 0 0 1 0;");
        
        setOnMouseEntered(e -> container.setStyle("-fx-background-color: #202C33; -fx-border-color: #1F2C33; -fx-border-width: 0 0 1 0;"));
        setOnMouseExited(e -> container.setStyle("-fx-background-color: #111B21; -fx-border-color: #1F2C33; -fx-border-width: 0 0 1 0;"));
    }
    
    @Override
    protected void updateItem(ChatPreview item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
        } else {
            nameLabel.setText(item.getContactName());
            messageLabel.setText(item.getLastMessage());
            timeLabel.setText(item.getTimestamp());
            
            StackPane profilePic = createProfilePictureStatic(item.getContactName(), 45);
            
            VBox textBox = new VBox(3);
            textBox.getChildren().addAll(nameLabel, messageLabel);
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            container.getChildren().clear();
            container.getChildren().addAll(profilePic, textBox, spacer, timeLabel);
            
            if (item.hasUnread()) {
                nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #25D366;");
                messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #25D366; -fx-font-weight: bold;");
            } else {
                nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #E9EDEF;");
                messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8696A0;");
            }
            
            setGraphic(container);
        }
    }
    
    private StackPane createProfilePictureStatic(String name, int size) {
        Circle circle = new Circle(size / 2);
        String color = ChatClient.PROFILE_COLORS.getOrDefault(name.toLowerCase(), "#999999");
        circle.setFill(Color.web(color));
        
        Label initials = new Label(name.substring(0, 1).toUpperCase());
        initials.setFont(new Font(size / 2));
        initials.setTextFill(Color.WHITE);
        
        StackPane stack = new StackPane(circle, initials);
        stack.setPrefSize(size, size);
        return stack;
    }
}

class ChatPreview {
    private String contactName;
    private String lastMessage;
    private String timestamp;
    private boolean hasUnread = false;
    
    public ChatPreview(String contactName, String lastMessage, String timestamp) {
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
    }
    
    public String getContactName() { return contactName; }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public boolean hasUnread() { return hasUnread; }
    
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setUnread(boolean unread) { this.hasUnread = unread; }
}

class MessageItem {
    private String content;
    private String sender;
    private boolean isSent;
    private String timestamp;
    private boolean isDelivered;
    
    public MessageItem(String content, String sender, boolean isSent, String timestamp, boolean isDelivered) {
        this.content = content;
        this.sender = sender;
        this.isSent = isSent;
        this.timestamp = timestamp;
        this.isDelivered = isDelivered;
    }
    
    public String getContent() { return content; }
    public String getSender() { return sender; }
    public boolean isSent() { return isSent; }
    public String getTimestamp() { return timestamp; }
    public boolean isDelivered() { return isDelivered; }
    public void setDelivered(boolean delivered) { this.isDelivered = delivered; }
}

class MessageCell extends ListCell<MessageItem> {
    private HBox container;
    
    @Override
    protected void updateItem(MessageItem item, boolean empty) {
        super.updateItem(item, empty);
        
        if (empty || item == null) {
            setGraphic(null);
        } else {
            VBox messageBox = new VBox(2);
            
            HBox contentWithCheckmark = new HBox(5);
            contentWithCheckmark.setAlignment(Pos.CENTER_LEFT);
            
            Label contentLabel = new Label(item.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(300);
            
            Label checkmark = new Label();
            if (item.isSent()) {
                if (item.isDelivered()) {
                    checkmark.setText("✓✓");
                    checkmark.setStyle("-fx-text-fill: #25D366; -fx-font-weight: bold; -fx-font-size: 12px;");
                } else {
                    checkmark.setText("✓");
                    checkmark.setStyle("-fx-text-fill: #8696A0; -fx-font-size: 12px;");
                }
                contentWithCheckmark.getChildren().addAll(contentLabel, checkmark);
            } else {
                contentWithCheckmark.getChildren().add(contentLabel);
            }
            
            Label timeLabel = new Label(item.getTimestamp());
            timeLabel.setStyle("-fx-font-size: 9px;");
            
            messageBox.getChildren().addAll(contentWithCheckmark, timeLabel);
            
            if (item.isSent()) {
                contentLabel.setStyle("-fx-text-fill: white; -fx-padding: 8px 12px;");
                timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #CCCCCC;");
                messageBox.setStyle("-fx-background-color: #005C4B; -fx-background-radius: 10; -fx-padding: 0;");
                
                container = new HBox();
                container.setPadding(new Insets(5));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                container.getChildren().addAll(spacer, messageBox);
                container.setStyle("-fx-background-color: #0B141A;");
            } else {
                contentLabel.setStyle("-fx-text-fill: #E9EDEF; -fx-padding: 8px 12px;");
                timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #8696A0;");
                messageBox.setStyle("-fx-background-color: #1F2C33; -fx-background-radius: 10; -fx-padding: 0;");
                
                container = new HBox();
                container.setPadding(new Insets(5));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                container.getChildren().addAll(messageBox, spacer);
                container.setStyle("-fx-background-color: #0B141A;");
            }
            
            setGraphic(container);
        }
    }
}