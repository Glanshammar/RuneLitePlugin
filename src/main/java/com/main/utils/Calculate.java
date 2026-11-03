package com.main.utils;

import net.runelite.api.GameObject;
import net.runelite.api.Point;

import java.awt.*;

public class Calculate {
    private Calculate(){}

    public static Point objectCenter(GameObject obj, Point fallback) {
        if (obj != null) {
            Shape hull = obj.getConvexHull();
            if (hull != null) {
                Rectangle r = hull.getBounds();
                if (r.width > 0 && r.height > 0) {
                    return new Point(r.x + r.width / 2, r.y + r.height / 2);
                }
            }
        }
        return fallback;
    }
}