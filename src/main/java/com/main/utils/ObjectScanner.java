package com.main.utils;

import com.main.ApexeaConfig;
import com.main.ipc.IpcServer;
import com.main.data.ObjectDto;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.function.IntPredicate;

@Slf4j
public class ObjectScanner {

    private static final int SCENE_SIZE = 104;

    private final Client client;
    private final ApexeaConfig config;
    private final IpcServer ipcServer;
    public final Set<Integer> objectIds = new HashSet<>();

    public ObjectScanner(Client client, IpcServer ipcServer, ApexeaConfig config) {
        this.client = client;
        this.ipcServer = ipcServer;
        this.config = config;
    }

    public enum TargetType { OBJECT, NPC, GROUND_ITEM }

    @lombok.Value
    public static class Target {
        TargetType type;
        String name;
        WorldPoint worldPosition;
        int distance;
        Point canvasPos;
        int id;
        GameObject gameObject;  // OBJECT only
        NPC npc;                // NPC only
        Tile tile;              // GROUND_ITEM only (needed by Clicker)
        TileItem tileItem;      // GROUND_ITEM only
    }

    // ============================================================
    //  GAME OBJECTS
    // ============================================================
    public List<Target> findGameObjects(Set<Integer> ids) {
        return findObjectsMatching(id -> ids == null || ids.isEmpty() || ids.contains(id));
    }

    public Optional<GameObject> findNearestObject(Set<Integer> ids) {
        return findGameObjects(ids).stream().findFirst().map(Target::getGameObject);
    }

