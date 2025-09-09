package com.himanshu.securex.controller;

import com.himanshu.securex.model.PasswordEntry;
import com.himanshu.securex.services.AutoLockService;
import com.himanshu.securex.services.ClipboardService;
import com.himanshu.securex.services.CryptoService;
import com.himanshu.securex.services.StorageService;
import com.himanshu.securex.util.PasswordGenerator;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DashboardController {

    private final BorderPane view;
    private final Stage stage;
    private final StorageService storageService;
    private final AutoLockService autoLockService;

    private ObservableList<PasswordEntry> passwordEntries;
    private PasswordEntry currentlySelectedEntry = null;

    private ListView<PasswordEntry> entryListView;
    private TextField accountField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField plainPasswordField;
    private StackPane passwordContainer;
    private Label feedbackLabel;

    private GridPane detailsPane;
    private Label emptyStateLabel;

    public DashboardController(Stage stage, char[] masterPassword, byte[] salt) {
        this.stage = stage;
        CryptoService cryptoService = new CryptoService(masterPassword, salt);
        this.storageService = new StorageService(cryptoService);
        Arrays.fill(masterPassword, '\0');

        this.autoLockService = new AutoLockService(5, this::performLogout);
        this.autoLockService.start();

        this.view = new BorderPane();
        this.view.setPadding(new Insets(10));

        this.view.setOnMouseMoved(e -> autoLockService.reset());
        this.view.setOnKeyPressed(e -> autoLockService.reset());

        setupUI();
        loadEntries();
    }

    // This constructor is for testing purposes
    DashboardController(Stage stage, StorageService storageService) {
        this.stage = stage;
        this.storageService = storageService;
        this.autoLockService = new AutoLockService(5, this::performLogout); // Dummy for testing
        this.view = new BorderPane();
        setupUI();
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

        Button restoreBtn = new Button("Restore from Backup");
        restoreBtn.setOnAction(e -> handleRestoreBackup());

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> performLogout());

        topBar.getChildren().addAll(title, spacer, restoreBtn, logoutBtn);
        return topBar;
    }

    private void handleRestoreBackup() {
        try {
            List<Path> backupFiles = storageService.getBackupFiles();
            if (backupFiles.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No backups found.");
                return;
            }

            List<String> backupChoices = backupFiles.stream()
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());

            ChoiceDialog<String> dialog = new ChoiceDialog<>(backupChoices.get(0), backupChoices);
            dialog.setTitle("Restore Vault");
            dialog.setHeaderText("Choose a backup to restore.");
            dialog.setContentText("Warning: This will overwrite your current vault.");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(selectedBackupName -> {
                try {
                    Path selectedBackupFile = backupFiles.stream()
                            .filter(p -> p.getFileName().toString().equals(selectedBackupName))
                            .findFirst()
                            .orElse(null);
                    if (selectedBackupFile != null) {
                        storageService.restoreFromBackup(selectedBackupFile);
                        showAlert(Alert.AlertType.INFORMATION, "Vault restored successfully! Reloading data.");
                        loadEntries(); // Reload the dashboard with the new data
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Failed to restore backup.");
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Could not read backup files.");
        }
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

        detailsPane.setVisible(false);
        emptyStateLabel.setVisible(true);

        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            // Clear the fields before populating them with new data to minimize memory exposure.
            if (oldSelection != null) {
                clearDetailsFields();
            }

            currentlySelectedEntry = newSelection;
            if (newSelection != null) {
                populateDetails(newSelection);
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

        Button copyUserButton = new Button("Copy");
        copyUserButton.setOnAction(e -> copyToClipboard(usernameField.getText(), "Username"));
        HBox userBox = new HBox(5, usernameField, copyUserButton);
        HBox.setHgrow(usernameField, Priority.ALWAYS);

        Button copyPassButton = new Button("Copy");
        copyPassButton.setOnAction(e -> copyToClipboard(passwordField.getText(), "Password"));
        Button generateButton = new Button("Generate");
        generateButton.setOnAction(e -> handleGeneratePassword());
        HBox passwordBox = new HBox(5, passwordContainer, copyPassButton, generateButton);
        HBox.setHgrow(passwordContainer, Priority.ALWAYS);

        grid.add(new Label("Account:"), 0, 0);
        grid.add(accountField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(userBox, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordBox, 1, 2);

        feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-text-fill: green;");
        grid.add(feedbackLabel, 1, 3);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveCurrentEntry());
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteSelectedEntry());

        saveButton.disableProperty().bind(accountField.textProperty().isEmpty());
        deleteButton.disableProperty().bind(entryListView.getSelectionModel().selectedItemProperty().isNull());

        HBox buttonBar = new HBox(10, deleteButton, saveButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        grid.add(buttonBar, 1, 4);
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

        HBox passwordToggleBox = new HBox(5);
        StackPane fieldContainer = new StackPane(passwordField, plainPasswordField);
        HBox.setHgrow(fieldContainer, Priority.ALWAYS);
        passwordToggleBox.getChildren().addAll(fieldContainer, toggleButton);

        passwordContainer = new StackPane(passwordToggleBox);
    }

    private void handleGeneratePassword() {
        char[] generatedPassword = PasswordGenerator.generatePassword(16);
        if (!passwordField.getText().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Overwrite Password");
            alert.setHeaderText("Are you sure you want to overwrite the current password?");
            alert.setContentText("This action cannot be undone.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                passwordField.setText(new String(generatedPassword));
            }
        } else {
            passwordField.setText(new String(generatedPassword));
        }
        Arrays.fill(generatedPassword, '\0'); // Clear the generated password from memory
    }

    private void copyToClipboard(String text, String fieldName) {
        if (text == null || text.isEmpty()) return;
        ClipboardService.copyToClipboard(text);
        showFeedback(fieldName + " copied to clipboard.");
    }

    private void showFeedback(String message) {
        feedbackLabel.setText(message);
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> feedbackLabel.setText(""));
        pause.play();
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
        passwordField.setText(new String(entry.getPassword()));
    }

    private void clearDetailsFields() {
        accountField.clear();
        usernameField.clear();
        passwordField.clear();
    }

    private void saveCurrentEntry() {
        String account = accountField.getText();
        String username = usernameField.getText();
        char[] password = passwordField.getText().toCharArray();

        if (account.trim().isEmpty() || username.trim().isEmpty() || password.length == 0) {
            showAlert(Alert.AlertType.ERROR, "All fields (Account, Username, Password) must be filled.");
            Arrays.fill(password, '\0');
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

        Arrays.fill(password, '\0'); // Clear password from memory after use
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
        autoLockService.stop();
        // Securely clear all password entries from memory before logging out
        for (PasswordEntry entry : passwordEntries) {
            entry.clearPassword();
        }
        passwordEntries.clear();

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