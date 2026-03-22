package com.cole.objects;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public class node {
    private final VBox pane;
    private final TextField nameField;
    private final TextArea promptField;
    private final ChoiceBox<String> nodeTypeChoice;
    private final VBox outputContainer;
    private final List<OutputRow> outputRows;
    private final Circle inputPort;
    private final HBox inputRow;
    private final HBox outputHeader;
    private final Button addOutputButton;

    private OutputPortListener outputPortListener;
    private boolean structureLocked;

    private double dragOffsetX;
    private double dragOffsetY;

    public node(String name1, String prompt, List<OutputLink> defaultOutputs) {
        this(name1, prompt, defaultOutputs, NodeKind.DECISION, false);
    }

    public node(String name1, String prompt, String defaultOutput) {
        this(name1, prompt, List.of(new OutputLink(defaultOutput, "Next Node")));
    }

    public node(String name1, String prompt, List<OutputLink> defaultOutputs, NodeKind nodeKind, boolean locked) {
        pane = new VBox();
        pane.getStylesheets().add(
            node.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        pane.getStyleClass().add("node-pane");
        pane.setPrefWidth(380);
        pane.setFillWidth(true);

        nameField = new TextField(name1);
        nameField.getStyleClass().addAll("node-title", "node-title-field");

        var titleBlock = new VBox(6);
        var titleLabel = new Label("Node Name");
        titleLabel.getStyleClass().add("section-label");

        nodeTypeChoice = new ChoiceBox<>();
        nodeTypeChoice.getItems().addAll(
            NodeKind.START.getDisplayName(),
            NodeKind.DECISION.getDisplayName(),
            NodeKind.END.getDisplayName(),
            NodeKind.END_CONVERSATION.getDisplayName()
        );

        var typeBlock = new VBox(6);
        var typeLabel = new Label("Node Type");
        typeLabel.getStyleClass().add("section-label");
        typeBlock.getChildren().addAll(typeLabel, nodeTypeChoice);

        var headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        var headerTop = new HBox(12, new VBox(6, titleLabel, nameField), headerSpacer, typeBlock);
        headerTop.setAlignment(Pos.TOP_LEFT);
        headerTop.getStyleClass().add("node-header-content");

        var header = new VBox(headerTop);
        header.getStyleClass().add("node-header");
        header.setPadding(new Insets(14, 18, 14, 18));
        makeDraggable(header);

        promptField = new TextArea(prompt);
        promptField.setPromptText("Describe what the LLM should check or say at this step.");
        promptField.setWrapText(true);
        promptField.setPrefRowCount(4);

        var promptLabel = new Label("AI Prompt / Rule");
        promptLabel.getStyleClass().add("section-label");

        inputPort = new Circle(6);
        inputPort.getStyleClass().addAll("port", "input-port");
        inputRow = new HBox(12, inputPort, buildHintLabel("Incoming Flow"));
        inputRow.setAlignment(Pos.CENTER_LEFT);

        outputContainer = new VBox(10);
        outputRows = new ArrayList<>();

        outputHeader = new HBox();
        outputHeader.setAlignment(Pos.CENTER_LEFT);
        outputHeader.setSpacing(10);

        var outputLabel = new Label("Outputs");
        outputLabel.getStyleClass().add("section-label");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        addOutputButton = new Button("+ Output");
        addOutputButton.setOnAction(event -> addOutput("", ""));

        outputHeader.getChildren().addAll(outputLabel, spacer, addOutputButton);

        var body = new VBox(12, promptLabel, promptField, inputRow, outputHeader, outputContainer);
        body.getStyleClass().add("node-body");
        body.setPadding(new Insets(16));

        pane.getChildren().addAll(header, body);

        if (defaultOutputs != null) {
            for (OutputLink output : defaultOutputs) {
                addOutput(output.getLabel(), output.getLeadsTo());
            }
        }

        nodeTypeChoice.setValue(nodeKind.getDisplayName());
        nodeTypeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                applyNodeKind(NodeKind.fromDisplayName(newValue));
            }
        });

        structureLocked = locked;
        applyNodeKind(nodeKind);
        applyStructureLock(locked);
    }

    public static node createStartNode(String prompt) {
        return new node(
            "Start",
            prompt,
            List.of(new OutputLink("Begin", "")),
            NodeKind.START,
            true
        );
    }

    public static node createDecisionNode(String name, String prompt) {
        return new node(
            name,
            prompt,
            List.of(
                new OutputLink("True", ""),
                new OutputLink("False", "")
            ),
            NodeKind.DECISION,
            false
        );
    }

    public static node createEndNode(String prompt) {
        return new node(
            "End",
            prompt,
            new ArrayList<OutputLink>(),
            NodeKind.END,
            true
        );
    }

    public static node createEndConversationNode(String prompt) {
        return new node(
            "End Conversation",
            prompt,
            new ArrayList<OutputLink>(),
            NodeKind.END_CONVERSATION,
            true
        );
    }

    public static node createFromState(String name, String prompt, List<OutputLink> outputs, NodeKind kind) {
        boolean locked = kind == NodeKind.START || kind == NodeKind.END || kind == NodeKind.END_CONVERSATION;
        return new node(name, prompt, outputs, kind, locked);
    }

    public Pane getPane() {
        return pane;
    }

    public Circle getInputPort() {
        return inputPort;
    }

    public boolean hasInputPort() {
        return inputRow.isVisible();
    }

    public boolean hasOutputs() {
        return !outputRows.isEmpty();
    }

    public String getNodeName() {
        return nameField.getText();
    }

    public String getPrompt() {
        return promptField.getText();
    }

    public NodeKind getNodeKind() {
        return NodeKind.fromDisplayName(nodeTypeChoice.getValue());
    }

    public List<OutputLink> getOutputs() {
        var outputs = new ArrayList<OutputLink>();
        for (OutputRow row : outputRows) {
            outputs.add(new OutputLink(row.outputNameField.getText(), row.leadsToField.getText()));
        }
        return outputs;
    }

    public void setOutputTarget(int index, String targetName) {
        if (index >= 0 && index < outputRows.size()) {
            outputRows.get(index).leadsToField.setText(targetName);
        }
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

    public void clearOutputs() {
        outputRows.clear();
        outputContainer.getChildren().clear();
    }

    public Point2D getInputPortSceneCenter() {
        return getCircleCenterInScene(inputPort);
    }

    public Point2D getOutputPortSceneCenter(int index) {
        return getCircleCenterInScene(outputRows.get(index).outputPort);
    }

    private Label buildHintLabel(String text) {
        var label = new Label(text);
        label.getStyleClass().add("hint-label");
        return label;
    }

    private Point2D getCircleCenterInScene(Circle circle) {
        return circle.localToScene(circle.getCenterX(), circle.getCenterY());
    }

    private void makeDraggable(VBox dragHandle) {
        dragHandle.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX() - pane.getLayoutX();
            dragOffsetY = event.getSceneY() - pane.getLayoutY();
        });

        dragHandle.setOnMouseDragged(event -> {
            pane.setLayoutX(event.getSceneX() - dragOffsetX);
            pane.setLayoutY(event.getSceneY() - dragOffsetY);
        });
    }

    private void applyNodeKind(NodeKind kind) {
        pane.getStyleClass().removeAll("start-node", "decision-node", "end-node", "end-conversation-node");
        pane.getStyleClass().add(kind.getStyleClass());

        switch (kind) {
            case START:
                inputRow.setManaged(false);
                inputRow.setVisible(false);
                ensureExactOutputs(List.of(new OutputLink("Begin", "")));
                promptField.setPromptText("Write the opening message or first AI instruction.");
                break;
            case DECISION:
                inputRow.setManaged(true);
                inputRow.setVisible(true);
                if (outputRows.isEmpty()) {
                    ensureExactOutputs(List.of(
                        new OutputLink("True", ""),
                        new OutputLink("False", "")
                    ));
                }
                promptField.setPromptText("Describe the condition the LLM should evaluate as true or false.");
                break;
            case END:
                inputRow.setManaged(true);
                inputRow.setVisible(true);
                ensureExactOutputs(new ArrayList<OutputLink>());
                promptField.setPromptText("Write the ending response or action.");
                break;
            case END_CONVERSATION:
                inputRow.setManaged(true);
                inputRow.setVisible(true);
                ensureExactOutputs(new ArrayList<OutputLink>());
                promptField.setPromptText("Write the short closing line that ends the conversation.");
                break;
            default:
                break;
        }
    }

    private void applyStructureLock(boolean locked) {
        structureLocked = locked;
        nameField.setEditable(!locked);
        nodeTypeChoice.setDisable(locked);
        addOutputButton.setDisable(locked);
        outputHeader.setManaged(!getNodeKind().equals(NodeKind.END));
        outputHeader.setVisible(!getNodeKind().equals(NodeKind.END));
    }

    private void ensureExactOutputs(List<OutputLink> outputs) {
        clearOutputs();
        for (OutputLink output : outputs) {
            addOutput(output.getLabel(), output.getLeadsTo());
        }

        var showOutputTools = !outputs.isEmpty();
        outputHeader.setManaged(showOutputTools);
        outputHeader.setVisible(showOutputTools);
    }

    public enum NodeKind {
        START("Start", "start-node"),
        DECISION("Decision", "decision-node"),
        END("End", "end-node"),
        END_CONVERSATION("End Conversation", "end-conversation-node");

        private final String displayName;
        private final String styleClass;

        NodeKind(String displayName, String styleClass) {
            this.displayName = displayName;
            this.styleClass = styleClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getStyleClass() {
            return styleClass;
        }

        public static NodeKind fromDisplayName(String value) {
            for (NodeKind kind : values()) {
                if (kind.displayName.equals(value)) {
                    return kind;
                }
            }
            return DECISION;
        }
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
            outputNameField.setPromptText("Branch");
            outputNameField.getStyleClass().add("output-name-field");
            outputNameField.setPrefWidth(110);

            leadsToField = new TextField(leadsTo);
            leadsToField.setPromptText("Leads to");
            HBox.setHgrow(leadsToField, Priority.ALWAYS);

            outputPort = new Circle(6);
            outputPort.getStyleClass().addAll("port", "output-port");

            var removeButton = new Button("Remove");
            removeButton.getStyleClass().add("secondary-button");
            removeButton.setDisable(structureLocked);

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
