package org.example.myfirstpro;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {

    @FXML
    private ToggleButton withoutEquivBtn;
    @FXML
    private ToggleButton withEquivBtn;
    Logger logger = LoggerFactory.getLogger(MainController.class);

    private void equivVisibility() {
        Platform.runLater(() -> {
            boolean showEquivComponents = withEquivBtn.isSelected();
            InterfacesUtils.equiv.set(showEquivComponents);
        });
    }

    @FXML
    public void initialize() {
        try {
            ToggleGroup toggleGroup = new ToggleGroup();
            withoutEquivBtn.setToggleGroup(toggleGroup);
            withEquivBtn.setToggleGroup(toggleGroup);
            withoutEquivBtn.setSelected(true);
            equivVisibility();
            toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> equivVisibility());
        } catch (Exception e) {
            logger.error("Error initializing application: ", e);
        }
    }
}
