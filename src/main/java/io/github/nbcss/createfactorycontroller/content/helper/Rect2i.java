package io.github.nbcss.createfactorycontroller.content.helper;

import org.jetbrains.annotations.Contract;
import org.joml.Vector2dc;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public record Rect2i(int x, int y, int w, int h) {

    public static Rect2i fromXYWH(int x, int y, int w, int h) {
        return new Rect2i(x, y, w, h);
    }

    public static Rect2i fromXYWH(Vector2ic topLeft, Vector2ic size) {
        return new Rect2i(topLeft.x(), topLeft.y(), size.x(), size.y());
    }

    public static Rect2i fromBounds(int minX, int minY, int maxX, int maxY) {
        return new Rect2i(minX, minY, maxX - minX, maxY - minY);
    }

    public static Rect2i fromBounds(Vector2ic topLeft, Vector2ic bottomRight) {
        return new Rect2i(topLeft.x(), topLeft.y(), bottomRight.x() - topLeft.x(), bottomRight.y() - topLeft.y());
    }

    public int minX() { return x; }
    public int minY() { return y; }
    public int maxX() { return x + w; }
    public int maxY() { return y + h; }

    @Contract("-> new") public Vector2i topLeft()     { return new Vector2i(x, y); }
    @Contract("-> new") public Vector2i bottomRight() { return new Vector2i(x + w, y + h); }
    @Contract("-> new") public Vector2i size()        { return new Vector2i(w, h); }

    /** How points on the rectangle's edges are treated. */
    public enum Boundary {
        INCLUSIVE, // Includes both minimum and maximum edges.
        HALF_OPEN, // Includes minimum edges and excludes maximum edges.
        EXCLUSIVE, // Excludes both minimum and maximum edges.
    }

    public boolean intersects(Rect2i other, Boundary boundary) {
        return switch (boundary) {
            case INCLUSIVE -> minX() <= other.maxX() && maxX() >= other.minX()
                    && minY() <= other.maxY() && maxY() >= other.minY();
            case HALF_OPEN, EXCLUSIVE -> minX() < other.maxX() && maxX() > other.minX()
                    && minY() < other.maxY() && maxY() > other.minY();
        };
    }

    public boolean contains(int x, int y, Boundary boundary) {
        return switch (boundary) {
            case INCLUSIVE -> x >= minX() && x <= maxX() && y >= minY() && y <= maxY();
            case HALF_OPEN -> x >= minX() && x < maxX() && y >= minY() && y < maxY();
            case EXCLUSIVE -> x > minX() && x < maxX() && y > minY() && y < maxY();
        };
    }

    public boolean contains(double x, double y, Boundary boundary) {
        return switch (boundary) {
            case INCLUSIVE -> x >= minX() && x <= maxX() && y >= minY() && y <= maxY();
            case HALF_OPEN -> x >= minX() && x < maxX() && y >= minY() && y < maxY();
            case EXCLUSIVE -> x > minX() && x < maxX() && y > minY() && y < maxY();
        };
    }

    public boolean contains(Vector2ic pos, Boundary boundary) {
        return contains(pos.x(), pos.y(), boundary);
    }

    public boolean contains(Vector2dc pos, Boundary boundary) {
        return contains(pos.x(), pos.y(), boundary);
    }

}
