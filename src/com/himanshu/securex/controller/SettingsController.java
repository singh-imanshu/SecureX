package com.himanshu.securex.controller;

import com.himanshu.securex.auth.AuthManager;
import com.himanshu.securex.services.StorageService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Arrays;

public class SettingsController {

    private final Stage owner;
    private final StorageService storageService;
    private final DashboardController dashboardController;
    private final AuthManager authManager;

    private Stage dialogStage;

    public SettingsController(Stage owner, StorageService storageService, DashboardController dashboardController) {
        this.owner = owner;
        this.storageService = storageService;
        this.dashboardController = dashboardController;
        this.authManager = new AuthManager();
    }

    public void show() {
        if (dialogStage == null) {
            dialogStage = buildDialogStage();
        }
        dialogStage.show(); // non-blocking
        dialogStage.toFront();
    }

    private Stage buildDialogStage() {
        Stage stage = new Stage();
        stage.setTitle("Settings");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);

        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText("Current master password");
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New master password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm new master password");

        grid.add(new Label("Current Password:"), 0, 0);
        grid.add(currentPassword, 1, 0);
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(newPassword, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmPassword, 1, 2);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(35);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(65);
        grid.getColumnConstraints().addAll(c0, c1);

        Button btnSave = new Button("Save");
        Button btnCancel = new Button("Cancel");
        HBox buttons = new HBox(10, btnCancel, btnSave);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        btnCancel.setOnAction(e -> stage.close());

        btnSave.setOnAction(e -> {
            char[] oldPwd = currentPassword.getText().toCharArray();
            char[] newPwd = newPassword.getText().toCharArray();
            char[] confirmPwd = confirmPassword.getText().toCharArray();

            try {
                if (newPwd.length == 0) {
                    showAlert(Alert.AlertType.ERROR, "New password cannot be empty.");
                    return;
                }
                if (!Arrays.equals(newPwd, confirmPwd)) {
                    showAlert(Alert.AlertType.ERROR, "New password and confirmation do not match.");
                    return;
                }

                // Use in-memory entries to avoid decrypting from disk
                boolean ok = authManager.changeMasterPassword(oldPwd, newPwd, storageService, dashboardController.snapshotEntries());
                dashboardController.onPasswordChanged(ok);
                if (ok) {
                    stage.close();
                }
            } finally {
                Arrays.fill(oldPwd, '\0');
                Arrays.fill(newPwd, '\0');
                Arrays.fill(confirmPwd, '\0');
                currentPassword.clear();
                newPassword.clear();
                confirmPassword.clear();
            }
        });

        root.setCenter(grid);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 420, 220);
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