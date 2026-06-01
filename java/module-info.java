module com.example.bellman {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.bellman to javafx.fxml;
    exports com.example.bellman;
}