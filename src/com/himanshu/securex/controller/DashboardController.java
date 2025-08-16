package com.himanshu.securex.controller;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class DashboardController {
    private final BorderPane view;
    private final Stage stage;

    public DashboardController(Stage stage) {
        this.stage = stage;
        this.view = new BorderPane();
        this.view.setPadding(new Insets(10));

        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(0, 0, 10, 0));
        Label title = new Label("SecureX - Password Manager");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> handleLogout());

        topBar.getChildren().addAll(title, spacer, logoutBtn);
        view.setTop(topBar);

        SplitPane mainContent = createMainContentArea();
        view.setCenter(mainContent);
    }

    /**
     * Creates the main content area with a list on the left and details on the right.
     * @return A configured SplitPane.
     */
    private SplitPane createMainContentArea() {
        ListView<String> entryList = new ListView<>();
        // TODO: Populate this list with actual data in the future
        entryList.getItems().addAll("Google", "Amazon", "GitHub");

        GridPane detailsPane = new GridPane();
        detailsPane.setPadding(new Insets(10));
        detailsPane.setHgap(10);
        detailsPane.setVgap(10);
        detailsPane.add(new Label("Select an item to see details."), 0, 0);
        // TODO: Populate this pane with fields for account, username, password etc.

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(entryList, detailsPane);
        splitPane.setDividerPositions(0.3);

        return splitPane;
    }


    public BorderPane getView() {
        return view;
    }

    /**
     * Handles the logout process by switching back to the Login view.
     */
    private void handleLogout() {
        LoginController loginController = new LoginController(stage);
        Scene loginScene = new Scene(loginController.getView(), 300, 200);
        stage.setTitle("SecureX - Login");
        stage.setResizable(false);
        stage.setScene(loginScene);
        stage.centerOnScreen();
    }
}