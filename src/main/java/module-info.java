module com.micro.kfschecker {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires itextpdf;
    requires org.apache.commons.csv;
    requires org.apache.commons.io;


    opens com.micro.kfschecker to javafx.fxml;
    exports com.micro.kfschecker;
}