package com.cole.objects;


import javafx.scene.control.Label;
import javafx.scene.layout.Pane;


public class node {
    private Pane pane;
    private Label name;

    public node(String name1){
        pane = new Pane();
        pane.getStylesheets().add(
            node.class.getResource("/com/cole/styles/node-pane.css").toExternalForm()
        );
        pane.getStyleClass().add("node-pane");
        name = new Label(name1);
    }

    public boolean isClickedLabel(){
        return name.isHover();
    }
}