    private List<Target> findObjectsMatching(IntPredicate filter) {
        Player player = client.getLocalPlayer();
        if (player == null) return Collections.emptyList();
        WorldPoint playerPos = player.getWorldLocation();
        if (playerPos == null) return Collections.emptyList();
        Scene scene = client.getScene();
        if (scene == null) return Collections.emptyList();

        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        List<Target> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (int x = 0; x < SCENE_SIZE; x++) {
            for (int y = 0; y < SCENE_SIZE; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) continue;
                GameObject[] objects = tile.getGameObjects();
                if (objects == null) continue;

                for (GameObject obj : objects) {
                    if (obj == null) continue;
                    int id = obj.getId();
                    if (id == -1) continue;
                    if (!filter.test(id)) continue;

                    WorldPoint wp = obj.getWorldLocation();

                    long key = ((long) id << 32) ^ ((long) wp.getX() << 16) ^ wp.getY();
                    if (!seen.add(key)) continue;

                    LocalPoint lp = LocalPoint.fromWorld(client, wp);
                    if (lp == null) continue;
                    Point cp = Perspective.localToCanvas(client, lp, plane);
                    if (cp == null) continue;

                    ObjectComposition def = client.getObjectDefinition(id);
                    String name = (def != null && def.getName() != null) ? def.getName() : "Object";

                    results.add(new Target(
                            TargetType.OBJECT, name, wp,
                            playerPos.distanceTo(wp), cp, id,
                            obj, null, null, null
                    ));
                }
            }
        }
        results.sort(Comparator.comparingInt(Target::getDistance));
        return results;
    }

    // ============================================================
    //  NPCs
    // ============================================================
    public List<Target> findNpcs(Set<Integer> ids) {
        return findNpcsMatching(id -> ids == null || ids.isEmpty() || ids.contains(id));
    }

    public Optional<NPC> findNearestNpc(Set<Integer> ids) {
        return findNpcs(ids).stream().findFirst().map(Target::getNpc);
    }

    private List<Target> findNpcsMatching(IntPredicate filter) {
        Player player = client.getLocalPlayer();
        if (player == null) return Collections.emptyList();
        WorldPoint playerPos = player.getWorldLocation();
        if (playerPos == null) return Collections.emptyList();
        List<NPC> npcs = client.getNpcs();
        if (npcs == null) return Collections.emptyList();
        int plane = client.getPlane();

        List<Target> results = new ArrayList<>();
        for (NPC npc : npcs) {
            if (npc == null) continue;
            if (!filter.test(npc.getId())) continue;

            WorldPoint wp = npc.getWorldLocation();
            if (wp == null) continue;
            if (wp.getPlane() != plane) continue; // ignore NPCs on other planes

            LocalPoint lp = npc.getLocalLocation();
            if (lp == null) continue;
            Point cp = Perspective.localToCanvas(client, lp, plane);
            if (cp == null) continue;

            String name = npc.getName() != null ? npc.getName() : "NPC";
            results.add(new Target(
                    TargetType.NPC, name, wp,
                    playerPos.distanceTo(wp), cp, npc.getId(),
                    null, npc, null, null
            ));
        }
        results.sort(Comparator.comparingInt(Target::getDistance));
        return results;
    }

    // ============================================================
    //  GROUND ITEMS
    // ============================================================
    public List<Target> findGroundItems(Set<Integer> ids) {
        return findGroundItemsMatching(id -> ids == null || ids.isEmpty() || ids.contains(id));
    }

    public Optional<Target> findNearestGroundItem(Set<Integer> ids) {
        return findGroundItems(ids).stream().findFirst();
    }

    private List<Target> findGroundItemsMatching(IntPredicate filter) {
        Player player = client.getLocalPlayer();
        if (player == null) return Collections.emptyList();
        WorldPoint playerPos = player.getWorldLocation();
        if (playerPos == null) return Collections.emptyList();
        Scene scene = client.getScene();
        if (scene == null) return Collections.emptyList();

        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        List<Target> results = new ArrayList<>();

        for (int x = 0; x < SCENE_SIZE; x++) {
            for (int y = 0; y < SCENE_SIZE; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) continue;
                List<TileItem> items = tile.getGroundItems();
                if (items == null || items.isEmpty()) continue;

                WorldPoint wp = tile.getWorldLocation();
                if (wp == null) continue;
                LocalPoint lp = tile.getLocalLocation();
                if (lp == null) continue;
                Point cp = Perspective.localToCanvas(client, lp, plane);
                if (cp == null) continue;

                for (TileItem item : items) {
                    if (item == null) continue;
                    if (!filter.test(item.getId())) continue;

                    ItemComposition def = client.getItemDefinition(item.getId());
                    String name = def.getName() != null ? def.getName() : "Item";

                    results.add(new Target(
                            TargetType.GROUND_ITEM, name, wp,
                            playerPos.distanceTo(wp), cp, item.getId(),
                            null, null, tile, item
                    ));
                }
            }
        }
        results.sort(Comparator.comparingInt(Target::getDistance));
        return results;
    }

    // ============================================================
    //  COMBINED
    // ============================================================
    public List<Target> findAll(Set<Integer> objectIds,
                                Set<Integer> npcIds,
                                Set<Integer> itemIds) {
        List<Target> all = new ArrayList<>();
        if (objectIds != null && !objectIds.isEmpty()) all.addAll(findGameObjects(objectIds));
        if (npcIds != null && !npcIds.isEmpty())     all.addAll(findNpcs(npcIds));
        if (itemIds != null && !itemIds.isEmpty())   all.addAll(findGroundItems(itemIds));
        all.sort(Comparator.comparingInt(Target::getDistance));
        return all;
    }

    public List<Target> findAll(Set<Integer> ids) {
        return findAll(ids, ids, ids);
    }

    // ============================================================
    //  PUBLISHERS
    // ============================================================
    public void addObjectIds(Set<Integer> ids){
        if (ids != null) {
            objectIds.addAll(ids);
        }
    }

    public void publishObjects() {
        if (ipcServer == null) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;
        WorldPoint playerPos = player.getWorldLocation();
        if (playerPos == null) return;

        var canvas = client.getCanvas();
        List<ObjectDto> dtos = new ArrayList<>();

        for (var object : findGameObjects(objectIds)) {
            var wp = object.getWorldPosition();
            if (wp == null) continue;

            Point center = Calculate.objectCenter(object.getGameObject());
            if(center == null)
                log.warn("Center is null");

            int screenX = -1, screenY = -1;
            if (canvas != null && center != null) {
                var screen = SwingUtilities.convertPoint(
                        canvas, new java.awt.Point(center.getX(), center.getY()), null);
                screenX = screen.x;
                screenY = screen.y;
            }

            dtos.add(new ObjectDto(
                    object.getName(),
                    object.getId(),
                    wp.getX(), wp.getY(), wp.getPlane(),
                    center != null ? center.getX() : -1,
                    center != null ? center.getY() : -1,
                    screenX, screenY,
                    playerPos.distanceTo(wp)
            ));
        }

        ipcServer.publishObjects(dtos);
    }
}
