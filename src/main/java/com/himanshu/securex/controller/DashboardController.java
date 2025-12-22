package com.himanshu.securex.controller;

import com.himanshu.securex.model.PasswordEntry;
import com.himanshu.securex.services.*;
import com.himanshu.securex.util.PasswordGenerator;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
import java.util.*;

public class DashboardController {

    private final BorderPane view;
    private final Stage stage;
    private final StorageService storageService;
    private final AutoLockService autoLockService;
    private final SettingsService settingsService; // NEW

    private ObservableList<PasswordEntry> passwordEntries;
    private PasswordEntry currentlySelectedEntry = null;

    private ListView<PasswordEntry> entryListView;
    private TextField accountField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField plainPasswordField;
    private StackPane passwordContainer;
    private Label feedbackLabel;

    private BorderPane detailsPane;
    private Label emptyStateLabel;

    public DashboardController(Stage stage, char[] masterPassword, byte[] salt) {
        this.stage = stage;

        // Initialize Settings Service
        this.settingsService = new SettingsService();

        CryptoService cryptoService = new CryptoService(Arrays.copyOf(masterPassword, masterPassword.length), salt);
        this.storageService = new StorageService(cryptoService);
        Arrays.fill(masterPassword, '\0');

        // Initialize AutoLock with User Preference
        int savedTimeout = settingsService.getAutoLockTimeout();
        this.autoLockService = new AutoLockService(savedTimeout, this::performLogout);
        this.autoLockService.start();

        this.view = new BorderPane();

        this.view.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        this.view.setPadding(new Insets(10));

        this.view.setOnMouseMoved(e -> autoLockService.reset());
        this.view.setOnKeyPressed(e -> autoLockService.reset());

        setupUI();
        loadEntries();
    }

    // For testing using JUnit
    DashboardController(Stage stage, StorageService storageService) {
        this.stage = stage;
        this.storageService = storageService;
        this.settingsService = new SettingsService();
        this.autoLockService = new AutoLockService(5, this::performLogout);
        this.view = new BorderPane();
        setupUI();
    }

    // NEW: Method to update timeout at runtime
    public void updateAutoLockTimeout(int minutes) {
        autoLockService.updateTimeout(minutes);
        autoLockService.reset(); // Apply immediately
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

        String blueGlowStyle = "-fx-focus-color: #0096C9; -fx-faint-focus-color: #0096C945; -fx-font-weight: bold;";

        Button settingsBtn = new Button("Settings");
        settingsBtn.setStyle(blueGlowStyle);
        settingsBtn.setOnAction(e -> openSettings());

        Button restoreBtn = new Button("Restore from Backup");
        restoreBtn.setStyle(blueGlowStyle);
        restoreBtn.setOnAction(e -> handleRestoreBackup());

        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle(blueGlowStyle);
        logoutBtn.setOnAction(e -> performLogout());

        topBar.getChildren().addAll(title, spacer, settingsBtn, restoreBtn, logoutBtn);
        return topBar;
    }

    private void openSettings() {
        SettingsController settings = new SettingsController(stage, storageService, this, settingsService);
        settings.show();
    }


