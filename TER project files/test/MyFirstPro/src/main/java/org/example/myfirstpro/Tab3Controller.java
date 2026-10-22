package org.example.myfirstpro;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import javafx.application.Platform;
import javafx.fxml.FXML;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.List;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import javafx.stage.FileChooser;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.spark.api.java.function.FilterFunction;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.*;
import org.controlsfx.validation.ValidationResult;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.myfirstpro.InterfacesUtils.convertRDF;
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
                Platform.runLater(() -> validateTextArea.appendText("Starting RDF processing...\n"));

                // Process RDF data file
                File processedDataFile = processDataFile(dataFilePath);
                equivalentsMap = parseMappingFile(mappingTextAreaValidate.getText());

                // Modify data content based on mappings
                String modifiedDataContent = Files.readString(processedDataFile.toPath());
                for (Map.Entry<String, String> entry : equivalentsMap.entrySet()) {
                    String key = entry.getKey().trim();
                    String value = entry.getValue().trim();
                    modifiedDataContent = modifiedDataContent.replaceAll("\\b" + key + "\\b", value);
                }

                Platform.runLater(() -> validateTextArea.appendText("Transformation complete. Writing to output...\n"));

                // Load Shapes Graph
                Graph shapesGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(shapesGraph, shapesFilePath);
                Platform.runLater(() -> validateTextArea.appendText("Shapes file loaded: " + shapesFilePath + "\n"));

                // Create Data Graph
                Graph dataGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(dataGraph, new StringReader(modifiedDataContent), null, Lang.TURTLE);

                // Perform SHACL validation
                Shapes shapes = Shapes.parse(shapesGraph);
                ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), dataGraph);
                Platform.runLater(() -> validateTextArea.appendText("Validation report generated.\n"));

                // Count violations and gather subjects
                Map<String, Integer> violationCounts = new HashMap<>();
                AtomicInteger totalViolations = new AtomicInteger(0);
                Map<String, Set<String>> violationSubjects = new HashMap<>(); // Map to store violation messages and their subjects
                Model reportModel = report.getModel();

                // Iterate through the validation report to count violations and track subjects
                report.getEntries().forEach(entry -> {

                    Node sourceConstraintNode = entry.sourceConstraintComponent();
                    RDFNode sourceConstraint = reportModel.asRDFNode(sourceConstraintNode);
                    String constraintName = sourceConstraint != null ? sourceConstraint.toString() : "Unknown Constraint";

                    String resultMessage = entry.message();
                    String uniqueConstraintKey = constraintName + " - " + resultMessage;

                    // Update violation counts
                    violationCounts.put(uniqueConstraintKey, violationCounts.getOrDefault(uniqueConstraintKey, 0) + 1);
                    totalViolations.incrementAndGet();

                    // Track affected subjects
                    String subject = entry.focusNode().toString(); // Focus node is the violating subject
                    violationSubjects.computeIfAbsent(resultMessage, k -> new HashSet<>()).add(subject);
                });

                // Display violation summary
                Platform.runLater(() -> validateTextArea.appendText("Violation Summary:\n"));
                violationCounts.forEach((constraint, count) -> {
                    Platform.runLater(() -> validateTextArea.appendText("Constraint: " + constraint + "\n"));
                    Platform.runLater(() -> validateTextArea.appendText("Violations: " + count + "\n"));
                });

                Platform.runLater(() -> validateTextArea.appendText("Total Violations: " + totalViolations.get() + "\n"));

                // Generate the PDF report, passing violationSubjects to it
                generateValidationReport(violationCounts, totalViolations.get(), violationSubjects);

                // Enable download button
                Platform.runLater(() -> downloadReportButton.setDisable(false));

            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "Validation failed: " + e.getMessage(), Alert.AlertType.ERROR));
            } finally {
                Platform.runLater(() -> validationLoadingIndicator.setVisible(false));
            }
        }).start();
    }
    private void generateValidationReport(Map<String, Integer> violationCounts, int totalViolations, Map<String, Set<String>> violationSubjects) {
        String outputPath = "output/validation_report.pdf";

        try {
            PdfWriter writer = new PdfWriter(new FileOutputStream(outputPath));
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Title
            document.add(new Paragraph("Validation Report").setBold().setFontSize(16));

            // Date & Time
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            document.add(new Paragraph("Date: " + dateTime));

            // Findings (Total Violations)
            document.add(new Paragraph("Findings: " + totalViolations));

            // Result (Failed or Success)
            String result = totalViolations > 0 ? "Failed" : "Success";
            document.add(new Paragraph("Result: " + result).setBold());

            // Details Section
            document.add(new Paragraph("\nDetails:\n").setBold().setFontSize(14));

            if (totalViolations > 0) {
                com.itextpdf.layout.element.List violationList = new com.itextpdf.layout.element.List();

                for (Map.Entry<String, Integer> entry : violationCounts.entrySet()) {
                    String[] parts = entry.getKey().split(" - ", 2);
                    String message = parts.length > 1 ? parts[1] : "No message available";

                    // Extract the subjects related to the violation message
                    Set<String> subjects = violationSubjects.get(message);
                    String subjectsString = subjects != null ? String.join(", ", subjects) : "Unknown Subjects";

                    // Create ListItem
                    ListItem violationItem = new ListItem();
                    violationItem.add(new Paragraph("Message: " + message));
                    //violationItem.add(new Paragraph("Predicate: " + "ex:located"));  // Update dynamically based on violation type
                    violationItem.add(new Paragraph("Subjects: " + subjectsString));
                    violationItem.add(new Paragraph("Occurrences: " + entry.getValue() + "\n"));

                    violationList.add(violationItem);
                }
                document.add(violationList);
            } else {
                document.add(new Paragraph("No violations found. The data is valid."));
            }

            document.close();
            Platform.runLater(() -> validateTextArea.appendText("Validation report saved as: " + outputPath + "\n"));

        } catch (Exception e) {
            Platform.runLater(() -> validateTextArea.appendText("Error generating report: " + e.getMessage() + "\n"));
        }
    }

    private String extractPropertyPath(String constraintUri, Model sh) {
        // Load the SHACL shape model (assuming the shapes file is already loaded as `shapesModel`)
        Model shapesModel = sh;  // You would have loaded the model from your SHACL file

        // Find the shape by URI (constraintUri is the URI of the shape)
        Resource shapeResource = shapesModel.getResource(constraintUri);

        // Extract the property path (sh:path) if it's a property constraint
        Property pathProperty = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#path");
        if (shapeResource.hasProperty(pathProperty)) {
            RDFNode pathNode = shapeResource.getProperty(pathProperty).getObject();
            return pathNode.toString();  // Convert the path to string (could be a URI or blank node)
        }

        return "Unknown Property Path";
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
                Platform.runLater(() -> validateTextArea.appendText("Starting RDF processing...\n"));

                // Process the data file
                File processedDataFile = processDataFile(dataFilePath);
                String DATA = processedDataFile.getAbsolutePath();
                String SHAPES = shapesFilePath;

                // Load Shapes Graph
                Graph shapesGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(shapesGraph, SHAPES);
                Platform.runLater(() -> validateTextArea.appendText("Shapes file loaded: " + SHAPES + "\n"));

                // Load Data Graph
                Graph dataGraph = GraphFactory.createDefaultGraph();
                RDFDataMgr.read(dataGraph, DATA);

                // Parse Shapes and validate
                Shapes shapes = Shapes.parse(shapesGraph);
                Platform.runLater(() -> validateTextArea.appendText("Shapes graph loaded.\n"));

                // Perform SHACL validation
                ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), dataGraph);
                Platform.runLater(() -> validateTextArea.appendText("Validation report generated.\n"));

                // Prepare violation data
                Map<String, Integer> violationCounts = new HashMap<>();
                AtomicInteger totalViolations = new AtomicInteger(0);
                Map<String, Set<String>> violationSubjects = new HashMap<>();
                Set<String> reportedPairs = new HashSet<>(); // Prevent duplicate counting for same pairs

                // Process the report entries
                Model reportModel = report.getModel();
                report.getEntries().forEach(entry -> {
                    Node sourceConstraintNode = entry.sourceConstraintComponent();
                    RDFNode sourceConstraint = reportModel.asRDFNode(sourceConstraintNode);
                    String constraintName = sourceConstraint != null ? sourceConstraint.toString() : "Unknown Constraint";
                    String resultMessage = entry.message();
                    String uniqueConstraintKey = constraintName + " - " + resultMessage;

                    // Extract subjects involved in violation
                    String subject1 = entry.focusNode().toString();
                    String subject2 = entry.value() != null ? entry.value().toString() : null;


                        // Handle other types of violations (non-sameAs)
                        violationCounts.put(uniqueConstraintKey, violationCounts.getOrDefault(uniqueConstraintKey, 0) + 1);
                        totalViolations.incrementAndGet();
                        violationSubjects.computeIfAbsent(resultMessage, k -> new HashSet<>()).add(subject1);

                });

                // Display violation summary
                if (totalViolations.get() > 0) {
                    Platform.runLater(() -> validateTextArea.appendText("Violation Counts by Individual Constraint:\n"));
                    violationCounts.forEach((constraint, count) -> {
                        Platform.runLater(() -> validateTextArea.appendText("Constraint: " + constraint + "\n"));
                        Platform.runLater(() -> validateTextArea.appendText("Violations: " + count + "\n"));
                    });
                }

                // Display detailed violations for sameAs
                violationSubjects.forEach((message, subjects) -> {
                    Set<String> sortedSubjects = new TreeSet<>(subjects); // Sort subjects to ensure consistency
                    String subjectsList = String.join(", ", sortedSubjects);

                    // Display the grouped subjects and occurrences
                    Platform.runLater(() -> validateTextArea.appendText("Message: " + message + "\n"));
                    Platform.runLater(() -> validateTextArea.appendText("Subjects: " + subjectsList + "\n"));
                    Platform.runLater(() -> validateTextArea.appendText("Occurrences: " + sortedSubjects.size() + "\n"));
                });

                // Display total number of violations
                Platform.runLater(() -> validateTextArea.appendText("Total Number of Violations: " + totalViolations.get() + "\n"));

                // Call the generateValidationReport method to create the PDF report
                generateValidationReport(violationCounts, totalViolations.get(), violationSubjects);

                // Enable the download button once the report is generated
                Platform.runLater(() -> downloadReportButton.setDisable(false));

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
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("RDF Files", "*.nt", "*.nq", "*.ttl", "*.rdf", "*.xml", "*.rdfxml"));

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            String selectedFilePath = selectedFile.getAbsolutePath();
            String convertedFilePath = "converted.nt";

            if (!FilenameUtils.getExtension(selectedFilePath).equals("nt")) {
                convertRDF(selectedFilePath, convertedFilePath);
                selectedFilePath = convertedFilePath; // Use the converted file
            }

            validationLoadingIndicator.setVisible(true);

            String finalSelectedFilePath = selectedFilePath;
            new Thread(() -> {
                try {
                    String content = Files.readString(Paths.get(finalSelectedFilePath));

                    Platform.runLater(() -> {
                        dataFileTextArea.replaceText(content);
                        dataFilePath = finalSelectedFilePath;
                        validationLoadingIndicator.setVisible(false);
                        if (dataFilePath != null && shapesFilePath != null) {
                            if (equivVboxValidate.isVisible()) {
                                validateButton.setDisable(mappingTextAreaValidate.getText().isEmpty());
                            } else validateButton.setDisable(false);

                        } else validateButton.setDisable(true);

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
                Files.copy(new File("output/validation_report.pdf").toPath(),
                        selectedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
