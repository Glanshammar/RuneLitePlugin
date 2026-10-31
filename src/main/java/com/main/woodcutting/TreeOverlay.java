package com.main.woodcutting;

import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

public class TreeOverlay extends Overlay {

    private final Supplier<List<TreeDetector.TreeInfo>> treeSupplier;

    public TreeOverlay(Supplier<List<TreeDetector.TreeInfo>> treeSupplier) {
        this.treeSupplier = treeSupplier;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (TreeDetector.TreeInfo tree : treeSupplier.get()) {
            Point p = tree.getCanvasPos();
            graphics.setColor(Color.RED);
            graphics.drawOval(p.getX() - 10, p.getY() - 10, 20, 20);
            graphics.drawString(tree.getName() + " (" + tree.getDistance() + "t)", p.getX(), p.getY() - 15);
        }
        return null;
    }
}
