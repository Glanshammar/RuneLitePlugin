package com.main.data;

import java.util.Objects;

public final class ObjectDto {
    private final String name;
    private final int id;
    private final int worldX;
    private final int worldY;
    private final int plane;
    private final int canvasX;
    private final int canvasY;
    private final int screenX;
    private final int screenY;
    private final int distance;

    public ObjectDto(String name, int id, int worldX, int worldY, int plane,
                     int canvasX, int canvasY, int screenX, int screenY, int distance) {
        this.name = name;
        this.id = id;
        this.worldX = worldX;
        this.worldY = worldY;
        this.plane = plane;
        this.canvasX = canvasX;
        this.canvasY = canvasY;
        this.screenX = screenX;
        this.screenY = screenY;
        this.distance = distance;
    }

    public String name() { return name; }
    public int id() { return id; }
    public int worldX() { return worldX; }
    public int worldY() { return worldY; }
    public int plane() { return plane; }
    public int canvasX() { return canvasX; }
    public int canvasY() { return canvasY; }
    public int screenX() { return screenX; }
    public int screenY() { return screenY; }
    public int distance() { return distance; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectDto)) return false;
        ObjectDto that = (ObjectDto) o;
        return id == that.id &&
                worldX == that.worldX &&
                worldY == that.worldY &&
                plane == that.plane &&
                canvasX == that.canvasX &&
                canvasY == that.canvasY &&
                screenX == that.screenX &&
                screenY == that.screenY &&
                distance == that.distance &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, worldX, worldY, plane,
                canvasX, canvasY, screenX, screenY, distance);
    }

    @Override
    public String toString() {
        return "ObjectDto[name=" + name +
                ", id=" + id +
                ", worldX=" + worldX +
                ", worldY=" + worldY +
                ", plane=" + plane +
                ", canvasX=" + canvasX +
                ", canvasY=" + canvasY +
                ", screenX=" + screenX +
                ", screenY=" + screenY +
                ", distance=" + distance + "]";
    }
}
