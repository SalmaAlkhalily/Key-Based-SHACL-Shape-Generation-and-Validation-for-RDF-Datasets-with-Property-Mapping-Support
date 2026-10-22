package org.example.myfirstpro;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.spark.api.java.function.FilterFunction;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.*;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.myfirstpro.InterfacesUtils.showAlert;

public class Tab3Controller {
    @FXML
    private ProgressIndicator validationLoadingIndicator;
    @FXML
    private StyleClassedTextArea validateTextArea;
    @FXML
    private StyleClassedTextArea shapesFileTextArea;
    @FXML
    private StyleClassedTextArea dataFileTextArea;
    @FXML
    private Button validateButton;
    @FXML
    private VBox equivVboxValidate;
    @FXML
    private Button downloadReportButton;
    Map<String, String> equivalentsMap = new HashMap<>();

    private void updateToggle() {
        boolean showEquivComponents = InterfacesUtils.equiv.get();
        equivVboxValidate.setVisible(showEquivComponents);
        equivVboxValidate.setManaged(showEquivComponents);
    }

    @FXML
    private void initialize() {

        updateToggle();
        InterfacesUtils.equiv.addListener((observable, oldValue, newValue) -> {
            updateToggle();
            shapesFileTextArea.clear();
            dataFileTextArea.clear();

            mappingTextAreaValidate.clear();
            validateTextArea.clear();
            validateButton.setDisable(true);
            downloadReportButton.setDisable(true);
        });

    }

    private String dataFilePath = null;
    private String shapesFilePath = null;

    private File processDataFile(String inputFilePath) {
        Platform.runLater(() -> validateTextArea.appendText("Starting RDF processing...\n"));
        System.setProperty("hadoop.native.io.disabled", "true");

        SparkSession spark = SparkSession.builder()
                .appName("RDF Processor")
                .master("local[*]")
                .getOrCreate();

        String outputPath = "output/";
        String prefix = "http://example.com/ns#";
        Dataset<Row> rdfData = spark.read().text(inputFilePath);

        Dataset<Row> transformedData = rdfData
                .filter((FilterFunction<Row>) row -> !row.getString(0).trim().isEmpty())
                .map((MapFunction<Row, String>) row -> {
                    String line = row.getString(0).trim();
                    String[] parts = line.split(" ", 3);
                    if (parts.length != 3) return "";

                    String subject = parts[0].replace("<", "").replace(">", "");
                    String predicate = parts[1].replace("<", "").replace(">", "");
                    String object = parts[2].replace("<", "").replace(">", "").replace(".", "");
                    object = object.replace("\"", "");

                    String transformedSubject = prefix + subject.replace("`", "_");
                    String transformedPredicate = prefix + predicate.toLowerCase().replace("`", "_");
                    String transformedObject = "\"" + object.replace("`", "_") + "\"";

                    if (transformedObject.startsWith("\"")
                            && transformedObject.contains("-")
                            && transformedObject.contains("##")) {
                        transformedObject = transformedObject
                                .replace("\"", "")
                                .replace("##", "01");
                        transformedObject = "\"" + transformedObject + "\"";
                    }

                    return "<" + transformedSubject + "> <" + transformedPredicate + "> " + transformedObject + " .";
                }, Encoders.STRING()).toDF();

        Platform.runLater(() -> validateTextArea.appendText("Transformation complete. Writing to output...\n"));
        transformedData.coalesce(1).write().mode(SaveMode.Overwrite).text(outputPath);

        Platform.runLater(() -> validateTextArea.appendText("Processing complete. Output written to " + outputPath + "\n"));

        spark.stop();

        File outputDir = new File(outputPath);
        File[] files = outputDir.listFiles((dir, name) -> name.startsWith("part-"));

        if (files != null && files.length > 0) {
            File partFile = files[0];
            Path targetPath = Paths.get(outputPath + "processed_data.ttl");

            try {
                Files.move(partFile.toPath(), targetPath);
                Platform.runLater(() -> validateTextArea.appendText("File renamed to: " + targetPath + "\n"));
                return targetPath.toFile();
            } catch (Exception e) {
                Platform.runLater(() -> validateTextArea.appendText("Error renaming file: " + e.getMessage() + "\n"));
                throw new RuntimeException("Failed to rename the output file.", e);
            }
        } else {
            Platform.runLater(() -> validateTextArea.appendText("No part files found in the output directory.\n"));
            throw new RuntimeException("No part files found in the output directory.");
        }
    }

