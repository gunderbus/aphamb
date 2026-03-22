package com.cole;

import com.cole.ai.FlowRunner.FlowConnection;
import com.cole.ai.FlowRunner;
import com.cole.ai.OllamaClient;
import com.cole.objects.node.NodeKind;
import com.cole.objects.node.OutputLink;
import com.cole.objects.node;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.CacheHint;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * JavaFX App
 */
public class App extends Application {
    private final List<node> nodes = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();
    private final DragState dragState = new DragState();
    private final FlowRunner flowRunner = new FlowRunner(new OllamaClient());
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private int createdDecisionNodes = 2;
    private Stage primaryStage;
    private File currentFlowchartFile;

    private Pane canvas;
    private Pane world;
    private Pane lineLayer;
    private Pane nodeLayer;
    private Canvas gridCanvas;
    private CubicCurve previewLine;
    private Connection selectedConnection;
    private Button deleteLineButton;
    private Button runFlowButton;
    private TextArea transcriptArea;
    private TextArea traversalArea;
    private TextArea styleContextArea;
    private Label styleContextStatusLabel;
    private TextField userMessageField;
    private TextField modelField;
    private TextField baseUrlField;
    private double viewportScale = 1.0;
    private double viewportTranslateX = 0;
    private double viewportTranslateY = 0;
    private double panStartTranslateX = 0;
    private double panStartTranslateY = 0;
    private double panStartSceneX = 0;
    private double panStartSceneY = 0;
    private boolean panning;

    private static final double MIN_ZOOM = 0.45;
    private static final double MAX_ZOOM = 2.25;
    private static final double ZOOM_STEP = 1.12;
    private static final double GRID_MINOR_SPACING = 40;
    private static final double GRID_MAJOR_EVERY = 5;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        var root = new BorderPane();
        root.getStyleClass().add("editor-root");

        var toolbar = buildToolbar();
        root.setTop(toolbar);

        canvas = new Pane();
        canvas.getStyleClass().add("editor-canvas");

        gridCanvas = new Canvas();
        gridCanvas.setMouseTransparent(true);
        gridCanvas.widthProperty().bind(canvas.widthProperty());
        gridCanvas.heightProperty().bind(canvas.heightProperty());

        world = new Pane();
        world.setPickOnBounds(false);
        world.setCache(true);
        world.setCacheHint(CacheHint.SPEED);

        lineLayer = new Pane();
        lineLayer.setPickOnBounds(false);

        nodeLayer = new Pane();
        nodeLayer.setPickOnBounds(false);

        world.getChildren().addAll(lineLayer, nodeLayer);
        canvas.getChildren().addAll(gridCanvas, world);
        root.setCenter(canvas);
        root.setRight(buildRunnerPanel());

        previewLine = createConnectionLine();
        previewLine.setVisible(false);
        previewLine.setMouseTransparent(true);
        lineLayer.getChildren().add(previewLine);

        seedInitialGraph();
        installViewportListeners();
        installCanvasHandlers();
        installConnectionUpdater();
        centerWorkspace();
        renderGrid();

