package com.himanshu.securex.controller;

import com.himanshu.securex.model.PasswordEntry;
import com.himanshu.securex.services.CryptoService;
import com.himanshu.securex.services.StorageService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.Optional;

public class DashboardController {

    private final BorderPane view;
    private final Stage stage;
    private final StorageService storageService;

    private ObservableList<PasswordEntry> passwordEntries;
    private PasswordEntry currentlySelectedEntry = null;

    private ListView<PasswordEntry> entryListView;
    private TextField accountField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField plainPasswordField;
    private StackPane passwordContainer;

    // --- UI State Nodes ---
    private GridPane detailsPane;
    private Label emptyStateLabel;

    public DashboardController(Stage stage, char[] masterPassword) {
        this.stage = stage;
        CryptoService cryptoService = new CryptoService(masterPassword);
        this.storageService = new StorageService(cryptoService);
        Arrays.fill(masterPassword, '\0');

        this.view = new BorderPane();
        this.view.setPadding(new Insets(10));

        setupUI();
        loadEntries();
    }

    private void setupUI() {
        view.setTop(createTopBar());
        view.setCenter(createMainContentArea());
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(0, 0, 10, 0));
        Label title = new Label("SecureX - Password Manager");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> performLogout());
        topBar.getChildren().addAll(title, spacer, logoutBtn);
        return topBar;
    }

    private SplitPane createMainContentArea() {
        VBox leftPane = new VBox(10);
        passwordEntries = FXCollections.observableArrayList();
        entryListView = new ListView<>(passwordEntries);
        VBox.setVgrow(entryListView, Priority.ALWAYS);

        Button newButton = new Button("New Entry");
        newButton.setMaxWidth(Double.MAX_VALUE);
        newButton.setOnAction(e -> handleNewEntryClick());
        leftPane.getChildren().addAll(entryListView, newButton);

        detailsPane = createDetailsPane();
        emptyStateLabel = new Label("Select an entry to view details, or click 'New Entry' to begin.");
        emptyStateLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: grey;");
        StackPane rightPane = new StackPane(emptyStateLabel, detailsPane);

        // Set initial state
        detailsPane.setVisible(false);
        emptyStateLabel.setVisible(true);

        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, old, aNew) -> {
            currentlySelectedEntry = aNew;
            if (aNew != null) {
                populateDetails(aNew);
                detailsPane.setVisible(true);
                emptyStateLabel.setVisible(false);
            } else {
                clearDetailsFields();
                detailsPane.setVisible(false);
                emptyStateLabel.setVisible(true);
            }
        });

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.3);
        return splitPane;
    }

    private GridPane createDetailsPane() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(15);

        accountField = new TextField();
        usernameField = new TextField();
        createPasswordToggleField();

        grid.add(new Label("Account:"), 0, 0);
        grid.add(accountField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordContainer, 1, 2);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveCurrentEntry());
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteSelectedEntry());

        saveButton.disableProperty().bind(accountField.textProperty().isEmpty());
        deleteButton.disableProperty().bind(entryListView.getSelectionModel().selectedItemProperty().isNull());

        HBox buttonBar = new HBox(10, deleteButton, saveButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        grid.add(buttonBar, 1, 3);
        return grid;
    }

    private void createPasswordToggleField() {
        passwordField = new PasswordField();
        plainPasswordField = new TextField();

        plainPasswordField.managedProperty().bind(passwordField.managedProperty());
        plainPasswordField.visibleProperty().bind(passwordField.visibleProperty().not());
        passwordField.textProperty().bindBidirectional(plainPasswordField.textProperty());

        ToggleButton toggleButton = new ToggleButton("👁");
        toggleButton.setStyle("-fx-font-size: 14px;");
        passwordField.visibleProperty().bind(toggleButton.selectedProperty().not());

        HBox passwordBox = new HBox(5);
        StackPane fieldContainer = new StackPane(passwordField, plainPasswordField);
        HBox.setHgrow(fieldContainer, Priority.ALWAYS);
        passwordBox.getChildren().addAll(fieldContainer, toggleButton);

        passwordContainer = new StackPane(passwordBox);
    }

    private void handleNewEntryClick() {
        entryListView.getSelectionModel().clearSelection();
        currentlySelectedEntry = null;
        clearDetailsFields();
        detailsPane.setVisible(true);
        emptyStateLabel.setVisible(false);
        accountField.requestFocus();
    }

    private void populateDetails(PasswordEntry entry) {
        accountField.setText(entry.getAccount());
        usernameField.setText(entry.getUsername());
        passwordField.setText(entry.getPassword());
    }

    private void clearDetailsFields() {
        accountField.clear();
        usernameField.clear();
        passwordField.clear();
    }

    private void saveCurrentEntry() {
        String account = accountField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (account.trim().isEmpty() || username.trim().isEmpty() || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "All fields (Account, Username, Password) must be filled.");
            return;
        }

        if (currentlySelectedEntry != null) {
            currentlySelectedEntry.setAccount(account);
            currentlySelectedEntry.setUsername(username);
            currentlySelectedEntry.setPassword(password);
            entryListView.refresh();
        } else {
            PasswordEntry newEntry = new PasswordEntry(account, username, password);
            passwordEntries.add(newEntry);
            entryListView.getSelectionModel().select(newEntry);
        }
        saveEntries();
    }

    private void deleteSelectedEntry() {
        if (currentlySelectedEntry == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Entry");
        alert.setHeaderText("Are you sure you want to delete the entry for '" + currentlySelectedEntry.getAccount() + "'?");
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            passwordEntries.remove(currentlySelectedEntry);
            saveEntries();
        }
    }

    private void loadEntries() {
        try {
            passwordEntries.setAll(storageService.load());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Failed to load vault. It may be corrupt or the password may be incorrect.");
        }
    }

    private void saveEntries() {
        try {
            storageService.save(passwordEntries);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Failed to save vault.");
        }
    }

    private void performLogout() {
        LoginController loginController = new LoginController(stage);
        Scene loginScene = new Scene(loginController.getView(), 300, 200);
        stage.setTitle("SecureX - Login");
        stage.setResizable(false);
        stage.setScene(loginScene);
        stage.centerOnScreen();
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public BorderPane getView() {
        return view;
    }
}