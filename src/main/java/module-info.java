module com.micro.kfschecker {
    requires javafx.controls;
    requires javafx.fxml;
    requires itextpdf;
    requires org.apache.commons.io;
    requires org.apache.logging.log4j.core;
    requires org.apache.commons.csv;
    requires org.slf4j;
    requires java.sql;


    opens com.micro.kfschecker to javafx.fxml;
    exports com.micro.kfschecker;
}