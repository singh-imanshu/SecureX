package com.himanshu.securex.controller;

import com.himanshu.securex.auth.AuthManager;
import com.himanshu.securex.services.SettingsService;
import com.himanshu.securex.services.StorageService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.*;

public class SettingsController {
    private final Stage owner;
    private final StorageService storageService;
    private final DashboardController dashboardController;
    private final SettingsService settingsService; // NEW
    private final AuthManager authManager;

    private Stage dialogStage;
    private ChoiceBox<String> timeoutChoiceBox;

    // Map display strings to minute values
    private static final Map<String, Integer> TIMEOUT_OPTIONS = new LinkedHashMap<>();
    static {
        TIMEOUT_OPTIONS.put("1 Minute", 1);
        TIMEOUT_OPTIONS.put("5 Minutes", 5);
        TIMEOUT_OPTIONS.put("15 Minutes", 15);
        TIMEOUT_OPTIONS.put("30 Minutes", 30);
        TIMEOUT_OPTIONS.put("1 Hour", 60);
        TIMEOUT_OPTIONS.put("Never", -1);
    }

    public SettingsController(Stage owner, StorageService storageService, DashboardController dashboardController, SettingsService settingsService) {
        this.owner = owner;
        this.storageService = storageService;
        this.dashboardController = dashboardController;
        this.settingsService = settingsService;
        this.authManager = new AuthManager();
    }

    public void show() {
        if (dialogStage == null) {
            dialogStage = buildDialogStage();
        }
        dialogStage.show();
        dialogStage.toFront();
    }

    private Stage buildDialogStage() {
        Stage stage = new Stage();
        stage.setTitle("Settings");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        VBox contentBox = new VBox(20);

        //Section 1: Security Preferences
        TitledPane prefPane = new TitledPane();
        prefPane.setText("Security Preferences");
        prefPane.setCollapsible(false);

        GridPane prefGrid = new GridPane();
        prefGrid.setHgap(10);
        prefGrid.setVgap(10);
        prefGrid.setPadding(new Insets(10));

        Label timeoutLabel = new Label("Auto-Lock Timeout:");
        timeoutChoiceBox = new ChoiceBox<>();
        timeoutChoiceBox.getItems().addAll(TIMEOUT_OPTIONS.keySet());

        // Set current selection
        int currentTimeout = settingsService.getAutoLockTimeout();
        String currentKey = TIMEOUT_OPTIONS.entrySet().stream()
                .filter(e -> e.getValue() == currentTimeout)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("5 Minutes");
        timeoutChoiceBox.setValue(currentKey);

        prefGrid.add(timeoutLabel, 0, 0);
        prefGrid.add(timeoutChoiceBox, 1, 0);
        prefPane.setContent(prefGrid);

        //Section 2: Change Master Password
        TitledPane passwordPane = new TitledPane();
        passwordPane.setText("Change Master Password");
        passwordPane.setCollapsible(false);

        GridPane passGrid = new GridPane();
        passGrid.setHgap(10);
        passGrid.setVgap(12);
        passGrid.setPadding(new Insets(10));

        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText("Current master password");
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New master password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm new master password");

        passGrid.add(new Label("Current Password:"), 0, 0);
        passGrid.add(currentPassword, 1, 0);
        passGrid.add(new Label("New Password:"), 0, 1);
        passGrid.add(newPassword, 1, 1);
        passGrid.add(new Label("Confirm:"), 0, 2);
        passGrid.add(confirmPassword, 1, 2);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(35);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(65);
        passGrid.getColumnConstraints().addAll(c0, c1);
        passwordPane.setContent(passGrid);

        contentBox.getChildren().addAll(prefPane, passwordPane);

        //Buttons
        Button btnSave = new Button("Save Changes");
        Button btnCancel = new Button("Cancel");
        HBox buttons = new HBox(10, btnCancel, btnSave);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        btnCancel.setOnAction(e -> stage.close());

        btnSave.setOnAction(e -> {
            // 1. Save Preferences
            String selectedKey = timeoutChoiceBox.getValue();
            int newTimeout = TIMEOUT_OPTIONS.get(selectedKey);
            settingsService.setAutoLockTimeout(newTimeout);
            dashboardController.updateAutoLockTimeout(newTimeout);

            // 2. Handle Password Change (if fields are filled)
            char[] oldPwd = currentPassword.getText().toCharArray();
            char[] newPwd = newPassword.getText().toCharArray();
            char[] confirmPwd = confirmPassword.getText().toCharArray();

            boolean passwordChangeRequested = oldPwd.length > 0 || newPwd.length > 0;

            if (passwordChangeRequested) {
                if (newPwd.length == 0) {
                    showAlert(Alert.AlertType.ERROR, "New password cannot be empty.");
                    return;
                }
                if (!Arrays.equals(newPwd, confirmPwd)) {
                    showAlert(Alert.AlertType.ERROR, "New password and confirmation do not match.");
                    return;
                }

                // Logic from previous implementation
                Alert confirm = new Alert(Alert.AlertType.WARNING);
                confirm.setTitle("Confirm Password Change");
                confirm.setHeaderText("Backups will be Re-encrypted");
                confirm.setContentText("Changing your master password will re-encrypt your vault and backups.\nDo you want to continue?");
                ButtonType proceed = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirm.getButtonTypes().setAll(cancel, proceed);
                confirm.initOwner(owner);

                var result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != proceed) {
                    return;
                }

                boolean ok = authManager.changeMasterPassword(oldPwd, newPwd, storageService, dashboardController.snapshotEntries());
                dashboardController.onPasswordChanged(ok);
                if (ok) stage.close();

            } else {
                // Only settings changed
                stage.close();
                showAlert(Alert.AlertType.INFORMATION, "Settings saved.");
            }

            // Clean up
            Arrays.fill(oldPwd, '\0');
            Arrays.fill(newPwd, '\0');
            Arrays.fill(confirmPwd, '\0');
            currentPassword.clear();
            newPassword.clear();
            confirmPassword.clear();
        });

        root.setCenter(contentBox);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 450, 450);
        stage.setScene(scene);
        return stage;
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, msg, ButtonType.OK);
            alert.initOwner(owner);
            alert.setHeaderText(null);
            alert.show();
        });
    }
}