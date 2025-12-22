module com.himanshu.securex {
    // JavaFX dependencies
    requires javafx.controls;
    requires javafx.fxml;

    // Third-party libraries
    requires com.google.gson;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    // Permissions
    opens com.himanshu.securex to javafx.fxml, javafx.graphics;
    opens com.himanshu.securex.controller to javafx.fxml;
    opens com.himanshu.securex.model to com.google.gson;

    exports com.himanshu.securex;
}