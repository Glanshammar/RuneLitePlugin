package com.main.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

@Slf4j
public class GameFunctions {
    private GameFunctions(){}

    public static void click(Client client, int x, int y){
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        // Don't dispatch clicks outside the canvas — they'd do nothing anyway
        if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) {
            log.warn("Coords outside canvas");
            return;
        }
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, x, y, 0, false));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    public static void walkToTile(Client client, WorldPoint targetTile) {
        Player player = client.getLocalPlayer();
        if (player == null) return;
        if (player.getWorldLocation().getPlane() != targetTile.getPlane()) return;

        LocalPoint lp = LocalPoint.fromWorld(client, targetTile);
        if (lp == null) return;

        Point canvasPoint = Perspective.localToCanvas(client, lp, targetTile.getPlane());
        if (canvasPoint == null) return;

        int x = canvasPoint.getX();
        int y = canvasPoint.getY();
        click(client, x, y);
    }

    public static void clickWidget(Client client, Widget widget) {
        if (widget == null || widget.isHidden()) {
            log.warn("clickWidget: widget is null or hidden");
            return;
        }

        Rectangle bounds = widget.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            log.warn("clickWidget: widget has degenerate bounds");
            return;
        }

        int x = bounds.x + bounds.width / 2;
        int y = bounds.y + bounds.height / 2;
        click(client, x, y);
    }

    public static void clickObject(Client client, GameObject obj) {
        if (obj == null) {
            log.warn("clickObject: obj is null");
            return;
        }

        int x, y;

        Shape clickbox = obj.getClickbox();
        if (clickbox != null) {
            Rectangle b = clickbox.getBounds();
            if (b.width > 0 && b.height > 0) {
                x = b.x + b.width / 2;
                y = b.y + b.height / 2;
                click(client, x, y);
                return;
            }
            log.warn("clickObject: degenerate clickbox for id {}", obj.getId());
        }
    }

    public static void clickNpc(Client client, NPC npc) {
        if (npc == null) {
            log.warn("clickNpc: npc is null");
            return;
        }

        int x, y;

        Shape hull = npc.getConvexHull();
        if (hull != null) {
            Rectangle b = hull.getBounds();
            if (b.width > 0 && b.height > 0) {
                x = b.x + b.width / 2;
                y = b.y + b.height / 2;
                click(client, x, y);
                return;
            }
            log.warn("clickNpc: degenerate hull for id {}", npc.getId());
        }
    }

    public static void clickGroundItem(Client client, Tile tile, TileItem item) {
        if (tile == null || item == null) return;

        LocalPoint lp = tile.getLocalLocation();
        if (lp == null) return;

        ItemComposition def = client.getItemDefinition(item.getId());
        String name = def.getName() != null ? def.getName() : "Item";

        client.menuAction(
                lp.getSceneX(),                     // param0 = scene X
                lp.getSceneY(),                     // param1 = scene Y
                MenuAction.GROUND_ITEM_FIRST_OPTION,
                item.getId(),                       // identifier = item ID
                item.getId(),                       // itemId = item ID
                "Take",                             // option
                name                                // target = item name
        );
    }

    public static void logoutNow(Client client) {
        log.info("logoutNow called, state={}", client.getGameState());
        if (client.getGameState() != GameState.LOGGED_IN) return;

        Widget w = client.getWidget(InterfaceID.LOGOUT, 69);
        if (w == null) { log.warn("logout widget 182:69 is NULL"); return; }
        if (w.isHidden()) { log.warn("logout widget is hidden"); return; }

        String[] actions = w.getActions();
        log.info("logout widget actions={}", java.util.Arrays.toString(actions));
        if (actions == null) return;

        for (int i = 0; i < actions.length; i++) {
            if ("Logout".equals(actions[i])) {
                log.info("firing logout CC_OP idx={}", i + 1);
                client.menuAction(w.getId(), -1, MenuAction.CC_OP, i + 1, -1, "Logout", "");
                return;
            }
        }
        log.warn("No 'Logout' action found on widget");
    }

    public static void pressEscape(Client client) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;
        long now = System.currentTimeMillis();
        canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED,
                now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
    }
}