package com.main.data;

import java.util.Objects;

public final class WidgetDto {
    private final String name;
    private final int canvasX;
    private final int canvasY;
    private final int screenX;
    private final int screenY;
    private final int width;
    private final int height;
    private final boolean visible;

    public WidgetDto(String name, int canvasX, int canvasY,
                     int screenX, int screenY,
                     int width, int height, boolean visible) {
        this.name = name;
        this.canvasX = canvasX;
        this.canvasY = canvasY;
        this.screenX = screenX;
        this.screenY = screenY;
        this.width = width;
        this.height = height;
        this.visible = visible;
    }

    public String name()      { return name; }
    public int canvasX()      { return canvasX; }
    public int canvasY()      { return canvasY; }
    public int screenX()      { return screenX; }
    public int screenY()      { return screenY; }
    public int width()        { return width; }
    public int height()       { return height; }
    public boolean visible()  { return visible; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WidgetDto)) return false;
        WidgetDto that = (WidgetDto) o;
        return canvasX == that.canvasX && canvasY == that.canvasY
                && screenX == that.screenX && screenY == that.screenY
                && width   == that.width   && height   == that.height
                && visible == that.visible
                && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, canvasX, canvasY, screenX, screenY,
                width, height, visible);
    }

    @Override
    public String toString() {
        return "WidgetDto[name=" + name
                + ", canvas=(" + canvasX + "," + canvasY + ")"
                + ", screen=(" + screenX + "," + screenY + ")"
                + ", size=" + width + "x" + height
                + ", visible=" + visible + "]";
    }
}