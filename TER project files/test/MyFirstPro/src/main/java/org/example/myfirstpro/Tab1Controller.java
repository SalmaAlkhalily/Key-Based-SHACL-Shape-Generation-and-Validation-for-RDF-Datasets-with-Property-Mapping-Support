package org.example.myfirstpro;

import de.uni_leipzig.simba.keydiscovery.model.CandidateNode;
import de.uni_leipzig.simba.keydiscovery.rockerone.Rocker;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.FileChooser;
import org.apache.hadoop.shaded.org.apache.commons.io.FilenameUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.example.myfirstpro.InterfacesUtils.convertRDF;
import static org.example.myfirstpro.InterfacesUtils.showAlert;

public class Tab1Controller {
    private String selectedFilePath;
    List<List<String>> almostKeys;
    @FXML
    private Button generateKeysButton;
    @FXML
    private StyleClassedTextArea rdfContentTextArea;
    @FXML
    private StyleClassedTextArea outputTextArea;
    @FXML
    private Button saveKeysButton;
    @FXML
    private ProgressIndicator loadingIndicator;




    private List<List<String>> parseAlmostKeysFromOutput(String output) {
        List<List<String>> keys = new ArrayList<>();
        String almostKeysOutput = output.split("0-almost keys:")[1].split("1-non keys:")[0].trim();
        String[] keyGroups = almostKeysOutput.split("], \\[");
        for (String group : keyGroups) {
            List<String> keyList = List.of(group.replace("[", "").replace("]", "").split(", "));
            keys.add(keyList);
        }
        return keys;
    }

    @FXML
    private void openRdfFileChooser() {
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            selectedFilePath = selectedFile.getAbsolutePath();
            String convertedFilePath = "myfile.nt";

            if (!FilenameUtils.getExtension(selectedFilePath).equals("nt")) {
                convertRDF(selectedFilePath, convertedFilePath);
                selectedFilePath = convertedFilePath; // Use the converted file
            }

            rdfContentTextArea.clear();
            almostKeys = new ArrayList<>();
            outputTextArea.clear();
            saveKeysButton.setDisable(true);
            loadingIndicator.setVisible(true);
            rdfContentTextArea.replaceText("Loading file content... Please wait.");

            new Thread(() -> {
                try {
                    String content = new String(Files.readAllBytes(Paths.get(selectedFilePath)));
                    javafx.application.Platform.runLater(() -> {
                        rdfContentTextArea.replaceText(content);
                        loadingIndicator.setVisible(false);
                        generateKeysButton.setDisable(false);
                        generateKeysButton.setOnAction(e -> generateKeys());
                    });
                } catch (IOException ex) {
                    javafx.application.Platform.runLater(() -> {
                        showAlert("Error", "Failed to read the file.", Alert.AlertType.ERROR);
                        loadingIndicator.setVisible(false);
                    });
                }
            }).start();
        }
    }

    @FXML
    private void generateKeys() {
        if (selectedFilePath != null && !selectedFilePath.isEmpty()) {

            String sakeyCommand = "java -jar sakeyTool\\sakey.jar \"" + selectedFilePath + "\" 1";
            loadingIndicator.setVisible(true);
            outputTextArea.clear();
            new Thread(() -> {
                try {
                    Process process = new ProcessBuilder(sakeyCommand.split(" ")).start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    int exitCode = process.waitFor();
                    javafx.application.Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        if (exitCode == 0) {
                            outputTextArea.replaceText(output.toString());
                            almostKeys = parseAlmostKeysFromOutput(output.toString());
                            saveKeysButton.setDisable(false);
                        } else {
                            showAlert("Error", "The sakey tool encountered an error.", Alert.AlertType.ERROR);
                        }
                    });
                } catch (IOException | InterruptedException ex) {
                    javafx.application.Platform.runLater(() -> {
                        showAlert("Error", "An error occurred while running the sakey tool.", Alert.AlertType.ERROR);
                        loadingIndicator.setVisible(false);
                    });
                }
            }).start();
        } else {
            showAlert("Error", "No RDF file selected.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void saveGeneratedKeysToFile() {
        String keysContent = outputTextArea.getText();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Generated Keys");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File selectedFile = fileChooser.showSaveDialog(null);

        if (selectedFile != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(selectedFile))) {
                writer.write(keysContent);
                showAlert("Success", "Generated keys saved successfully!", Alert.AlertType.CONFIRMATION);
            } catch (IOException e) {
                showAlert("Error", "Error saving keys: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

}
