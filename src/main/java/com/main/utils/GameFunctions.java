package com.main.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

@Slf4j
public class GameFunctions {
    private GameFunctions(){}

    private static Instant waitingSince = null;
    private static volatile Client client;

    public static void init(Client c)  { client = c; }
    public static void destroy()       { client = null; }

    public static void startWaitTimer(){ waitingSince = Instant.now(); }
    public static void resetTimer() { waitingSince = null; }

    public static boolean ifTimeWaited(Duration duration) {
        if (waitingSince == null) return false;

        boolean elapsed = Duration.between(waitingSince, Instant.now()).compareTo(duration) > 0;
        if (elapsed) waitingSince = null;
        return elapsed;
    }

    public static void click(int leftRight, int x, int y) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) {
            log.warn("Coords outside canvas");
            return;
        }

        // Get the system-wide AWT event queue once.
        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();

        // 1. Mouse Moved (optional, but often needed for hover state)
        eventQueue.postEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                now, 0, x, y, 0, false, MouseEvent.NOBUTTON));

        // 2. Mouse Pressed
        eventQueue.postEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                now, 0, x, y, 1, false, leftRight));

        // 3. Mouse Released
        eventQueue.postEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                now, 0, x, y, 1, false, leftRight));
    }

    public static void menuAction(int objectId, String option){
        client.menuAction(-1, -1, MenuAction.GAME_OBJECT_FIRST_OPTION, objectId, -1, option, "");
    }

    public static void menuClickWidget(WidgetInfo targetWidgetId, MenuAction menuAction, String optionText, String targetText){
        Widget widget = client.getWidget(targetWidgetId);
        client.menuAction(
                0,                        // p0 (child index, 0 for the widget itself)
                widget.getId(),             // p1 (widget ID)
                menuAction,               // action type
                0,                        // id
                -1,                       // itemId (none)
                optionText,               // option text
                targetText                // target text
        );
    }

    public static void walkToTile(WorldPoint targetTile) {
        Player player = client.getLocalPlayer();
        if (player == null) return;
        if (player.getWorldLocation().getPlane() != targetTile.getPlane()) return;

        LocalPoint lp = LocalPoint.fromWorld(client, targetTile);
        if (lp == null) return;

        Point canvasPoint = Perspective.localToCanvas(client, lp, targetTile.getPlane());
        if (canvasPoint == null) return;

        int x = canvasPoint.getX();
        int y = canvasPoint.getY();
        click(MouseEvent.BUTTON1,  x, y);
    }

    public static void clickWidget(Widget widget) {
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
        click(MouseEvent.BUTTON1, x, y);
        log.info("clickWidget: Clicked widget.");
    }

    public static void clickObject(GameObject obj) {
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
                click(MouseEvent.BUTTON1, x, y);
                return;
            }
            log.warn("clickObj: degenerate clickbox for id {}", obj.getId());
        }
    }

    public static void clickNpc(NPC npc) {
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
                click(MouseEvent.BUTTON1, x, y);
                return;
            }
            log.warn("clickNpc: degenerate hull for id {}", npc.getId());
        }
    }

    public static void clickGroundItem(Tile tile, TileItem item) {
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

    public static int findSlotWithItem(int itemId) {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return -1;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getId() == itemId) {
                log.info("Found slot with item ID {} at slot {}", itemId, i+1);
                return i+1;
            }
        }

        log.warn("Found no slot with item ID {}", itemId);
        return -1;
    }

    public static void clickInventorySlot(int slot) {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null) {
            log.warn("Inventory widget not found.");
            return;
        }

        Rectangle invBounds = inventoryWidget.getBounds();
        if (invBounds == null || invBounds.width <= 0 || invBounds.height <= 0) {
            log.warn("Inventory widget has invalid bounds.");
            return;
        }

        // Slot layout constants (mirrors InventoryTilesOverlay)
        final int slotWidth  = 36;
        final int slotHeight = 32;
        final int padX       = 6;
        final int padY       = 4;
        final int columns    = 4;
        final int offsetX    = 16;
        final int offsetY    = 8;

        // Row/column of the target slot
        int row = slot / columns;
        int col = slot % columns;

        // Top-left corner of the slot
        int slotX = invBounds.x + offsetX + (slotWidth + padX) * col;
        int slotY = invBounds.y + offsetY + (slotHeight + padY) * row;

        // Center of the slot
        int centerX = slotX + slotWidth / 2;
        int centerY = slotY + slotHeight / 2;

        GameFunctions.click(MouseEvent.BUTTON1, centerX, centerY);
        log.info("Clicked inventory slot {} at ({}, {}).", slot, centerX, centerY);
    }

    public static boolean login(String username, String password) {
        GameState state = client.getGameState();

        if (state == GameState.LOGIN_SCREEN) {
            client.setUsername(username);
            client.setPassword(password);

            // Click the Login button. The login interface is usually group 378.
            // We locate the button by its action text to be resilient to ID changes.
            Widget loginButton = client.getWidget(601, 19);
            if (loginButton != null) {
                clickWidget(loginButton);
                return true;
            }
            log.warn("login: Login button not found");
            return false;
        }
        return false;
    }

    public static void logout() {
        if (client.getGameState() != GameState.LOGGED_IN) return;

        Widget logoutButton1 = client.getWidget(548, 297);

        if(logoutButton1 == null)
            log.error("Logout button 1 is NULL");
        else
            clickWidget(logoutButton1);
    }

    public static void keyPress(int keyCode) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();

        eventQueue.postEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                now, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
        eventQueue.postEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED,
                now, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    public static void typeText(String text){
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long delay = 0;

        for (char c : text.toCharArray()) {
            int keyCode = charToKeyCode(c);
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                delay += 50;
                continue;
            }
            scheduler.schedule(() -> keyPress(keyCode), delay, TimeUnit.MILLISECONDS);
            delay += 50;
        }

        // Shut down after all keys are dispatched
        scheduler.schedule(() -> scheduler.shutdown(), delay + 100, TimeUnit.MILLISECONDS);
    }

    public static boolean isInventoryFull() {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        return inv != null && inv.count() == inv.size();
    }
    public static int countInventoryItems(){
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        assert inv != null;
        return inv.count();
    }

    public static int countItem(int itemId) {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return 0;
        int total = 0;
        for (Item item : inv.getItems()) {
            if (item != null && item.getId() == itemId) total += item.getQuantity();
        }
        return total;
    }

    public static boolean isBankOpen() {
        Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return w != null && !w.isHidden();
    }

    public static void depositInventory() {
        Widget deposit = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (deposit == null || deposit.isHidden()) {
            log.warn("Deposit-inventory widget not found");
            return;
        }
        clickWidget(deposit);
    }

    public static void sendCommand(String command) {
        if (command == null || !command.startsWith("::")) {
            log.warn("sendCommand: command must start with '::'");
            return;
        }

        // 1. Set the chatbox text variable to the full command string
        client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, command);

        // 2. Run the chatbox input script with a default chat type (0 = public chat)
        client.runScript(ScriptID.CHAT_SEND, 0, command);

        log.info("Sent command: {}", command);
    }

    private static final Map<Character, Integer> SPECIAL_KEYS;
    static {
        Map<Character, Integer> m = new HashMap<>();
        m.put(' ', KeyEvent.VK_SPACE);
        m.put('\n', KeyEvent.VK_ENTER);
        m.put('-', KeyEvent.VK_MINUS);
        m.put('=', KeyEvent.VK_EQUALS);
        m.put('[', KeyEvent.VK_OPEN_BRACKET);
        m.put(']', KeyEvent.VK_CLOSE_BRACKET);
        m.put('\\', KeyEvent.VK_BACK_SLASH);
        m.put(';', KeyEvent.VK_SEMICOLON);
        m.put('\'', KeyEvent.VK_QUOTE);
        m.put('/', KeyEvent.VK_SLASH);
        m.put(',', KeyEvent.VK_COMMA);
        m.put('.', KeyEvent.VK_PERIOD);
        m.put('`', KeyEvent.VK_BACK_QUOTE);
        SPECIAL_KEYS = Collections.unmodifiableMap(m);
    }

    private static int charToKeyCode(char c) {
        if (c >= 'a' && c <= 'z') return KeyEvent.VK_A + (c - 'a');
        if (c >= 'A' && c <= 'Z') return KeyEvent.VK_A + (c - 'A');
        if (c >= '0' && c <= '9') return KeyEvent.VK_0 + (c - '0');
        return SPECIAL_KEYS.getOrDefault(c, KeyEvent.VK_UNDEFINED);
    }
}