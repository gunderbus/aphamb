package com.cole;

import com.cole.objects.node;
import com.cole.objects.node.OutputLink;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.CubicCurve;
import javafx.stage.Stage;

/**
 * JavaFX App
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        var root = new Pane();
        root.getStyleClass().add("editor-root");

        var lineLayer = new Pane();
        lineLayer.setMouseTransparent(true);
        lineLayer.prefWidthProperty().bind(root.widthProperty());
        lineLayer.prefHeightProperty().bind(root.heightProperty());

        var nodeLayer = new Pane();
        nodeLayer.prefWidthProperty().bind(root.widthProperty());
        nodeLayer.prefHeightProperty().bind(root.heightProperty());

        var firstNode = new node(
            "User Choice",
            "What should happen next?",
            List.of(
                new OutputLink("Yes", "Approve Order"),
                new OutputLink("No", "Show Error"),
                new OutputLink("Maybe", "Ask Again")
            )
        );
        firstNode.getPane().setLayoutX(140);
        firstNode.getPane().setLayoutY(120);

        var secondNode = new node(
            "Approve Order",
            "Create confirmation",
            List.of(
                new OutputLink("Done", "Archive"),
                new OutputLink("Retry", "Review")
            )
        );
        secondNode.getPane().setLayoutX(620);
        secondNode.getPane().setLayoutY(140);

        var thirdNode = new node(
            "Show Error",
            "Tell the user what failed",
            List.of(new OutputLink("Back", "User Choice"))
        );
        thirdNode.getPane().setLayoutX(620);
        thirdNode.getPane().setLayoutY(360);

        nodeLayer.getChildren().addAll(firstNode.getPane(), secondNode.getPane(), thirdNode.getPane());
        root.getChildren().addAll(lineLayer, nodeLayer);

        var connections = new ArrayList<Connection>();
        addConnection(lineLayer, root, connections, firstNode, 0, secondNode);
        addConnection(lineLayer, root, connections, firstNode, 1, thirdNode);

        var previewLine = createConnectionLine();
        previewLine.setVisible(false);
        lineLayer.getChildren().add(previewLine);

        var dragState = new DragState();

        registerOutputHandlers(root, previewLine, dragState, firstNode);
        registerOutputHandlers(root, previewLine, dragState, secondNode);
        registerOutputHandlers(root, previewLine, dragState, thirdNode);

        attachInputHandlers(lineLayer, root, previewLine, dragState, connections, firstNode);
        attachInputHandlers(lineLayer, root, previewLine, dragState, connections, secondNode);
        attachInputHandlers(lineLayer, root, previewLine, dragState, connections, thirdNode);

        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (dragState.sourceNode == null) {
                return;
            }

            var start = toRoot(root, dragState.sourceNode.getOutputPortSceneCenter(dragState.outputIndex));
            updateCurve(previewLine, start, new Point2D(event.getX(), event.getY()));
        });

        root.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            dragState.clear();
            previewLine.setVisible(false);
        });

        var lineUpdater = new AnimationTimer() {
            @Override
            public void handle(long now) {
                for (Connection connection : connections) {
                    var start = toRoot(root, connection.source.getOutputPortSceneCenter(connection.outputIndex));
                    var end = toRoot(root, connection.target.getInputPortSceneCenter());
                    updateCurve(connection.line, start, end);
                }
            }
        };
        lineUpdater.start();

        var scene = new Scene(root, 1120, 720);
        scene.getStylesheets().add(
            App.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("Aphrodite's Ambassador");
        stage.show();
    }

    private void registerOutputHandlers(
        Pane root,
        CubicCurve previewLine,
        DragState dragState,
        node currentNode
    ) {
        currentNode.setOutputPortListener((sourceNode, port, outputIndex) -> {
            port.setOnMousePressed(event -> {
                var currentIndex = sourceNode.getOutputPortIndex(port);
                if (currentIndex < 0) {
                    return;
                }

                dragState.sourceNode = sourceNode;
                dragState.outputIndex = currentIndex;

                var start = toRoot(root, sourceNode.getOutputPortSceneCenter(currentIndex));
                updateCurve(previewLine, start, start);
                previewLine.setVisible(true);
                event.consume();
            });
        });
    }

    private void attachInputHandlers(
        Pane lineLayer,
        Pane root,
        CubicCurve previewLine,
        DragState dragState,
        List<Connection> connections,
        node targetNode
    ) {
        targetNode.getInputPort().setOnMouseReleased(event -> {
            if (dragState.sourceNode == null) {
                return;
            }

            var connectionLine = createConnectionLine();
            lineLayer.getChildren().add(0, connectionLine);
            connections.add(new Connection(dragState.sourceNode, dragState.outputIndex, targetNode, connectionLine));

            previewLine.setVisible(false);
            dragState.clear();
            event.consume();
        });
    }

    private void addConnection(
        Pane lineLayer,
        Pane root,
        List<Connection> connections,
        node source,
        int outputIndex,
        node target
    ) {
        var line = createConnectionLine();
        lineLayer.getChildren().add(line);
        connections.add(new Connection(source, outputIndex, target, line));

        var start = toRoot(root, source.getOutputPortSceneCenter(outputIndex));
        var end = toRoot(root, target.getInputPortSceneCenter());
        updateCurve(line, start, end);
    }

    private CubicCurve createConnectionLine() {
        var curve = new CubicCurve();
        curve.getStyleClass().add("connection-line");
        curve.setFill(null);
        curve.setMouseTransparent(true);
        return curve;
    }

    private Point2D toRoot(Pane root, Point2D scenePoint) {
        return root.sceneToLocal(scenePoint);
    }

    private void updateCurve(CubicCurve curve, Point2D start, Point2D end) {
        var delta = Math.max(80, Math.abs(end.getX() - start.getX()) * 0.5);

        curve.setStartX(start.getX());
        curve.setStartY(start.getY());
        curve.setControlX1(start.getX() + delta);
        curve.setControlY1(start.getY());
        curve.setControlX2(end.getX() - delta);
        curve.setControlY2(end.getY());
        curve.setEndX(end.getX());
        curve.setEndY(end.getY());
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
        private final node target;
        private final CubicCurve line;

        private Connection(node source, int outputIndex, node target, CubicCurve line) {
            this.source = source;
            this.outputIndex = outputIndex;
            this.target = target;
            this.line = line;
        }
    }
}
