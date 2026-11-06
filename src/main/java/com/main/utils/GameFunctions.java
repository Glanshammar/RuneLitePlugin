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

    public static void click(Client client, int leftRight, int x, int y) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) {
            log.warn("Coords outside canvas");
            return;
        }

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, leftRight)); //1 = left, 3 = right
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, x, y, 1, false, leftRight));
    }

    public static boolean selectMenuOption(Client client, String optionText) {
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null || entries.length == 0) {
            log.warn("selectMenuOption: menu is empty");
            return false;
        }

        int menuX = client.getMenuX();
        int menuY = client.getMenuY();
        log.info("menu at ({},{}) with {} entries", menuX, menuY, entries.length);

        // Locate the target and its row from the top.
        // entries[length-1] is the TOP visible entry; entries[0] is the BOTTOM.
        // If this proves wrong on your client build, the "menu order" log below
        // will show it — just flip the formula.
        for (int i = 0; i < entries.length; i++) {
            MenuEntry e = entries[i];
            log.info("  [{}] top-row={} option='{}' target='{}'",
                    i, entries.length - 1 - i, e.getOption(), e.getTarget());

            if (optionText.equalsIgnoreCase(e.getOption())) {
                int rowFromTop = entries.length - 1 - i;
                int clickX = menuX + 40;                       // left-ish inside the menu
                int clickY = menuY + rowFromTop * 15 + 7;      // 15px row height, 7 to center

                log.info("selectMenuOption: clicking '{}' at ({},{})",
                        optionText, clickX, clickY);

                Canvas canvas = client.getCanvas();
                long now = System.currentTimeMillis();
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                        now, 0, clickX, clickY, 0, false, MouseEvent.NOBUTTON));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                        now, 0, clickX, clickY, 1, false, MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                        now, 0, clickX, clickY, 1, false, MouseEvent.BUTTON1));
                return true;
            }
        }
        log.warn("selectMenuOption: '{}' not found", optionText);
        return false;
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
        click(client, MouseEvent.BUTTON1,  x, y);
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
        click(client, MouseEvent.BUTTON1, x, y);
    }

    public static void clickObject(Client client, GameObject obj) {
        if (obj == null) {
            log.warn("clickObject: obj is null");
            return;
        }

        int x, y;
        Shape hull = obj.getClickbox();
        if (hull != null) {
            Rectangle b = hull.getBounds();
            if (b.width > 0 && b.height > 0) {
                x = b.x + b.width / 2;
                y = b.y + b.height / 2;
                click(client, MouseEvent.BUTTON1, x, y);
                return;
            }
            log.warn("clickObj: degenerate clickbox for id {}", obj.getId());
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
                click(client, MouseEvent.BUTTON1, x, y);
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

        Widget logoutWidget = client.getWidget(InterfaceID.LOGOUT);
        Widget logoutWidget2 = client.getWidget(InterfaceID.LOGOUT, 69);
        if (logoutWidget == null) { log.warn("logout widget 182 is NULL"); return; }
        if (logoutWidget2 == null) { log.warn("logout widget 182:69 is NULL"); return; }

        clickWidget(client, logoutWidget);
        clickWidget(client, logoutWidget2);
    }

    public static void pressKey(Client client, int keyCode) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;
        long now = System.currentTimeMillis();
        canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                now, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
        canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED,
                now, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    public static boolean isInventoryFull(Client client) {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        return inv != null && inv.count() == inv.size();
    }
    public static int hasInventoryAmount(Client client){
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        assert inv != null;
        return inv.count();
    }

    public static boolean isBankOpen(Client client) {
        Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return w != null && !w.isHidden();
    }

    public static void depositInventory(Client client) {
        Widget deposit = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (deposit == null || deposit.isHidden()) {
            log.warn("Deposit-inventory widget not found");
            return;
        }
        GameFunctions.clickWidget(client, deposit);
    }
}