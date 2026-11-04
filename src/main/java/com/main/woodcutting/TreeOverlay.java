package com.main.woodcutting;

import com.main.utils.ObjectScanner;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

public class TreeOverlay extends Overlay {

    private final Supplier<List<ObjectScanner.Target>> treeSupplier;

    public TreeOverlay(Supplier<List<ObjectScanner.Target>> treeSupplier) {
        this.treeSupplier = treeSupplier;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (ObjectScanner.Target t : treeSupplier.get()) {
            Point p = t.getCanvasPos();
            graphics.setColor(Color.RED);
            graphics.drawOval(p.getX() - 10, p.getY() - 10, 20, 20);
            graphics.drawString(t.getName() + " (" + t.getDistance() + "t)", p.getX(), p.getY() - 15);
        }
        return null;
    }
}
