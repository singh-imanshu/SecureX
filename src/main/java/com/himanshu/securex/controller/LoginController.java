package com.himanshu.securex.controller;

import com.himanshu.securex.auth.AuthManager;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Arrays;

public class LoginController {
    private final VBox view;
    private final AuthManager authManager;
    private final Stage stage;

    public LoginController(Stage stage) {
        this.stage = stage;
        this.authManager = new AuthManager();
        this.view = new VBox(10);
        this.view.setPadding(new Insets(15));

        Label label = new Label(authManager.masterPasswordExists() ? "Login" : "Create Master Password");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter master password");

        Button actionBtn = new Button(authManager.masterPasswordExists() ? "Login" : "Sign Up");
        actionBtn.setDefaultButton(true);

        actionBtn.setOnAction(e -> {
            char[] pwd = passwordField.getText().toCharArray();
            passwordField.clear();

            if (pwd.length == 0) {
                showAlert(Alert.AlertType.ERROR, "Password cannot be empty");
                return;
            }

            if (authManager.masterPasswordExists()) {
                // IMPORTANT: verify with a COPY because verifyPassword (and HashUtil.verifyPassword) zero the array
                char[] pwdForVerify = Arrays.copyOf(pwd, pwd.length);
                boolean ok = authManager.verifyPassword(pwdForVerify);
                Arrays.fill(pwdForVerify, '\0'); // clear the verification copy promptly

                if (ok) {
                    // We need the salt to initialize the CryptoService for this session
                    byte[] salt = authManager.getSalt();
                    if (salt != null) {
                        // Pass the ORIGINAL password array to construct the session key.
                        // CryptoService will zero it; we also zero it after switching scenes.
                        switchToDashboard(pwd, salt);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Could not retrieve salt for decryption.");
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Incorrect password");
                }
            } else {
                if (authManager.saveMasterPassword(pwd)) {
                    showAlert(Alert.AlertType.INFORMATION, "Master password created! Please restart the app to log in.");
                    stage.close();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Could not save master password. Check file permissions.");
                }
            }
            // Clear the original password after it's handed off
            Arrays.fill(pwd, '\0');
        });

        view.getChildren().addAll(label, passwordField, actionBtn);
    }

    public VBox getView() {
        return view;
    }

    private void switchToDashboard(char[] masterPassword, byte[] salt) {
        DashboardController dashboardController = new DashboardController(stage, masterPassword, salt);
        Scene dashboardScene = new Scene(dashboardController.getView(), 800, 600);

        stage.setTitle("SecureX - Dashboard");
        stage.setResizable(true);
        stage.setScene(dashboardScene);
        stage.centerOnScreen();
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}