package com.himanshu.securex;

import com.himanshu.securex.controller.LoginController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        LoginController loginController = new LoginController(stage);
        Scene scene = new Scene(loginController.getView(), 300, 200);

        stage.setTitle("SecureX - Login");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}