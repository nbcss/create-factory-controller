package io.github.nbcss.createfactorycontroller.content.helper;

public record Rect2i(int x, int y, int w, int h) {

    public static Rect2i fromXYWH(int x, int y, int w, int h) {
        return new Rect2i(x, y, w, h);
    }

    public static Rect2i fromBounds(int minX, int minY, int maxX, int maxY) {
        return new Rect2i(minX, minY, maxX - minX, maxY - minY);
    }

    public int minX() { return x; }
    public int minY() { return y; }
    public int maxX() { return x + w; }
    public int maxY() { return y + h; }

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

}
