package com.main.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

@Slf4j
public class ObjectScanner {
    private ObjectScanner(){}

    private static volatile Client client;

    public static void init(Client c) {
        client = c;
    }

    // ============================================================
    //  GAME OBJECTS
    // ============================================================
    private static boolean matchesTarget(TileObject obj, Set<Integer> objectIds, Set<WorldPoint> targetLocations) {
        if (objectIds != null && !objectIds.isEmpty() && !objectIds.contains(obj.getId())) {
            return false;
        }
        WorldPoint wp = obj.getWorldLocation();
        return wp != null && targetLocations.contains(wp);
    }

    public static TileObject findObjectCoords(Set<Integer> objectIds, Collection<WorldPoint> locationList) {
        if (locationList == null || locationList.isEmpty()) {
            return null;
        }

        Scene scene = client.getScene();
        if (scene == null || client.getLocalPlayer() == null) {
            return null;
        }

        LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
        if (playerLocation == null) {
            return null;
        }

        Set<WorldPoint> targetLocations = new HashSet<>(locationList);
        List<TileObject> matchingObjects = new ArrayList<>();
        Tile[][][] tiles = scene.getTiles();

        for (Tile[][] value : tiles) {
            for (int x = 0; x < value.length; x++) {
                for (int y = 0; y < value[x].length; y++) {
                    Tile tile = value[x][y];
                    if (tile == null) {
                        continue;
                    }

                    // 1. Check GameObjects on the tile
                    GameObject[] gameObjects = tile.getGameObjects();
                    if (gameObjects != null) {
                        for (GameObject obj : gameObjects) {
                            if (obj != null && matchesTarget(obj, objectIds, targetLocations)) {
                                matchingObjects.add(obj);
                            }
                        }
                    }

                    // 2. Check WallObject on the tile
                    WallObject wallObject = tile.getWallObject();
                    if (wallObject != null && matchesTarget(wallObject, objectIds, targetLocations)) {
                        matchingObjects.add(wallObject);
                    }
                }
            }
        }

        if (matchingObjects.isEmpty()) {
            return null;
        }

        return matchingObjects.stream()
                .filter(obj -> obj.getLocalLocation() != null)
                .min(Comparator.comparingInt(obj -> obj.getLocalLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    public static List<GameObject> findGameObjects(Set<Integer> objectIds, Predicate<GameObject> filter) {
        List<GameObject> foundObjects = new ArrayList<>();
        Scene scene = client.getScene();
        if (scene == null) {
            return foundObjects;
        }

        Tile[][][] tiles = scene.getTiles();
        for (int z = 0; z < tiles.length; z++) {
            for (int x = 0; x < tiles[z].length; x++) {
                for (int y = 0; y < tiles[z][x].length; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    GameObject[] gameObjects = tile.getGameObjects();
                    if (gameObjects != null) {
                        for (GameObject obj : gameObjects) {
                            if (obj != null && objectIds.contains(obj.getId())) {
                                // Apply the filter if present
                                if (filter == null || filter.test(obj)) {
                                    foundObjects.add(obj);
                                }
                            }
                        }
                    }
                }
            }
        }
        return foundObjects;
    }

    public static List<WallObject> findWallObjects(Set<Integer> objectIds, Predicate<WallObject> filter) {
        List<WallObject> foundObjects = new ArrayList<>();
        Scene scene = client.getScene();
        if (scene == null) {
            return foundObjects;
        }

        Tile[][][] tiles = scene.getTiles();
        for (int z = 0; z < tiles.length; z++) {
            for (int x = 0; x < tiles[z].length; x++) {
                for (int y = 0; y < tiles[z][x].length; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    WallObject wallObject = tile.getWallObject();
                    if (wallObject != null && (objectIds == null || objectIds.contains(wallObject.getId()))) {
                        if (filter == null || filter.test(wallObject)) {
                            foundObjects.add(wallObject);
                        }
                    }
                }
            }
        }
        return foundObjects;
    }

    public static boolean objectExistsAt(Set<Integer> objectIds, WorldPoint location) {
        if (objectIds == null || objectIds.isEmpty() || location == null) return false;

        if (client == null) return false;

        Scene scene = client.getScene();
        if (scene == null) return false;

        LocalPoint lp = LocalPoint.fromWorld(client, location);
        if (lp == null) return false;               // off-scene / not loaded

        int plane = location.getPlane();
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (plane < 0 || plane >= 4) return false;
        if (sx < 0 || sx >= 104 || sy < 0 || sy >= 104) return false;

        Tile[][][] tiles = scene.getTiles();
        Tile tile = tiles[plane][sx][sy];
        if (tile == null) return false;

        GameObject[] gameObjects = tile.getGameObjects();
        if (gameObjects != null) {
            for (GameObject obj : gameObjects) {
                if (obj != null && objectIds.contains(obj.getId())) return true;
            }
        }

        WallObject wall = tile.getWallObject();
        return wall != null && objectIds.contains(wall.getId());
    }

    public static boolean objectExistsAt(int objectId, WorldPoint location) {
        return objectExistsAt(Set.of(objectId), location);
    }

    // ###############################
    // Game object helpers
    // ###############################
    public static List<GameObject> findGameObjects(Set<Integer> objectIds) {
        return findGameObjects(objectIds, null);
    }

    public static List<GameObject> findGameObjects(int objectId) {
        return findGameObjects(Set.of(objectId), null);
    }

    public static GameObject findNearestGameObject(Set<Integer> objectIds, Predicate<GameObject> filter) {
        // Reuse the main finder function with filters
        List<GameObject> matchingObjects = findGameObjects(objectIds, filter);

        if (matchingObjects.isEmpty() || client.getLocalPlayer() == null) {
            return null;
        }

        LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
        if (playerLocation == null) {
            return null;
        }

        // Find the object with the minimum distance to the local player
        return matchingObjects.stream()
                .min(Comparator.comparingInt(obj -> obj.getLocalLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    public static GameObject findNearestGameObject(int objectId){
        return findNearestGameObject(Set.of(objectId), null);
    }

    public static GameObject findNearestGameObject(Set<Integer> objectIds){
        return findNearestGameObject(objectIds, null);
    }

    // ###############################
    // Wall object helpers
    // ###############################
    public static List<WallObject> findWallObjects(Set<Integer> objectIds) {
        return findWallObjects(objectIds, null);
    }

    public static List<WallObject> findWallObjects(int objectId) {
        return findWallObjects(Set.of(objectId), null);
    }

    public static WallObject findNearestWallObject(Set<Integer> objectIds, Predicate<WallObject> filter) {
        List<WallObject> matchingObjects = findWallObjects(objectIds, filter);

        if (matchingObjects.isEmpty() || client.getLocalPlayer() == null) {
            return null;
        }

        LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
        if (playerLocation == null) {
            return null;
        }

        return matchingObjects.stream()
                .filter(obj -> obj.getLocalLocation() != null)
                .min(Comparator.comparingInt(obj -> obj.getLocalLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    public static WallObject findNearestWallObject(int objectId) {
        return findNearestWallObject(Set.of(objectId), null);
    }

    public static WallObject findNearestWallObject(Set<Integer> objectIds) {
        return findNearestWallObject(objectIds, null);
    }
    // ============================================================
    //  NPCs
    // ============================================================
    public static List<NPC> findNpcs(Client client, Set<Integer> npcIds, Predicate<NPC> filter) {
        List<NPC> foundNpcs = new ArrayList<>();
        List<NPC> clientNpcs = client.getNpcs();
        if (clientNpcs == null) {
            return foundNpcs;
        }

        for (NPC npc : clientNpcs) {
            if (npc != null && npcIds.contains(npc.getId())) {
                // Apply the filter if present
                if (filter == null || filter.test(npc)) {
                    foundNpcs.add(npc);
                }
            }
        }
        return foundNpcs;
    }

    public static List<NPC> findNpcs(Set<Integer> npcIds) {
        return findNpcs(client, npcIds, null);
    }

    public static NPC findNearestNpc(Set<Integer> npcIds, Predicate<NPC> filter) {
        // Reuse the main finder function with filters
        List<NPC> matchingNpcs = findNpcs(client, npcIds, filter);

        if (matchingNpcs.isEmpty() || client.getLocalPlayer() == null) {
            return null;
        }

        LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
        if (playerLocation == null) {
            return null;
        }

        // Find the NPC with the minimum distance to the local player
        return matchingNpcs.stream()
                .min(Comparator.comparingInt(npc -> {
                    LocalPoint npcLoc = npc.getLocalLocation();
                    return npcLoc != null ? npcLoc.distanceTo(playerLocation) : Integer.MAX_VALUE;
                }))
                .orElse(null);
    }

    public static NPC findNearestNpc(Set<Integer> npcIds){
        return findNearestNpc(npcIds, null);
    }

    public static NPC findNearestNpc(int npcId){
        return findNearestNpc(Set.of(npcId), null);
    }
}