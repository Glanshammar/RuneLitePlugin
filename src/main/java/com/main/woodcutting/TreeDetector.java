package com.main.woodcutting;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;
import java.util.*;
import java.util.List;

@Slf4j
public class TreeDetector {

    private final Client client;
    private final Set<Integer> targetIds;
    private static final int SCENE_SIZE = 104;

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

                    long key = ((long) objectId << 32)
                            ^ ((long) objPos.getX() << 16)
                            ^ objPos.getY();
                    if (!seen.add(key)) {
                        continue;
                    }

                    LocalPoint localPoint = LocalPoint.fromWorld(client, objPos);
                    if (localPoint == null) continue;

                    Point canvasPoint = Perspective.localToCanvas(client, localPoint, plane);
                    if (canvasPoint == null) continue;

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
        Point canvasPos;
        int objectId;
    }
}