        var scene = new Scene(root, 1560, 860);
        scene.getStylesheets().add(
            App.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                deleteSelectedConnection();
            }
        });

        stage.setScene(scene);
        stage.setTitle("Aphrodite's Ambassador");
        stage.show();
        Platform.runLater(this::centerWorkspace);
    }

    private HBox buildToolbar() {
        var title = new Label("AI Flowchart Builder");
        title.getStyleClass().add("toolbar-title");

        var subtitle = new Label(
            "Build the graph, connect branches, click a line to delete it, and run the conversation through local Ollama."
        );
        subtitle.getStyleClass().add("toolbar-subtitle");

        var titleBlock = new VBox(4, title, subtitle);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var addNodeButton = new Button("Add Decision Node");
        addNodeButton.getStyleClass().add("add-node-button");
        addNodeButton.setOnAction(event -> addDecisionNode());

        var addEndNodeButton = new Button("Add End Conversation");
        addEndNodeButton.getStyleClass().add("secondary-toolbar-button");
        addEndNodeButton.setOnAction(event -> addEndConversationNode());

        var saveAsButton = new Button("Save As");
        saveAsButton.getStyleClass().add("secondary-toolbar-button");
        saveAsButton.setOnAction(event -> saveFlowchartAs());

        var saveButton = new Button("Save");
        saveButton.getStyleClass().add("secondary-toolbar-button");
        saveButton.setOnAction(event -> saveFlowchart());

        var loadButton = new Button("Load");
        loadButton.getStyleClass().add("secondary-toolbar-button");
        loadButton.setOnAction(event -> loadFlowchart());

        var uploadStyleButton = new Button("Upload Style Context");
        uploadStyleButton.getStyleClass().add("secondary-toolbar-button");
        uploadStyleButton.setOnAction(event -> uploadStyleContextFile());

        styleContextStatusLabel = new Label("No style file loaded");
        styleContextStatusLabel.getStyleClass().add("toolbar-status-label");

        deleteLineButton = new Button("Delete Selected Line");
        deleteLineButton.getStyleClass().add("secondary-toolbar-button");
        deleteLineButton.setDisable(true);
        deleteLineButton.setOnAction(event -> deleteSelectedConnection());

        var toolbar = new HBox(
            18,
            titleBlock,
            spacer,
            saveAsButton,
            saveButton,
            loadButton,
            uploadStyleButton,
            styleContextStatusLabel,
            deleteLineButton,
            addEndNodeButton,
            addNodeButton
        );
        toolbar.getStyleClass().add("editor-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(18, 22, 18, 22));
        return toolbar;
    }

    private VBox buildRunnerPanel() {
        var panelTitle = new Label("Flow Runner");
        panelTitle.getStyleClass().add("panel-title");

        var panelSubtitle = new Label(
            "Runs the current flowchart against a local Ollama server. Make sure `ollama serve` is running and `llama3` is installed."
        );
        panelSubtitle.getStyleClass().add("panel-subtitle");
        panelSubtitle.setWrapText(true);

        baseUrlField = new TextField("http://localhost:11434");
        baseUrlField.setPromptText("Ollama base URL");

        modelField = new TextField("llama3");
        modelField.setPromptText("Model");

        userMessageField = new TextField();
        userMessageField.setPromptText("Enter the user's latest message");

        runFlowButton = new Button("Run Flow");
        runFlowButton.getStyleClass().add("add-node-button");
        runFlowButton.setMaxWidth(Double.MAX_VALUE);
        runFlowButton.setOnAction(event -> runFlow());

        transcriptArea = new TextArea();
        transcriptArea.setPromptText("Transcript will appear here.");
        transcriptArea.setWrapText(true);
        transcriptArea.setPrefRowCount(14);

        styleContextArea = new TextArea();
        styleContextArea.setPromptText("Paste style guidance here or upload a .txt / .md file to imitate its voice.");
        styleContextArea.setWrapText(true);
        styleContextArea.setPrefRowCount(8);
        styleContextArea.textProperty().addListener((obs, oldValue, newValue) -> updateStyleContextStatus(null));

        traversalArea = new TextArea();
        traversalArea.setPromptText("Chosen node path will appear here.");
        traversalArea.setWrapText(true);
        traversalArea.setEditable(false);
        traversalArea.setPrefRowCount(10);

        var runner = new VBox(
            12,
            panelTitle,
            panelSubtitle,
            labeledField("Base URL", baseUrlField),
            labeledField("Model", modelField),
            labeledField("User Message", userMessageField),
            labeledField("Style Context", styleContextArea),
            runFlowButton,
            labeledField("Transcript", transcriptArea),
            labeledField("Traversal", traversalArea)
        );
        runner.getStyleClass().add("runner-panel");
        runner.setPadding(new Insets(22));
        runner.setPrefWidth(360);
        return runner;
    }

    private VBox labeledField(String labelText, javafx.scene.Node field) {
        var label = new Label(labelText);
        label.getStyleClass().add("section-label");
        return new VBox(6, label, field);
    }

    private void seedInitialGraph() {
        var startNode = node.createStartNode("Greet the user and ask a short clarifying question if needed.");
        startNode.getPane().setLayoutX(60);
        startNode.getPane().setLayoutY(190);

        var firstDecision = node.createDecisionNode(
            "Decision 1",
            "Return True if the user's request is safe and actionable right now. Return False if it needs clarification."
        );
        firstDecision.getPane().setLayoutX(430);
        firstDecision.getPane().setLayoutY(160);

        var endNode = node.createEndConversationNode("End the conversation with a brief closing message.");
        endNode.getPane().setLayoutX(860);
        endNode.getPane().setLayoutY(320);

        addNode(startNode);
        addNode(firstDecision);
        addNode(endNode);

        addConnection(startNode, 0, firstDecision);
        addConnection(firstDecision, 0, endNode);
        addConnection(firstDecision, 1, endNode);
    }

    private void addDecisionNode() {
        var newNode = node.createDecisionNode(
            "Decision " + createdDecisionNodes,
            "Ask the LLM to evaluate this step as true or false based on the user's latest message."
        );
        newNode.getPane().setLayoutX(280 + (createdDecisionNodes * 32));
        newNode.getPane().setLayoutY(180 + (createdDecisionNodes * 26));
        createdDecisionNodes++;
        addNode(newNode);
    }

    private void addEndConversationNode() {
        var endNode = node.createEndConversationNode("End the conversation politely in one short sentence.");
        endNode.getPane().setLayoutX(860 + (nodes.size() * 18));
        endNode.getPane().setLayoutY(220 + (nodes.size() * 14));
        addNode(endNode);
    }

    private void addNode(node currentNode) {
        nodes.add(currentNode);
        currentNode.setDragCoordinateMapper(this::sceneToWorld);
        nodeLayer.getChildren().add(currentNode.getPane());
        currentNode.getPane().layoutXProperty().addListener((obs, oldValue, newValue) -> updateConnectionsForNode(currentNode));
        currentNode.getPane().layoutYProperty().addListener((obs, oldValue, newValue) -> updateConnectionsForNode(currentNode));
        currentNode.getPane().boundsInParentProperty().addListener((obs, oldValue, newValue) -> updateConnectionsForNode(currentNode));
        registerOutputHandlers(currentNode);
        attachInputHandlers(currentNode);
    }

    private void registerOutputHandlers(node currentNode) {
        currentNode.setOutputPortListener((sourceNode, port, outputIndex) -> {
            port.setOnMouseClicked(event -> {
                var currentIndex = sourceNode.getOutputPortIndex(port);
                if (currentIndex < 0) {
                    return;
                }

                startConnectionSelection(sourceNode, currentIndex);
                event.consume();
            });

            port.setOnMousePressed(event -> {
                var currentIndex = sourceNode.getOutputPortIndex(port);
                if (currentIndex < 0) {
                    return;
                }

                startConnectionSelection(sourceNode, currentIndex);
                event.consume();
            });
        });
    }

    private void attachInputHandlers(node targetNode) {
        targetNode.getInputPort().setOnMouseClicked(event -> {
            if (finishConnection(targetNode)) {
                event.consume();
            }
        });

        targetNode.getInputPort().setOnMouseReleased(event -> {
            if (finishConnection(targetNode)) {
                event.consume();
            }
        });

        targetNode.getPane().setOnMouseReleased(event -> {
            if (finishConnection(targetNode)) {
                event.consume();
            }
        });

        targetNode.getPane().setOnMouseClicked(event -> {
            if (finishConnection(targetNode)) {
                event.consume();
            }
        });
    }

    private void installCanvasHandlers() {
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (dragState.sourceNode == null) {
                return;
            }

            var start = toWorld(dragState.sourceNode.getOutputPortSceneCenter(dragState.outputIndex));
            updateCurve(previewLine, start, canvasPointToWorld(new Point2D(event.getX(), event.getY())));
        });

        canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (dragState.sourceNode == null) {
                return;
            }

            var start = toWorld(dragState.sourceNode.getOutputPortSceneCenter(dragState.outputIndex));
            updateCurve(previewLine, start, canvasPointToWorld(new Point2D(event.getX(), event.getY())));
        });

        canvas.setOnMousePressed(event -> {
            clearSelectedConnection();
            if (isPanTrigger(event)) {
                startPanning(event);
                event.consume();
                return;
            }

            if (event.getTarget() == canvas || event.getTarget() == lineLayer || event.getTarget() == gridCanvas) {
                cancelPendingConnection();
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (!panning) {
                return;
            }

            viewportTranslateX = panStartTranslateX + (event.getSceneX() - panStartSceneX);
            viewportTranslateY = panStartTranslateY + (event.getSceneY() - panStartSceneY);
            applyViewportTransform();
            event.consume();
        });

        canvas.setOnMouseReleased(event -> {
            if (panning) {
                panning = false;
                event.consume();
            }
        });

        canvas.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) {
                return;
            }

            var zoomFactor = event.getDeltaY() > 0 ? ZOOM_STEP : 1 / ZOOM_STEP;
            zoomAt(event.getSceneX(), event.getSceneY(), zoomFactor);
            event.consume();
        });
    }

    private void installConnectionUpdater() {
        updateAllConnections();
    }

    private Connection findConnection(node source, int outputIndex) {
        for (Connection connection : connections) {
            if (connection.source == source && connection.outputIndex == outputIndex) {
                return connection;
            }
        }
        return null;
    }

    private void addConnection(node source, int outputIndex, node target) {
        var line = createConnectionLine();
        var connection = new Connection(source, outputIndex, target, line);
        wireConnectionSelection(connection);
        lineLayer.getChildren().add(line);
        connections.add(connection);
        source.setOutputTarget(outputIndex, target.getNodeName());
        updateConnectionGeometry(connection);
    }

    private void startConnectionSelection(node sourceNode, int outputIndex) {
        dragState.sourceNode = sourceNode;
        dragState.outputIndex = outputIndex;

        var start = toWorld(sourceNode.getOutputPortSceneCenter(outputIndex));
        updateCurve(previewLine, start, start);
        previewLine.setVisible(true);
    }

    private boolean finishConnection(node targetNode) {
        if (dragState.sourceNode == null || dragState.sourceNode == targetNode) {
            return false;
        }

        var existing = findConnection(dragState.sourceNode, dragState.outputIndex);
        if (existing != null) {
            existing.target = targetNode;
            dragState.sourceNode.setOutputTarget(dragState.outputIndex, targetNode.getNodeName());
            updateConnectionGeometry(existing);
            clearSelectedConnection();
        } else {
            var connectionLine = createConnectionLine();
            var connection = new Connection(dragState.sourceNode, dragState.outputIndex, targetNode, connectionLine);
            wireConnectionSelection(connection);
            lineLayer.getChildren().add(0, connectionLine);
            connections.add(connection);
            dragState.sourceNode.setOutputTarget(dragState.outputIndex, targetNode.getNodeName());
            updateConnectionGeometry(connection);
        }

        cancelPendingConnection();
        return true;
    }

    private void cancelPendingConnection() {
        previewLine.setVisible(false);
        dragState.clear();
    }

    private void wireConnectionSelection(Connection connection) {
        connection.line.setOnMouseClicked(event -> {
            event.consume();
            setSelectedConnection(connection);
        });
    }

    private void setSelectedConnection(Connection connection) {
        if (selectedConnection != null) {
            selectedConnection.line.getStyleClass().remove("connection-line-selected");
        }

        selectedConnection = connection;
        if (selectedConnection != null && !selectedConnection.line.getStyleClass().contains("connection-line-selected")) {
            selectedConnection.line.getStyleClass().add("connection-line-selected");
        }
        deleteLineButton.setDisable(selectedConnection == null);
    }

    private void clearSelectedConnection() {
        if (selectedConnection != null) {
            selectedConnection.line.getStyleClass().remove("connection-line-selected");
            selectedConnection = null;
        }
        deleteLineButton.setDisable(true);
    }

    private void deleteSelectedConnection() {
        if (selectedConnection == null) {
            return;
        }

        selectedConnection.source.setOutputTarget(selectedConnection.outputIndex, "");
        lineLayer.getChildren().remove(selectedConnection.line);
        connections.remove(selectedConnection);
        selectedConnection = null;
        deleteLineButton.setDisable(true);
    }

    private void saveFlowchart() {
        if (currentFlowchartFile == null) {
            saveFlowchartAs();
            return;
        }

        writeFlowchartToFile(currentFlowchartFile);
    }

    private void saveFlowchartAs() {
        var chooser = new FileChooser();
        chooser.setTitle("Save Flowchart");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Flowchart JSON", "*.json"));
        chooser.setInitialFileName("flowchart.json");

        var file = chooser.showSaveDialog(primaryStage);
        if (file == null) {
            return;
        }

        currentFlowchartFile = file;
        writeFlowchartToFile(file);
    }

    private void writeFlowchartToFile(File file) {
        try {
            var saveData = new FlowchartFile();
            saveData.baseUrl = baseUrlField.getText();
            saveData.model = modelField.getText();
            saveData.transcript = transcriptArea.getText();
            saveData.styleContext = styleContextArea.getText();

            var nodeIds = new HashMap<node, String>();
            for (int i = 0; i < nodes.size(); i++) {
                var currentNode = nodes.get(i);
                var id = "node-" + i;
                nodeIds.put(currentNode, id);

                var savedNode = new SavedNode();
                savedNode.id = id;
                savedNode.name = currentNode.getNodeName();
                savedNode.prompt = currentNode.getPrompt();
                savedNode.kind = currentNode.getNodeKind().name();
                savedNode.x = currentNode.getPane().getLayoutX();
                savedNode.y = currentNode.getPane().getLayoutY();
                savedNode.outputs = currentNode.getOutputs();
                saveData.nodes.add(savedNode);
            }

            for (Connection connection : connections) {
                var savedConnection = new SavedConnection();
                savedConnection.sourceId = nodeIds.get(connection.source);
                savedConnection.outputIndex = connection.outputIndex;
                savedConnection.targetId = nodeIds.get(connection.target);
                saveData.connections.add(savedConnection);
            }

            Files.writeString(file.toPath(), gson.toJson(saveData));
            traversalArea.setText("Saved flowchart to " + file.getAbsolutePath());
        } catch (Exception e) {
            traversalArea.setText("Save failed: " + e.getMessage());
        }
    }

    private void loadFlowchart() {
        var chooser = new FileChooser();
        chooser.setTitle("Load Flowchart");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Flowchart JSON", "*.json"));

        var file = chooser.showOpenDialog(primaryStage);
        if (file == null) {
            return;
        }

        try {
            var content = Files.readString(file.toPath());
            var saveData = gson.fromJson(content, FlowchartFile.class);
            if (saveData == null) {
                traversalArea.setText("That file was empty or invalid.");
                return;
            }

            resetGraph();

            if (saveData.baseUrl != null) {
                baseUrlField.setText(saveData.baseUrl);
            }
            if (saveData.model != null) {
                modelField.setText(saveData.model);
            }
            transcriptArea.setText(saveData.transcript == null ? "" : saveData.transcript);
            styleContextArea.setText(saveData.styleContext == null ? "" : saveData.styleContext);
            updateStyleContextStatus(file.getName());
            traversalArea.clear();

            var nodeMap = new HashMap<String, node>();
            if (saveData.nodes != null) {
                for (SavedNode savedNode : saveData.nodes) {
                    var kind = NodeKind.valueOf(savedNode.kind);
                    var outputs = savedNode.outputs == null ? new ArrayList<OutputLink>() : savedNode.outputs;
                    var currentNode = node.createFromState(savedNode.name, savedNode.prompt, outputs, kind);
                    currentNode.getPane().setLayoutX(savedNode.x);
                    currentNode.getPane().setLayoutY(savedNode.y);
                    addNode(currentNode);
                    nodeMap.put(savedNode.id, currentNode);
                }
            }

            if (saveData.connections != null) {
                for (SavedConnection savedConnection : saveData.connections) {
                    var source = nodeMap.get(savedConnection.sourceId);
                    var target = nodeMap.get(savedConnection.targetId);
                    if (source != null && target != null) {
                        addConnection(source, savedConnection.outputIndex, target);
                    }
                }
            }

            currentFlowchartFile = file;
            createdDecisionNodes = countDecisionNodes() + 1;
            traversalArea.setText("Loaded flowchart from " + file.getAbsolutePath());
        } catch (Exception e) {
            traversalArea.setText("Load failed: " + e.getMessage());
        }
    }

    private void resetGraph() {
        nodes.clear();
        connections.clear();
        selectedConnection = null;
        dragState.clear();
        panning = false;
        deleteLineButton.setDisable(true);
        nodeLayer.getChildren().clear();
        lineLayer.getChildren().clear();
        previewLine = createConnectionLine();
        previewLine.setVisible(false);
        previewLine.setMouseTransparent(true);
        lineLayer.getChildren().add(previewLine);
    }

    private int countDecisionNodes() {
        int count = 0;
        for (node currentNode : nodes) {
            if (currentNode.getNodeKind() == NodeKind.DECISION) {
                count++;
            }
        }
        return count;
    }

    private CubicCurve createConnectionLine() {
        var curve = new CubicCurve();
        curve.getStyleClass().add("connection-line");
        curve.setFill(null);
        return curve;
    }

    private void installViewportListeners() {
        ChangeListener<Number> redrawListener = (obs, oldValue, newValue) -> renderGrid();
        canvas.widthProperty().addListener(redrawListener);
        canvas.heightProperty().addListener(redrawListener);
    }

    private void centerWorkspace() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return;
        }
        viewportTranslateX = Math.max(120, canvas.getWidth() * 0.22);
        viewportTranslateY = Math.max(100, canvas.getHeight() * 0.28);
        applyViewportTransform();
    }

    private boolean isPanTrigger(MouseEvent event) {
        return event.isMiddleButtonDown() || event.isSecondaryButtonDown();
    }

    private void startPanning(MouseEvent event) {
        panning = true;
        panStartSceneX = event.getSceneX();
        panStartSceneY = event.getSceneY();
        panStartTranslateX = viewportTranslateX;
        panStartTranslateY = viewportTranslateY;
    }

    private void zoomAt(double sceneX, double sceneY, double zoomFactor) {
        var clampedScale = clamp(viewportScale * zoomFactor, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(clampedScale - viewportScale) < 0.0001) {
            return;
        }

        var mouseInCanvas = canvas.sceneToLocal(sceneX, sceneY);
        var worldPointBeforeZoom = canvasPointToWorld(mouseInCanvas);
        viewportScale = clampedScale;
        viewportTranslateX = mouseInCanvas.getX() - (worldPointBeforeZoom.getX() * viewportScale);
        viewportTranslateY = mouseInCanvas.getY() - (worldPointBeforeZoom.getY() * viewportScale);
        applyViewportTransform();
    }

    private void applyViewportTransform() {
        world.setScaleX(viewportScale);
        world.setScaleY(viewportScale);
        world.setTranslateX(viewportTranslateX);
        world.setTranslateY(viewportTranslateY);
        renderGrid();
    }

    private void renderGrid() {
        if (gridCanvas == null) {
            return;
        }

        var width = gridCanvas.getWidth();
        var height = gridCanvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsContext graphics = gridCanvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, width, height);

        graphics.setFill(Color.rgb(12, 18, 30, 0.92));
        graphics.fillRect(0, 0, width, height);

        double scaledMinor = GRID_MINOR_SPACING * viewportScale;
        double scaledMajor = scaledMinor * GRID_MAJOR_EVERY;

        drawGridLines(graphics, width, height, scaledMinor, Color.rgb(126, 154, 255, 0.08));
        drawGridLines(graphics, width, height, scaledMajor, Color.rgb(126, 154, 255, 0.16));
    }

    private void drawGridLines(GraphicsContext graphics, double width, double height, double spacing, Color color) {
        if (spacing < 8) {
            return;
        }

        graphics.setStroke(color);
        graphics.setLineWidth(1);

        double offsetX = positiveModulo(viewportTranslateX, spacing);
        for (double x = offsetX; x <= width; x += spacing) {
            graphics.strokeLine(x, 0, x, height);
        }

        double offsetY = positiveModulo(viewportTranslateY, spacing);
        for (double y = offsetY; y <= height; y += spacing) {
            graphics.strokeLine(0, y, width, y);
        }
    }

    private double positiveModulo(double value, double modulus) {
        if (modulus == 0) {
            return 0;
        }
        double result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Point2D sceneToWorld(Point2D scenePoint) {
        return world.sceneToLocal(scenePoint);
    }

    private Point2D canvasPointToWorld(Point2D canvasPoint) {
        return sceneToWorld(canvas.localToScene(canvasPoint));
    }

    private Point2D toWorld(Point2D scenePoint) {
        return sceneToWorld(scenePoint);
    }

    private void updateCurve(CubicCurve curve, Point2D start, Point2D end) {
        var delta = Math.max(90, Math.abs(end.getX() - start.getX()) * 0.45);

        curve.setStartX(start.getX());
        curve.setStartY(start.getY());
        curve.setControlX1(start.getX() + delta);
        curve.setControlY1(start.getY());
        curve.setControlX2(end.getX() - delta);
        curve.setControlY2(end.getY());
        curve.setEndX(end.getX());
        curve.setEndY(end.getY());
    }

    private void runFlow() {
        var baseUrl = baseUrlField.getText().trim();
        var model = modelField.getText().trim();
        var userMessage = userMessageField.getText().trim();

        if (baseUrl.isBlank()) {
            traversalArea.setText("Add an Ollama base URL first.");
            return;
        }

        if (model.isBlank()) {
            traversalArea.setText("Add a model name first.");
            return;
        }

        if (userMessage.isBlank()) {
            traversalArea.setText("Enter a user message to run through the flowchart.");
            return;
        }

        runFlowButton.setDisable(true);
        traversalArea.setText("Running flow...");

        var snapshotNodes = new ArrayList<>(nodes);
        var snapshotConnections = new ArrayList<FlowConnection>();
        for (Connection connection : connections) {
            snapshotConnections.add(new FlowConnection(connection.source, connection.outputIndex, connection.target));
        }

        var existingTranscript = transcriptArea.getText();
        var styleContext = styleContextArea.getText();

        Task<FlowRunner.RunResult> task = new Task<>() {
            @Override
            protected FlowRunner.RunResult call() throws Exception {
                return flowRunner.runFlow(
                    baseUrl,
                    model,
                    userMessage,
                    existingTranscript,
                    styleContext,
                    snapshotNodes,
                    snapshotConnections
                );
            }
        };

        task.setOnSucceeded(event -> {
            var result = task.getValue();
            traversalArea.setText(result.traversalPath());
            appendTranscript("User", userMessage);
            appendTranscript("Assistant", result.assistantMessage());
            userMessageField.clear();
            runFlowButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            var error = task.getException();
            traversalArea.setText(error == null ? "Flow run failed." : error.getMessage());
            runFlowButton.setDisable(false);
        });

        Thread worker = new Thread(task, "flow-runner");
        worker.setDaemon(true);
        worker.start();
    }

    private void appendTranscript(String speaker, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (!transcriptArea.getText().isBlank()) {
            transcriptArea.appendText("\n\n");
        }
        transcriptArea.appendText(speaker + ": " + text);
    }

    private void uploadStyleContextFile() {
        var chooser = new FileChooser();
        chooser.setTitle("Upload Style Context");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md", "*.text"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        var file = chooser.showOpenDialog(primaryStage);
        if (file == null) {
            return;
        }

        try {
            var content = Files.readString(file.toPath());
            if (content.isBlank()) {
                traversalArea.setText("The selected style context file was empty.");
                return;
            }

            styleContextArea.setText(content);
            updateStyleContextStatus(file.getName());
            traversalArea.setText("Loaded style context from " + file.getAbsolutePath());
        } catch (IOException e) {
            traversalArea.setText("Could not read style context file: " + e.getMessage());
        }
    }

    private void updateStyleContextStatus(String sourceName) {
        if (styleContextStatusLabel == null) {
            return;
        }

        if (sourceName != null && !sourceName.isBlank()) {
            styleContextStatusLabel.setText("Style: " + sourceName);
            return;
        }

        var text = styleContextArea == null ? "" : styleContextArea.getText();
        styleContextStatusLabel.setText(text == null || text.isBlank() ? "No style file loaded" : "Style context ready");
    }

    private void updateConnectionsForNode(node currentNode) {
        for (Connection connection : connections) {
            if (connection.source == currentNode || connection.target == currentNode) {
                updateConnectionGeometry(connection);
            }
        }
    }

    private void updateAllConnections() {
        for (Connection connection : connections) {
            updateConnectionGeometry(connection);
        }
    }

    private void updateConnectionGeometry(Connection connection) {
        if (!connection.source.hasOutputs() || !connection.target.hasInputPort()) {
            return;
        }

        if (connection.outputIndex >= connection.source.getOutputs().size()) {
            return;
        }

        var start = toWorld(connection.source.getOutputPortSceneCenter(connection.outputIndex));
        var end = toWorld(connection.target.getInputPortSceneCenter());
        updateCurve(connection.line, start, end);
    }

    public static void main(String[] args) {
        launch();
    }

    private static final class DragState {
        private node sourceNode;
        private int outputIndex;

        private void clear() {
            sourceNode = null;
            outputIndex = -1;
        }
    }

    private static final class Connection {
        private final node source;
        private final int outputIndex;
        private node target;
        private final CubicCurve line;

        private Connection(node source, int outputIndex, node target, CubicCurve line) {
            this.source = source;
            this.outputIndex = outputIndex;
            this.target = target;
            this.line = line;
        }
    }

    private static final class FlowchartFile {
        private String baseUrl;
        private String model;
        private String transcript;
        private String styleContext;
        private List<SavedNode> nodes = new ArrayList<>();
        private List<SavedConnection> connections = new ArrayList<>();
    }

    private static final class SavedNode {
        private String id;
        private String name;
        private String prompt;
        private String kind;
        private double x;
        private double y;
        private List<OutputLink> outputs = new ArrayList<>();
    }

    private static final class SavedConnection {
        private String sourceId;
        private int outputIndex;
        private String targetId;
    }
}
