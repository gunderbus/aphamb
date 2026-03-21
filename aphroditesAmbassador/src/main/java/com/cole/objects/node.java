package com.cole.objects;


import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;


public class node {
    private Pane pane;
    private Label name;
    private ChoiceBox<String> dropDownIn;
    private ChoiceBox<String> dropDownOut;

    public node(String name1, String defaultIn, String defaultOut){
        pane = new Pane();
        pane.getStylesheets().add(
            node.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        pane.getStyleClass().add("node-pane");
        name = new Label(name1);

        dropDownIn = new ChoiceBox<>();
        dropDownIn.getItems().addAll(defaultIn);

        dropDownOut = new ChoiceBox<>();
        dropDownOut.getItems().addAll(defaultOut);
    }

    public boolean isClickedLabel(){
        return name.isHover();
    }
}