    private void validateAgainstEquivalentData() {
        validationLoadingIndicator.setVisible(true);
        Platform.runLater(() -> validateTextArea.clear());

        new Thread(() -> {
            if (dataFilePath == null || shapesFilePath == null || mappingFilePath == null) {
                Platform.runLater(() -> {
                    showAlert("Error", "Please choose all required files", Alert.AlertType.ERROR);
                    validationLoadingIndicator.setVisible(false);
                });
                return;
            }

            try {
                // Starting RDF processing message
                Platform.runLater(() -> validateTextArea.appendText("Starting RDF processing...\n"));

                // Process data file
                File processedDataFile = processDataFile(dataFilePath);
                equivalentsMap = parseMappingFile(mappingTextAreaValidate.getText());

                // Modify data content based on mappings
                String modifiedDataContent = Files.readString(processedDataFile.toPath());
                for (Map.Entry<String, String> entry : equivalentsMap.entrySet()) {
                    String key = entry.getKey().trim();
                    String value = entry.getValue().trim();
                    modifiedDataContent = modifiedDataContent.replaceAll("\\b" + key + "\\b", value);
                }

                // Output transformation complete message
                Platform.runLater(() -> validateTextArea.appendText("Transformation complete. Writing to output...\n"));

                String SHAPES = shapesFilePath;
                Graph shapesGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(shapesGraph, SHAPES);

                // Output shapes file loaded message
                Platform.runLater(() -> validateTextArea.appendText("Shapes file loaded: " + SHAPES + "\n"));

                // Create data graph from modified content
                Graph dataGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(dataGraph, new StringReader(modifiedDataContent), null, Lang.TURTLE);

                // Write the data graph to a string and output it with single-line formatting
                StringWriter dataGraphWriter = new StringWriter();
                RDFDataMgr.write(dataGraphWriter, dataGraph, Lang.TURTLE);

                // Post-process the RDF data to make it single-line and remove the full URI
                String formattedData = dataGraphWriter.toString()
                        .replaceAll("\n", "") // Remove all newlines
                        .replaceAll("\\s+", " ") // Replace multiple spaces with a single space
                        .replaceAll(" <(http://[^>]+)> <", "<$1> <") // Ensure that the angle brackets are kept together for the subject and predicate
                        .replaceAll("> \"([^\"]+)\" \\.", "> \"$1\" . \"\n"); // Ensure the object is properly formatted

                // Replace full URIs with just the local part of the resource
                String finalFormattedData = formattedData.replaceAll("<http://example.com/ns#([a-zA-Z_][a-zA-Z0-9_]*)>", "<$1>");

                // Output the formatted data graph
                Platform.runLater(() -> validateTextArea.appendText("Loaded Data Graph:\n" + finalFormattedData + "\n"));

                // Start validation process
                Shapes shapes = Shapes.parse(shapesGraph);
                ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), dataGraph);

                // Output validation report generation message
                Platform.runLater(() -> validateTextArea.appendText("Validation report generated.\n"));

                // Write the validation report to a string and output it
                StringWriter reportWriter = new StringWriter();
                RDFDataMgr.write(reportWriter, report.getModel(), Lang.TURTLE);
                //Platform.runLater(() -> validateTextArea.appendText("Validation Report:\n" + reportWriter.toString() + "\n"));

                // Count violations and output the violation summary
                Map<String, Integer> violationCounts = new HashMap<>();
                AtomicInteger totalViolations = new AtomicInteger(0);
                Model reportModel = report.getModel();
                report.getEntries().forEach(entry -> {
                    Node sourceConstraintNode = entry.sourceConstraintComponent();
                    RDFNode sourceConstraint = reportModel.asRDFNode(sourceConstraintNode);
                    String constraintName = sourceConstraint != null ? sourceConstraint.toString() : "Unknown Constraint";
                    String resultMessage = entry.message();
                    String uniqueConstraintKey = constraintName + " - " + resultMessage;
                    violationCounts.put(uniqueConstraintKey, violationCounts.getOrDefault(uniqueConstraintKey, 0) + 1);
                    totalViolations.incrementAndGet();
                });

                // Output violation counts by individual constraint
                Platform.runLater(() -> validateTextArea.appendText("Violation Summary:\n"));
                violationCounts.forEach((constraint, count) -> {
                    Platform.runLater(() -> validateTextArea.appendText("Constraint: " + constraint + "\n"));
                    Platform.runLater(() -> validateTextArea.appendText("Violations: " + count + "\n"));
                });

                // Output total violation count
                Platform.runLater(() -> validateTextArea.appendText("Total Violations: " + totalViolations.get() + "\n"));

                // Enable the download report button
                downloadReportButton.setDisable(false);

                // File processing complete message
                Platform.runLater(() -> validateTextArea.appendText("Processing complete. Output written to output/\n"));

                // Rename the file after processing (example path)
                File renamedFile = new File("output/processed_data.ttl");
                processedDataFile.renameTo(renamedFile);
                Platform.runLater(() -> validateTextArea.appendText("File renamed to: " + renamedFile.getAbsolutePath() + "\n"));

            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "Validation failed: " + e.getMessage(), Alert.AlertType.ERROR));
            } finally {
                Platform.runLater(() -> validationLoadingIndicator.setVisible(false));
            }
        }).start();
    }

    private void validateAgainstEquivalentData2() {
        validationLoadingIndicator.setVisible(true);
        Platform.runLater(() -> validateTextArea.clear());

        new Thread(() -> {
            if (dataFilePath == null || shapesFilePath == null || mappingFilePath == null) {
                Platform.runLater(() -> {
                    showAlert("Error", "Please choose all required files", Alert.AlertType.ERROR);
                    validationLoadingIndicator.setVisible(false);
                });
                return;
            }

            try {
                File processedDataFile = processDataFile(dataFilePath);
                equivalentsMap = parseMappingFile(mappingTextAreaValidate.getText());

                String modifiedDataContent = Files.readString(processedDataFile.toPath());
                for (Map.Entry<String, String> entry : equivalentsMap.entrySet()) {
                    String key = entry.getKey().trim();
                    String value = entry.getValue().trim();
                    modifiedDataContent = modifiedDataContent.replaceAll("\\b" + key + "\\b", value);
                }

                String modifiedDataMessage = "Modified Data Content:\n" + modifiedDataContent;
                Platform.runLater(() -> validateTextArea.appendText(modifiedDataMessage + "\n"));

                String SHAPES = shapesFilePath;
                Graph shapesGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(shapesGraph, SHAPES);

                String shapesMessage = "Shapes file: " + SHAPES;
                Platform.runLater(() -> validateTextArea.appendText(shapesMessage + "\n"));

                Graph dataGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(dataGraph, new StringReader(modifiedDataContent), null, Lang.TURTLE);

                StringWriter dataGraphWriter = new StringWriter();
                RDFDataMgr.write(dataGraphWriter, dataGraph, Lang.TURTLE);
                String dataGraphMessage = "Loaded Data Graph:\n" + dataGraphWriter;
                Platform.runLater(() -> validateTextArea.appendText(dataGraphMessage + "\n"));

                Shapes shapes = Shapes.parse(shapesGraph);
                ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), dataGraph);

                Platform.runLater(() -> validateTextArea.appendText("Validation report generated.\n"));

                StringWriter reportWriter = new StringWriter();
                RDFDataMgr.write(reportWriter, report.getModel(), Lang.TURTLE);
                String validationReportMessage = "Validation Report:\n" + reportWriter;
                Platform.runLater(() -> validateTextArea.appendText(validationReportMessage + "\n"));

                Map<String, Integer> violationCounts = new HashMap<>();
                AtomicInteger totalViolations = new AtomicInteger(0);
                Model reportModel = report.getModel();
                report.getEntries().forEach(entry -> {
                    Node sourceConstraintNode = entry.sourceConstraintComponent();
                    RDFNode sourceConstraint = reportModel.asRDFNode(sourceConstraintNode);
                    String constraintName = sourceConstraint != null ? sourceConstraint.toString() : "Unknown Constraint";
                    String resultMessage = entry.message();
                    String uniqueConstraintKey = constraintName + " - " + resultMessage;
                    violationCounts.put(uniqueConstraintKey, violationCounts.getOrDefault(uniqueConstraintKey, 0) + 1);
                    totalViolations.incrementAndGet();
                });

                if (totalViolations.get() > 0) {
                    Platform.runLater(() -> validateTextArea.appendText("Violation Counts by Individual Constraint:\n"));
                    violationCounts.forEach((constraint, count) -> {
                        Platform.runLater(() -> validateTextArea.appendText("Constraint: " + constraint + "\n"));
                        Platform.runLater(() -> validateTextArea.appendText("Violations: " + count + "\n"));
                    });
                }

                Platform.runLater(() -> validateTextArea.appendText("Total Number of Violations: " + totalViolations.get() + "\n"));
                downloadReportButton.setDisable(false);
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "Validation failed: " + e.getMessage(), Alert.AlertType.ERROR));
            } finally {
                Platform.runLater(() -> validationLoadingIndicator.setVisible(false));
            }
        }).start();
    }


    private Map<String, String> parseMappingFile(String mappingContent) {
        Map<String, String> equivalentsMap = new HashMap<>();
        for (String line : mappingContent.split("\\n")) {
            String[] parts = line.split(" equivalent ");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim().replace(".", "").replace(" ", "");
                equivalentsMap.put(key, value);
            }
        }
        return equivalentsMap;
    }

    @FXML
    private StyleClassedTextArea mappingTextAreaValidate;

    @FXML
    private void validate() {
        if (equivVboxValidate.isVisible())
            validateAgainstEquivalentData();
        else
            validateRdfData();
    }

    private void validateRdfData() {
        validationLoadingIndicator.setVisible(true);
        Platform.runLater(() -> validateTextArea.clear());
        new Thread(() -> {
            if (dataFilePath == null || shapesFilePath == null) {
                Platform.runLater(() -> {
                    showAlert("Error", "Please choose both a data file and a shapes file.", Alert.AlertType.ERROR);
                    validationLoadingIndicator.setVisible(false);
                });
                return;
            }

            try {
                File processedDataFile = processDataFile(dataFilePath);
                String DATA = processedDataFile.getAbsolutePath();
                String SHAPES = shapesFilePath;
                Graph shapesGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(shapesGraph, SHAPES);

                Platform.runLater(() -> validateTextArea.appendText("Data file: " + DATA + "\n"));
                Platform.runLater(() -> validateTextArea.appendText("Shapes file: " + SHAPES + "\n"));

                Graph dataGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(dataGraph, DATA);

                Shapes shapes = Shapes.parse(shapesGraph);
                Platform.runLater(() -> validateTextArea.appendText("Shapes graph loaded.\n"));

                ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), dataGraph);

                Platform.runLater(() -> validateTextArea.appendText("Validation report generated.\n"));
                Platform.runLater(() -> validateTextArea.appendText("Report: " + report.getEntries().toString() + "\n"));
                StringWriter rdfWriter = new StringWriter();
                RDFDataMgr.write(rdfWriter, report.getModel(), Lang.NT);
                Platform.runLater(() -> validateTextArea.appendText("RDF Validation Model:\n" + rdfWriter + "\n"));
                Map<String, Integer> violationCounts = new HashMap<>();
                AtomicInteger totalViolations = new AtomicInteger(0);
                Model reportModel = report.getModel();
                report.getEntries().forEach(entry -> {
                    Node sourceConstraintNode = entry.sourceConstraintComponent();
                    RDFNode sourceConstraint = reportModel.asRDFNode(sourceConstraintNode);
                    String constraintName = sourceConstraint != null ? sourceConstraint.toString() : "Unknown Constraint";
                    String resultMessage = entry.message();
                    String uniqueConstraintKey = constraintName + " - " + resultMessage;
                    violationCounts.put(uniqueConstraintKey, violationCounts.getOrDefault(uniqueConstraintKey, 0) + 1);
                    totalViolations.incrementAndGet();
                });

                if (totalViolations.get() > 0) {
                    Platform.runLater(() -> validateTextArea.appendText("Violation Counts by Individual Constraint:\n"));
                    violationCounts.forEach((constraint, count) -> {
                        Platform.runLater(() -> validateTextArea.appendText("Constraint: " + constraint + "\n"));
                        Platform.runLater(() -> validateTextArea.appendText("Violations: " + count + "\n"));
                    });
                }

                Platform.runLater(() -> validateTextArea.appendText("Total Number of Violations: " + totalViolations.get() + "\n"));
                downloadReportButton.setDisable(false);
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "Validation failed: " + e.getMessage(), Alert.AlertType.ERROR));
            } finally {
                Platform.runLater(() -> validationLoadingIndicator.setVisible(false));
            }
        }).start();
    }

    @FXML
    private void chooseShapesFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Shapes File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SHACL Files", "*.ttl"));

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            validationLoadingIndicator.setVisible(true);

            new Thread(() -> {
                try {
                    String content = Files.readString(selectedFile.toPath());

                    Platform.runLater(() -> {
                        shapesFileTextArea.replaceText(content);
                        shapesFilePath = selectedFile.getAbsolutePath();
                        validationLoadingIndicator.setVisible(false);
                        if (dataFilePath != null && shapesFilePath != null) {
                            if (equivVboxValidate.isVisible()) {
                                validateButton.setDisable(mappingTextAreaValidate.getText().isEmpty());
                            } else validateButton.setDisable(false);

                        } else
                            validateButton.setDisable(true);
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        showAlert("Error", "Failed to read shapes file: " + e.getMessage(), Alert.AlertType.ERROR);
                        validationLoadingIndicator.setVisible(false);
                    });
                }
            }).start();
        }
    }

    private String mappingFilePath;

    @FXML
    private void chooseMappingFileValidate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Mapping File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            validationLoadingIndicator.setVisible(true);

            new Thread(() -> {
                try {
                    String content = Files.readString(selectedFile.toPath());

                    Platform.runLater(() -> {
                        mappingTextAreaValidate.replaceText(content);
                        mappingFilePath = selectedFile.getAbsolutePath();
                        validationLoadingIndicator.setVisible(false);
                        if (dataFilePath != null && shapesFilePath != null) {
                            if (equivVboxValidate.isVisible()) {
                                validateButton.setDisable(mappingTextAreaValidate.getText().isEmpty());
                            } else validateButton.setDisable(false);

                        } else
                            validateButton.setDisable(true);

                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        showAlert("Error", "Failed to read data file: " + e.getMessage(), Alert.AlertType.ERROR);
                        validationLoadingIndicator.setVisible(false);
                    });
                }
            }).start();

        }
    }

    @FXML
    private void chooseDataFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Data File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("RDF Files", "*.nt"));

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            validationLoadingIndicator.setVisible(true);

            new Thread(() -> {
                try {
                    String content = Files.readString(selectedFile.toPath());

                    Platform.runLater(() -> {
                        dataFileTextArea.replaceText(content);
                        dataFilePath = selectedFile.getAbsolutePath();
                        validationLoadingIndicator.setVisible(false);
                        if (dataFilePath != null && shapesFilePath != null) {
                            if (equivVboxValidate.isVisible()) {
                                validateButton.setDisable(mappingTextAreaValidate.getText().isEmpty());
                            } else validateButton.setDisable(false);

                        } else
                            validateButton.setDisable(true);

                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        showAlert("Error", "Failed to read data file: " + e.getMessage(), Alert.AlertType.ERROR);
                        validationLoadingIndicator.setVisible(false);
                    });
                }
            }).start();

        }
    }

    @FXML
    public void downloadReport() {
        if (validateTextArea.getText().isEmpty()) {
            showAlert("Error", "No validation report to download", Alert.AlertType.ERROR);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("validation_report.pdf");

        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            try {
                saveReportAsPDF(selectedFile);
                showAlert("Success", "Validation report saved successfully as PDF!", Alert.AlertType.INFORMATION);
            } catch (IOException e) {
                showAlert("Error", "Failed to save the validation report as PDF: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void saveReportAsPDF(File file) throws IOException {
        PdfWriter writer = new PdfWriter(new FileOutputStream(file));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        String[] lines = validateTextArea.getText().split("\n");
        for (String line : lines) {
            document.add(new Paragraph(line));
        }

        document.close();
    }

}
