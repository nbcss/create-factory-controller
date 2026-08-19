package io.github.nbcss.createfactorycontroller.content.gui.widget;

public enum ConnectedTargetRole {
    INPUT(0x61D6F2),   // neighbour -> hovered component
    OUTPUT(0xFCD860);  // hovered component -> neighbour

    private final int defaultColor;

    ConnectedTargetRole(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    public int defaultColor() {
        return defaultColor;
    }
}