    private void handleRestoreBackup() {
        try {
            List<Path> backupFiles = storageService.getBackupFiles();
            if (backupFiles.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No backups found.");
                return;
            }

            // Create a custom Dialog to allow rich tooltips (popups)
            Dialog<Path> dialog = new Dialog<>();
            dialog.setTitle("Restore Vault");
            dialog.setHeaderText("Choose a backup to restore.");
            dialog.setContentText("Hover over a file to see details.");

            // Use the current window as the owner
            dialog.initOwner(stage);

            // Create a ListView for the backups
            ListView<Path> list = new ListView<>();
            list.setItems(FXCollections.observableArrayList(backupFiles));
            list.setPrefHeight(200);

            // Custom Cell Factory to add Tooltips (Popups)
            list.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(Path item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        // Display the filename
                        setText(item.getFileName().toString());

                        // Fetch count using our fast metadata reader
                        int count = storageService.getEntryCountFast(item);
                        String msg = (count >= 0)
                                ? "This backup file has " + count + " passwords."
                                : "Could not read password count.";

                        // Create the "little popup"
                        Tooltip tooltip = new Tooltip(msg);
                        tooltip.setStyle("-fx-font-size: 14px;");
                        tooltip.setShowDelay(Duration.millis(300)); // Show quickly
                        setTooltip(tooltip);
                    }
                }
            });

            dialog.getDialogPane().setContent(list);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            // Convert the result: Return selected path if OK is clicked
            dialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    return list.getSelectionModel().getSelectedItem();
                }
                return null;
            });

            Optional<Path> result = dialog.showAndWait();
            result.ifPresent(selectedFile -> {
                try {
                    storageService.restoreFromBackup(selectedFile);
                    showAlert(Alert.AlertType.INFORMATION, "Vault restored successfully! Reloading data.");
                    loadEntries();
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
        String blueGlowStyle = "-fx-focus-color: #0096C9; -fx-faint-focus-color: #0096C945; -fx-font-weight: bold;";
        newButton.setStyle(blueGlowStyle);

        newButton.setMaxWidth(Double.MAX_VALUE);
        newButton.setOnAction(e -> handleNewEntryClick());
        leftPane.getChildren().addAll(entryListView, newButton);

        detailsPane = createDetailsPane();
        emptyStateLabel = new Label("Select an entry to view details, or click 'New Entry' to begin.");
        emptyStateLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: grey;");

        ScrollPane detailsScroll = new ScrollPane(detailsPane);
        detailsScroll.setFitToWidth(true);
        detailsScroll.setFitToHeight(true);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        StackPane rightStack = new StackPane(emptyStateLabel, detailsScroll);
        detailsPane.setVisible(false);
        emptyStateLabel.setVisible(true);

        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
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

        SplitPane splitPane = new SplitPane(leftPane, rightStack);
        splitPane.setDividerPositions(0.30);
        return splitPane;
    }

    private BorderPane createDetailsPane() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);

        accountField = new TextField();
        usernameField = new TextField();

        String defaultFocus = "-fx-focus-color: #0096C9; -fx-faint-focus-color: #0096C945;";
        accountField.setStyle(defaultFocus);
        usernameField.setStyle(defaultFocus);

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

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(25);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(75);
        grid.getColumnConstraints().addAll(c0, c1);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveCurrentEntry());
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteSelectedEntry());

        saveButton.disableProperty().bind(accountField.textProperty().isEmpty());
        deleteButton.disableProperty().bind(entryListView.getSelectionModel().selectedItemProperty().isNull());

        HBox buttonBar = new HBox(10, deleteButton, saveButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        root.setCenter(grid);
        root.setBottom(buttonBar);
        return root;
    }

    private void createPasswordToggleField() {
        passwordField = new PasswordField();
        plainPasswordField = new TextField();

        String defaultFocus = "-fx-focus-color: #0096C9; -fx-faint-focus-color: #0096C945;";
        passwordField.setStyle(defaultFocus);
        plainPasswordField.setStyle(defaultFocus);

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
        Arrays.fill(generatedPassword, '\0');
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

        Arrays.fill(password, '\0');
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
        for (PasswordEntry entry : passwordEntries) {
            entry.clearPassword();
        }
        passwordEntries.clear();

        LoginController loginController = new LoginController(stage);
        Scene loginScene = new Scene(loginController.getView(), 300, 200);
        stage.setTitle("SecureX - Login");
        stage.setResizable(false);
        stage.setScene(loginScene);
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, msg, ButtonType.OK);
            alert.setHeaderText(null);
            alert.initOwner(stage);
            alert.show();
        });
    }

    public List<PasswordEntry> snapshotEntries() {
        List<PasswordEntry> copy = new ArrayList<>(passwordEntries.size());
        for (PasswordEntry e : passwordEntries) {
            char[] pwd = e.getPassword() != null ? Arrays.copyOf(e.getPassword(), e.getPassword().length) : new char[0];
            copy.add(new PasswordEntry(e.getAccount(), e.getUsername(), pwd));
            Arrays.fill(pwd, '\0');
        }
        return copy;
    }

    public void onPasswordChanged(boolean success) {
        if (success) {
            showAlert(Alert.AlertType.INFORMATION, "Master password changed successfully. You will be logged out.");
            PauseTransition pause = new PauseTransition(Duration.seconds(0.1));
            pause.setOnFinished(e -> performLogout());
            pause.play();
        } else {
            showAlert(Alert.AlertType.ERROR, "Password could not be changed successfully.");
        }
    }

    public BorderPane getView() {
        return view;
    }
}