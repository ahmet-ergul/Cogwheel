package dev.kima.cogwheel.model;

/** Simple 2D point/vector in world coordinates. */
public record Vec2(double x, double y) {
    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 add(double dx, double dy) {
        return new Vec2(x + dx, y + dy);
    }

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }
}
