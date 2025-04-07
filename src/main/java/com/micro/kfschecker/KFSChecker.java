package com.micro.kfschecker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;


public class KFSChecker extends Application {

    private Controller controller; // Potřebujeme odkaz na váš Controller

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/main.fxml"));
        VBox root = loader.load();
        controller = loader.getController();
        //System.out.println("Instance controlleru: " + controller); // docasne pro ladeni

        // Create a new Scene
        Scene scene = new Scene(root, 1500, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("KFS Checker");
        primaryStage.show();

        // Načtení prvního CSS souboru
        scene.getStylesheets().add(getClass().getResource("css/main_style-IMG.css").toExternalForm());
        // Načtení druhého CSS souboru
        scene.getStylesheets().add(getClass().getResource("css/Style.css").toExternalForm());

    }

    @Override
    public void stop() throws Exception {
        //System.out.println("MainApp - Metoda stop() byla zavolána."); //docasne pro ladeni
        if (controller != null) {
            controller.shutdownExecutor();
        }
        super.stop(); // Důležité pro provedení standardních úklidových operací
    }

    public static void main(String[] args) {
        launch(args);
    }
}