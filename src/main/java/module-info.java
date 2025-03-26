module com.micro.kfschecker {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires itextpdf;


    opens com.micro.kfschecker to javafx.fxml;
    exports com.micro.kfschecker;
}