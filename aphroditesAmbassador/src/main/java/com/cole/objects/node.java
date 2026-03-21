package com.cole.objects;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public class node {
    private final VBox pane;
    private final Label name;
    private final TextField inputField;
    private final VBox outputContainer;
    private final List<OutputRow> outputRows;
    private final Circle inputPort;
    private OutputPortListener outputPortListener;

    private double dragOffsetX;
    private double dragOffsetY;

    public node(String name1, String defaultInput, List<OutputLink> defaultOutputs) {
        pane = new VBox();
        pane.getStylesheets().add(
            node.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        pane.getStyleClass().add("node-pane");
        pane.setPrefWidth(360);
        pane.setFillWidth(true);

        name = new Label(name1);
        name.getStyleClass().add("node-title");

        var header = new HBox(name);
        header.getStyleClass().add("node-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 18, 14, 18));
        makeDraggable(header);

        inputField = new TextField(defaultInput);
        inputField.setPromptText("Input");

        var inputLabel = new Label("Input");
        inputLabel.getStyleClass().add("section-label");

        inputPort = new Circle(6);
        inputPort.getStyleClass().addAll("port", "input-port");

        var inputRow = new HBox(12, inputPort, inputField);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        outputContainer = new VBox(10);
        outputRows = new ArrayList<>();

        var outputHeader = new HBox();
        outputHeader.setAlignment(Pos.CENTER_LEFT);
        outputHeader.setSpacing(10);

        var outputLabel = new Label("Outputs");
        outputLabel.getStyleClass().add("section-label");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var addOutputButton = new Button("+ Output");
        addOutputButton.setOnAction(event -> addOutput("", ""));

        outputHeader.getChildren().addAll(outputLabel, spacer, addOutputButton);

        var body = new VBox(12, inputLabel, inputRow, outputHeader, outputContainer);
        body.getStyleClass().add("node-body");
        body.setPadding(new Insets(16));

        pane.getChildren().addAll(header, body);

        if (defaultOutputs == null || defaultOutputs.isEmpty()) {
            addOutput("Output", "Next Node");
        } else {
            for (OutputLink output : defaultOutputs) {
                addOutput(output.getLabel(), output.getLeadsTo());
            }
        }
    }

    public node(String name1, String defaultInput, String defaultOutput) {
        this(name1, defaultInput, List.of(new OutputLink(defaultOutput, "Next Node")));
    }

    public boolean isClickedLabel() {
        return name.isHover();
    }

    public Pane getPane() {
        return pane;
    }

    public Circle getInputPort() {
        return inputPort;
    }

    public String getInput() {
        return inputField.getText();
    }

    public List<OutputLink> getOutputs() {
        var outputs = new ArrayList<OutputLink>();
        for (OutputRow row : outputRows) {
            outputs.add(new OutputLink(row.outputNameField.getText(), row.leadsToField.getText()));
        }
        return outputs;
    }

    public List<Circle> getOutputPorts() {
        var ports = new ArrayList<Circle>();
        for (OutputRow row : outputRows) {
            ports.add(row.outputPort);
        }
        return ports;
    }

    public int getOutputPortIndex(Circle port) {
        for (int index = 0; index < outputRows.size(); index++) {
            if (outputRows.get(index).outputPort == port) {
                return index;
            }
        }
        return -1;
    }

    public void setOutputPortListener(OutputPortListener listener) {
        outputPortListener = listener;
        for (int index = 0; index < outputRows.size(); index++) {
            outputPortListener.onOutputPortCreated(this, outputRows.get(index).outputPort, index);
        }
    }

    public void addOutput(String outputName, String leadsTo) {
        var row = new OutputRow(outputName, leadsTo);
        outputRows.add(row);
        outputContainer.getChildren().add(row.container);
        if (outputPortListener != null) {
            outputPortListener.onOutputPortCreated(this, row.outputPort, outputRows.size() - 1);
        }
    }

    public boolean removeLastOutput() {
        if (outputRows.isEmpty()) {
            return false;
        }

        var removed = outputRows.remove(outputRows.size() - 1);
        outputContainer.getChildren().remove(removed.container);
        return true;
    }

    public int getOutputCount() {
        return outputRows.size();
    }

    public Point2D getInputPortSceneCenter() {
        return getCircleCenterInScene(inputPort);
    }

    public Point2D getOutputPortSceneCenter(int index) {
        return getCircleCenterInScene(outputRows.get(index).outputPort);
    }

    public Point2D sceneToLocal(Pane parent, Point2D scenePoint) {
        return parent.sceneToLocal(scenePoint);
    }

    private Point2D getCircleCenterInScene(Circle circle) {
        return circle.localToScene(circle.getCenterX(), circle.getCenterY());
    }

    private void makeDraggable(HBox dragHandle) {
        dragHandle.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX() - pane.getLayoutX();
            dragOffsetY = event.getSceneY() - pane.getLayoutY();
        });

        dragHandle.setOnMouseDragged(event -> {
            pane.setLayoutX(event.getSceneX() - dragOffsetX);
            pane.setLayoutY(event.getSceneY() - dragOffsetY);
        });
    }

    public static class OutputLink {
        private final String label;
        private final String leadsTo;

        public OutputLink(String label, String leadsTo) {
            this.label = label;
            this.leadsTo = leadsTo;
        }

        public String getLabel() {
            return label;
        }

        public String getLeadsTo() {
            return leadsTo;
        }
    }

    @FunctionalInterface
    public interface OutputPortListener {
        void onOutputPortCreated(node sourceNode, Circle port, int index);
    }

    private final class OutputRow {
        private final HBox container;
        private final TextField outputNameField;
        private final TextField leadsToField;
        private final Circle outputPort;

        private OutputRow(String outputName, String leadsTo) {
            outputNameField = new TextField(outputName);
            outputNameField.setPromptText("Output label");
            outputNameField.getStyleClass().add("output-name-field");
            outputNameField.setPrefWidth(110);

            leadsToField = new TextField(leadsTo);
            leadsToField.setPromptText("Leads to");
            HBox.setHgrow(leadsToField, Priority.ALWAYS);

            outputPort = new Circle(6);
            outputPort.getStyleClass().addAll("port", "output-port");

            var removeButton = new Button("Remove");
            removeButton.getStyleClass().add("secondary-button");

            var labels = new VBox(6, outputNameField, leadsToField);
            HBox.setHgrow(labels, Priority.ALWAYS);

            container = new HBox(10, labels, removeButton, outputPort);
            container.getStyleClass().add("output-row");
            container.setAlignment(Pos.CENTER_LEFT);

            removeButton.setOnAction(event -> {
                outputRows.remove(this);
                outputContainer.getChildren().remove(container);
            });
        }
    }
}
