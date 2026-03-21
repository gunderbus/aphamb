package com.cole.objects;


import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;


public class node {
    private final Pane pane;
    private final Label name;
    private final ChoiceBox<String> dropDownIn;
    private final ChoiceBox<String> dropDownOut;

    public node(String name1, String defaultIn, String defaultOut){
        pane = new Pane();
        pane.getStylesheets().add(
            node.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        pane.getStyleClass().add("node-pane");

        name = new Label(name1);
        name.getStyleClass().add("node-title");

        dropDownIn = new ChoiceBox<>();
        dropDownIn.getItems().add(defaultIn);
        dropDownIn.setValue(defaultIn);

        dropDownOut = new ChoiceBox<>();
        dropDownOut.getItems().add(defaultOut);
        dropDownOut.setValue(defaultOut);

        var header = new Pane(name);
        header.getStyleClass().add("node-header");
        header.setPrefWidth(260);

        var body = new VBox(10, dropDownIn, dropDownOut);
        body.getStyleClass().add("node-body");
        body.setLayoutY(48);
        body.setPrefWidth(260);

        pane.setPrefSize(260, 150);
        pane.getChildren().addAll(header, body);
    }

    public boolean isClickedLabel(){
        return name.isHover();
    }

    public Pane getPane() {
        return pane;
    }
}
