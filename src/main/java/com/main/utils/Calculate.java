package com.main.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Point;

import java.awt.*;

@Slf4j
public class Calculate {
    private Calculate() {}

    public static Point objectCenter(GameObject obj) {
        Shape clickbox = obj.getClickbox();
        if (clickbox != null) {
            Rectangle b = clickbox.getBounds();
            if (b.width > 0 && b.height > 0) {
                int cx = b.x + b.width / 2;
                int cy = b.y + b.height / 2;
                return new Point(cx, cy);
            }
        }

        return null;
    }
}