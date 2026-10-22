package org.example.myfirstpro;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.myfirstpro.InterfacesUtils.showAlert;

public class Tab2Controller {
    private List<List<String>> almostKeys;
    @FXML
    private StyleClassedTextArea keysTextArea;
    @FXML
    private Button generateShapesButton;
    @FXML
    private Button saveShapesButton;
    @FXML
    private StyleClassedTextArea shapesTextArea;
    @FXML
    private StyleClassedTextArea mappingTextArea;
    @FXML
    private Button chooseEquivFileBtn;
    @FXML
    private VBox equivVbox;
    Logger logger = LoggerFactory.getLogger(Tab2Controller.class);

    private void updateToggle() {
        boolean showEquivComponents = InterfacesUtils.equiv.get();
        equivVbox.setVisible(showEquivComponents);
        equivVbox.setManaged(showEquivComponents);
    }

    @FXML
    private void initialize() {
        updateToggle();
        InterfacesUtils.equiv.addListener((observable, oldValue, newValue) -> {
            updateToggle();
            shapesTextArea.clear();
            saveShapesButton.setDisable(true);
            chooseEquivFileBtn.setDisable(true);
            mappingTextArea.clear();
            keysTextArea.clear();
            generateShapesButton.setDisable(true);
        });

    }

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

    private List<List<String>> parseKeysFile(String keysContent, Map<String, String> equivalentsMap) {
        List<List<String>> almostKeys = new ArrayList<>();
        for (String line : keysContent.split("\\n")) {
            if (line.contains("almost keys")) {
                String keysSection = line.split(":")[1].trim();
                String[] keysGroups = keysSection.substring(1, keysSection.length() - 1).split("], \\[");

                for (String group : keysGroups) {
                    List<String> keyList = new ArrayList<>();
                    for (String key : group.replace("[", "").replace("]", "").split(",")) {
                        String trimmedKey = key.trim();
                        keyList.add(equivalentsMap.getOrDefault(trimmedKey, trimmedKey));
                    }
                    almostKeys.add(keyList);
                }
            }
        }
        return almostKeys;
    }

    private String buildSparqlConditions(List<String> key) {
        StringBuilder conditions = new StringBuilder();

        for (String property : key) {
            conditions.append(String.format("    ?this ex:%s ?%s .\n", property, property));
            conditions.append(String.format("    ?other ex:%s ?%s .\n", property, property));
        }

        return conditions.toString();
    }

