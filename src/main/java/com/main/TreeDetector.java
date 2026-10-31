package com.main;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public class TreeDetector {

    private final Client client;
    private final Set<Integer> targetIds;
    private static final int SCENE_SIZE = 104; // Constants.SCENE_SIZE

    public TreeDetector(Client client, Set<Integer> targetIds) {
        this.client = client;
        this.targetIds = targetIds;
    }

    public List<TreeInfo> findTrees() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }

        WorldPoint playerPos = player.getWorldLocation();
        if (playerPos == null) {
            return Collections.emptyList();
        }

        Scene scene = client.getScene();
        if (scene == null) {
            return Collections.emptyList();
        }

        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();

        List<TreeInfo> results = new ArrayList<>();
        // A tree's footprint spans multiple tiles, so the same GameObject is
        // returned by tile.getGameObjects() from several (x, y) positions.
        // Dedupe on (id, worldLocation).
        Set<Long> seen = new HashSet<>();

        for (int x = 0; x < SCENE_SIZE; x++) {
            for (int y = 0; y < SCENE_SIZE; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) continue;

                GameObject[] objects = tile.getGameObjects();
                if (objects == null) continue;

                for (GameObject obj : objects) {
                    if (obj == null) continue;

                    int objectId = obj.getId();
                    if (objectId == -1) continue;
                    if (!targetIds.contains(objectId)) continue;

                    WorldPoint objPos = obj.getWorldLocation();

                    // Unique key per object (id + origin tile).
                    long key = ((long) objectId << 32)
                            ^ ((long) objPos.getX() << 16)
                            ^ objPos.getY();
                    if (!seen.add(key)) {
                        continue; // already recorded this object from another tile
                    }

                    LocalPoint localPoint = LocalPoint.fromWorld(client, objPos);
                    if (localPoint == null) continue; // outside the loaded scene

                    Point canvasPoint = Perspective.localToCanvas(client, localPoint, plane);
                    if (canvasPoint == null) continue; // behind camera / off-screen

                    // Name is only used for display now, never for filtering.
                    ObjectComposition def = client.getObjectDefinition(objectId);
                    String name = (def != null && def.getName() != null)
                            ? def.getName()
                            : "Tree";

                    int distance = playerPos.distanceTo(objPos);
                    results.add(new TreeInfo(name, objPos, distance, canvasPoint, objectId));
                }
            }
        }

        results.sort(Comparator.comparingInt(TreeInfo::getDistance));
        return results;
    }

    public Optional<TreeInfo> findNearestTree() {
        return findTrees().stream().findFirst();
    }

    @lombok.Value
    public static class TreeInfo {
        String name;
        WorldPoint worldPosition;
        int distance;
        Point canvasPos;   // net.runelite.api.Point
        int objectId;
    }
}

class TreeOverlay extends Overlay {

    private final Supplier<List<TreeDetector.TreeInfo>> treeSupplier;

    TreeOverlay(Supplier<List<TreeDetector.TreeInfo>> treeSupplier) {
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