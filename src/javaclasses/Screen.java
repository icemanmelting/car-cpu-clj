package javaclasses;

import javaclasses.utils.CustomEntry;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by iceman on 18/07/2016.
 */
public class Screen {
    public static final int WINDOW_WIDTH = 800;
    public static final int WINDOW_HEIGHT = 480;

    private List<CustomEntry<Node, AbsolutePositioning>> nodes;

    public Screen() {
        nodes = new ArrayList<>();
    }

    public List<CustomEntry<Node, AbsolutePositioning>> getNodes() {
        return nodes;
    }

    public void setNodes(List<CustomEntry<Node, AbsolutePositioning>> nodes) {
        this.nodes = nodes;
    }
}
