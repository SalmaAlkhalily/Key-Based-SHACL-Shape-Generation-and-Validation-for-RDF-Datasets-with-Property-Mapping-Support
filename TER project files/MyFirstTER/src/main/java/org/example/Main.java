package org.example;

import javafx.application.Application;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class Main  extends Application {


        @Override
        public void start(Stage primaryStage) {
            // Create a FileChooser instance
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Resource File");

            // Set the initial directory (optional)
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            // Add file extension filters (optional)
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            // Show open file dialog
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                System.out.println("File selected: " + selectedFile.getAbsolutePath());
            } else {
                System.out.println("No file selected.");
            }
        }

        public static void main(String[] args) {
            launch(args);
        }
    }


