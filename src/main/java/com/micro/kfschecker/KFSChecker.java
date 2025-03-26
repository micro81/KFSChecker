package com.micro.kfschecker;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class KFSChecker extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/main.fxml"));
        VBox root = loader.load();

        // Create a new Scene
        Scene scene = new Scene(root, 1500, 800);

        // Add the stylesheet to the scene
        scene.getStylesheets().add(getClass().getResource("css/main_style-IMG.css").toExternalForm());
        primaryStage.setTitle("KFS Checker");
        primaryStage.setScene(scene);
        primaryStage.show();

    }
}