package org.example.myfirstpro;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class InterfacesUtils {
    public static BooleanProperty equiv = new SimpleBooleanProperty(false);

    public static void showAlert(String title, String message, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void convertRDF(String inputFile, String outputFile) {
        Lang inputLang = getLangFromFile(inputFile);
        Lang outputLang = getLangFromFile(outputFile);

        if (inputLang == null || outputLang == null) {
            System.err.println("Unsupported file format.");
            return;
        }

        // Load RDF data
        Model model = ModelFactory.createDefaultModel();
        try (FileInputStream in = new FileInputStream(inputFile)) {
            RDFDataMgr.read(model, in, inputLang);
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
            return;
        }

        // Write RDF data in the desired format
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            RDFDataMgr.write(out, model, outputLang);
            System.out.println("Conversion successful: " + inputFile + " → " + outputFile);
        } catch (IOException e) {
            System.err.println("Error writing output file: " + e.getMessage());
        }
    }

    private static Lang getLangFromFile(String filename) {
        if (filename.endsWith(".nt")) return RDFLanguages.NT;
        if (filename.endsWith(".nq")) return RDFLanguages.NQ;
        if (filename.endsWith(".ttl")) return RDFLanguages.TTL;
        if (filename.endsWith(".rdf") || filename.endsWith(".xml") || filename.endsWith(".rdfxml"))
            return RDFLanguages.RDFXML;
        return null;
    }
}