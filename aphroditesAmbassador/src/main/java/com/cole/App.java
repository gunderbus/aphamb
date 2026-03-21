package com.cole;

import com.cole.objects.node;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;


/**
 * JavaFX App
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        var root = new Pane();
        var editorNode = new node("Node Name", "Input", "Output");
        var nodePane = editorNode.getPane();

        nodePane.setLayoutX(160);
        nodePane.setLayoutY(120);
        root.getChildren().add(nodePane);

        var scene = new Scene(root, 640, 480);
        stage.setScene(scene);
        stage.setTitle("Aphrodite's Ambassador");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}
