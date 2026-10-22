package org.example.myfirstpro;

import de.uni_leipzig.simba.keydiscovery.model.CandidateNode;
import de.uni_leipzig.simba.keydiscovery.rockerone.Rocker;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.SQLException;
import java.util.Set;


public class MyApplication extends Application {

    Logger logger = LoggerFactory.getLogger(MyApplication.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            URL resource = getClass().getResource("/styles.css");
            if (resource != null) {
                scene.getStylesheets().add(resource.toExternalForm());
            } else {
                logger.error("Stylesheet not found!");
            }
            primaryStage.setTitle("RDF Validator And Shapes Generator");
            primaryStage.setScene(scene);
            primaryStage.show();
//            String[] command = {
//                    "java",
//                    "-Xmx8g",
//                    "-jar",
//                    "C:\\Users\\asus\\Downloads\\rocker-1.2.1-full.jar",
//                    "restaurant_1",
//                    "file:///C:/Users/asus/Desktop/YA_Bui.ttl",  // Fixed file path
//                    "http://www.okkam.org/ontology_restaurant1.owl#Restaurant",
//                    "false",
//                    "true",
//                    "1.0"
//            };
//            String[] command = {
//                    "java",
//                    "-Xmx8g",
//                    "-jar",
//                    "C:\\Users\\asus\\Downloads\\rocker-1.2.1-full.jar",
//                    "hhh",
//                    "file:///C:/Users/asus/Downloads/YA_Bui.ttl",  // Corrected file path
//                    "http://example.com/ns#hhh",
//                    "false",
//                    "true",
//                    "1.0"
//            };


//            try {
//                // Create ProcessBuilder instance
//                ProcessBuilder processBuilder = new ProcessBuilder(command);
//                processBuilder.redirectErrorStream(true); // Merge error stream with output
//
//                // Start the process
//                Process process = processBuilder.start();
//
//                // Read the output
//                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        System.out.println(line);
//                    }
//                }
//
//                // Wait for the process to complete and get the exit code
//                int exitCode = process.waitFor();
//                System.out.println("Process exited with code: " + exitCode);
//
//            } catch (IOException | InterruptedException e) {
//                e.printStackTrace();
//            }
             //           Rocker r = null;
//            try {
//                r = new Rocker("restaurant_1", "C:\\Users\\asus\\Downloads\\OAEI_2011_Restaurant_1.nt",
//                        "http://www.okkam.org/ontology_restaurant1.owl#Restaurant", false, true, 1.0);
//            } catch (ClassNotFoundException e) {
//                throw new RuntimeException(e);
//            } catch (SQLException e) {
//                throw new RuntimeException(e);
//            }
//            r.run();
//            Set<CandidateNode> results = r.getKeys();
//System.out.println("lllllllllllllll          "+results);

        } catch (Exception e) {
            logger.error("Error initializing application: ", e);
        }
    }

    public static void main(String[] args) {
        launch();

    }
}