    @FXML
    private void chooseKeysFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Keys File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            try {
                List<String> lines = Files.readAllLines(selectedFile.toPath());
                almostKeys = parseAlmostKeysFromOutput(lines.toString());
                String keysContent = String.join("\n", lines);
                javafx.application.Platform.runLater(() -> {
                    keysTextArea.replaceText(keysContent);
                    shapesTextArea.clear();
                    if (!equivVbox.isVisible())
                        generateShapesButton.setDisable(almostKeys.isEmpty());
                    saveShapesButton.setDisable(true);
                    chooseEquivFileBtn.setDisable(almostKeys.isEmpty());
                });

            } catch (IOException e) {
                showAlert("Error", "Failed to read keys file: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    private void chooseMappingFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Mapping File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(selectedFile))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                mappingTextArea.replaceText(content.toString());
                try {
                    Map<String, String> equivalentsMap = parseMappingFile(mappingTextArea.getText());
                    almostKeys = parseKeysFile(keysTextArea.getText(), equivalentsMap);
                    shapesTextArea.clear();
                    generateShapesButton.setDisable(almostKeys.isEmpty());
                    saveShapesButton.setDisable(true);
                } catch (Exception ex) {
                    showAlert("Error", "Failed to generate shapes: " + ex.getMessage(), Alert.AlertType.ERROR);
                }

            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    @FXML
    private void generateShapes() {
        shapesTextArea.clear();
        saveShapesButton.setDisable(true);

        if (almostKeys == null || almostKeys.isEmpty()) {
            showAlert("Error", "No almost keys available to generate shapes.", Alert.AlertType.ERROR);
            return;
        }

        Model model = ModelFactory.createDefaultModel();
        String sh = "http://www.w3.org/ns/shacl#";
        String ex = "http://example.com/ns#";

        model.setNsPrefix("sh", sh);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("ex", ex);

        Property rdfType = RDF.type;
        Property shPath = model.createProperty(sh + "path");
        Property shProperty = model.createProperty(sh + "property");
        Property shDatatype = model.createProperty(sh + "datatype");
        Property shMaxCount = model.createProperty(sh + "maxCount");
        Property shMinCount = model.createProperty(sh + "minCount");
        Property shSelect = model.createProperty(sh + "select");
        Property shMessage = model.createProperty(sh + "message");
        Property shSeverity = model.createProperty(sh + "severity");
        Property shSparql = model.createProperty(sh + "sparql");
        Property shRule = model.createProperty(sh + "rule");
        Property shTarget = model.createProperty(sh + "target");

        Resource shViolation = model.createResource(sh + "Violation");
        Resource shSPARQLConstraint = model.createResource(sh + "SPARQLConstraint");
        Resource shSPARQLTarget = model.createResource(sh + "SPARQLTarget");
        Resource shTripleRule = model.createResource(sh + "TripleRule");

        int shapeIndex = 1;
        for (List<String> key : almostKeys) {
            Resource shape = model.createResource(ex + "Shape" + shapeIndex++);
            shape.addProperty(rdfType, model.createResource(sh + "NodeShape"));

            // Add properties for each key
            for (String property : key) {
                String localProperty = extractLocalName(property);
                Resource propertyShape = model.createResource();
                propertyShape.addProperty(shPath, model.createProperty(ex + localProperty));
                propertyShape.addProperty(shDatatype, XSD.xstring);
                propertyShape.addProperty(shMaxCount, model.createTypedLiteral(1));
                propertyShape.addProperty(shMinCount, model.createTypedLiteral(1));
                shape.addProperty(shProperty, propertyShape);
            }

            // Construct SPARQL queries with specific variable names for each property
            String sparqlConditions = buildSparqlConditionsLocalNames(key, "ex:");

            String constructQuery = String.format(
                    "PREFIX ex: <http://example.com/ns#>\nPREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                            "CONSTRUCT {\n    ?this owl:sameAs ?other .\n}\nWHERE {\n    %s\n    FILTER (?this != ?other)\n}\n", sparqlConditions);

            String selectQuery = String.format(
                    "PREFIX ex: <http://example.com/ns#>\nPREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                            "SELECT ?this ?other\nWHERE {\n    %s\n    FILTER (?this != ?other)\n    FILTER NOT EXISTS { ?this owl:sameAs ?other }\n}\n", sparqlConditions);

            String duplicateQuery = String.format(
                    "PREFIX ex: <http://example.com/ns#>\n" +
                            "SELECT ?this\nWHERE {\n    %s\n    FILTER (?this != ?other)\n}\n", sparqlConditions);

            // Add rules
            Resource ruleConstraint = model.createResource();
            ruleConstraint.addProperty(rdfType, shTripleRule);
            ruleConstraint.addProperty(shSelect, constructQuery);
            shape.addProperty(shRule, ruleConstraint);

            // Add first SPARQL constraint (sameAs check)
            Resource sparqlConstraint1 = model.createResource();
            sparqlConstraint1.addProperty(rdfType, shSPARQLConstraint);
            sparqlConstraint1.addProperty(shMessage, "Duplicate with the same properties must be marked as owl:sameAs.");
            sparqlConstraint1.addProperty(shSelect, selectQuery);
            shape.addProperty(shSparql, sparqlConstraint1);

            // Add second SPARQL constraint (duplicate check)
            Resource sparqlConstraint2 = model.createResource();
            sparqlConstraint2.addProperty(rdfType, shSPARQLConstraint);
            sparqlConstraint2.addProperty(shMessage, "Duplicate detected for properties: " +
                    key.stream().map(this::extractLocalName).collect(Collectors.joining(", ")));
            sparqlConstraint2.addProperty(shSelect, duplicateQuery);
            shape.addProperty(shSparql, sparqlConstraint2);

            // Add target SPARQL query
            Resource targetConstraint = model.createResource();
            targetConstraint.addProperty(rdfType, shSPARQLTarget);
            targetConstraint.addProperty(shSelect, duplicateQuery);
            shape.addProperty(shTarget, targetConstraint);

            shape.addProperty(shSeverity, shViolation);
        }

        StringWriter writer = new StringWriter();
        model.write(writer, "TURTLE");
        String generatedShapes = writer.toString();
        javafx.application.Platform.runLater(() -> {
            shapesTextArea.replaceText(generatedShapes);
            saveShapesButton.setDisable(false);
        });
    }


    private String buildSparqlConditionsLocalNames(List<String> properties, String prefix) {
        StringBuilder conditions = new StringBuilder();
        for (String property : properties) {
            String localProperty = extractLocalName(property);
            String variableName = "?" + localProperty;  // Create a variable like ?haslatitude, ?hasheight, etc.
            conditions.append(String.format("?this %s%s %s .\n    ?other %s%s %s .", prefix, localProperty, variableName, prefix, localProperty, variableName));
        }
        return conditions.toString();
    }


    /**
     * Extracts the local name from a URI or returns it unchanged if it's already a local name.
     */
    private String extractLocalName(String uri) {
        if (uri.contains("#")) {
            return uri.substring(uri.lastIndexOf("#") + 1);  // Extracts after #
        } else if (uri.contains("/")) {
            return uri.substring(uri.lastIndexOf("/") + 1);  // Extracts after last /
        }
        return uri;  // Return as is if it's already a local name
    }

    /**
     * Builds SPARQL conditions using only local names instead of full URIs.
     */

    @FXML
    private void saveGeneratedShapesToFile() {
        String shapesContent = shapesTextArea.getText();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save SHACL Shapes");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Turtle Files", "*.ttl"));

        File selectedFile = fileChooser.showSaveDialog(null);

        if (selectedFile != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(selectedFile))) {
                writer.write(shapesContent);
                showAlert("Success", "SHACL Shapes saved successfully!", Alert.AlertType.CONFIRMATION);
            } catch (IOException e) {
                showAlert("Error", "Error saving SHACL Shapes: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

}
