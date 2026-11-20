package com.main.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
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
    public static int skipTicks = 0;
    private static volatile Client client;
    private static final List<Point> inventoryCoords = new ArrayList<>();
    private static final Map<String, Point> buttonCoords = new LinkedHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void init(Client c)  {
        client = c;
        buttonCoords.put("protect melee", new Point(2155, 1245));
        buttonCoords.put("protect magic", new Point(2115, 1245));
        buttonCoords.put("protect ranged",new Point(2080, 1245));
        buttonCoords.put("humidify",new Point(2105, 1155));
        buttonCoords.put("prayers",new Point(1950, 1395));
        buttonCoords.put("inventory",new Point(1885, 1395));
        buttonCoords.put("spellbook",new Point(1980, 1395));

        for (int y = 1135; y <= 1345; y += 35) {
            for (int x = 2060; x <= 2180; x += 40) {
                inventoryCoords.add(new Point(x, y));
            }
        }
    }
    public static void destroy() {
        client = null;
        buttonCoords.clear();
    }

    public static void startTimer(){ waitingSince = Instant.now(); }
    public static void resetTimer() { waitingSince = null; }

    public static boolean ifTimeWaited(Duration duration) {
        if (waitingSince == null) return false;

        boolean elapsed = Duration.between(waitingSince, Instant.now()).compareTo(duration) > 0;
        if (elapsed) waitingSince = null;
        return elapsed;
    }

    public static void skipTicks(int ticks) { skipTicks = ticks; }

    public static Point getButton(String name) {
        return buttonCoords.get(name);
    }

    public static Point getInventorySlot(int slot) {
        return inventoryCoords.get(slot);
    }

    public static void click(int leftRight, int x, int y) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) {
            log.warn("Coords outside canvas");
            return;
        }

        ClickTracker.record(x, y, leftRight);

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, x, y, 0, false));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, leftRight));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, x, y, 1, false, leftRight));
    }

    public static boolean clickMultiple(int leftRight, int[][] coordinates, long delayMs) {
        if (coordinates == null || coordinates.length == 0) return false;

        for (int i = 0; i < coordinates.length; i++) {
            final int x = coordinates[i][0];
            final int y = coordinates[i][1];
            final long delay = i * delayMs;
            scheduler.schedule(() -> click(leftRight, x, y), delay, TimeUnit.MILLISECONDS);
        }

        return true;
    }

    public static boolean clickMultiple(int[][] coordinates, long delayMs) {
        clickMultiple(MouseEvent.BUTTON1, coordinates, delayMs);
        return true;
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

    public static void clickWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            log.warn("clickWidget: widget is null or hidden");
            return;
        }

        Rectangle bounds = getCanvasBounds(widget);
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            log.warn("clickWidget: widget has degenerate canvas bounds");
            return;
        }

        log.info("Deposit widget raw bounds: {}", widget.getBounds());
        log.info("Deposit widget canvas bounds: {}", bounds);

        int x = bounds.x + bounds.width / 2;
        int y = bounds.y + bounds.height / 2;
        click(MouseEvent.BUTTON1, x, y);
        log.info("clickWidget: clicked at ({}, {}) in canvas space", x, y);
    }

    public static boolean clickObject(TileObject obj) {
        if (obj == null) {
            log.warn("clickObject: obj is null");
            return false;
        }

        Shape hull = obj.getClickbox();
        if (hull == null) {
            log.warn("clickObject: no clickbox for id {} (not rendered)", obj.getId());
            return false;
        }

        Rectangle b = hull.getBounds();
        if (b.width <= 0 || b.height <= 0) {
            log.warn("clickObject: degenerate clickbox for id {} ({}x{})", obj.getId(), b.width, b.height);
            return false;
        }

        int x = b.x + b.width / 2;
        int y = b.y + b.height / 2;

        Canvas canvas = client.getCanvas();
        if (canvas == null) return false;

        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        if (x < 0 || y < 0 || x >= cw || y >= ch) {
            log.warn("clickObject: id {} off-canvas at ({},{}) canvas {}x{} clickbox ({},{},{},{})",
                    obj.getId(), x, y, cw, ch, b.x, b.y, b.width, b.height);
            return false;
        }

        click(MouseEvent.BUTTON1, x, y);
        return true;
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

        Point canvasPoint = Perspective.localToCanvas(client, lp, client.getPlane());
        if (canvasPoint == null) {
            log.warn("clickGroundItem: could not compute canvas point for tile");
            return;
        }

        click(MouseEvent.BUTTON1, canvasPoint.getX(), canvasPoint.getY());
        log.info("Clicked ground item {} at ({}, {})", item.getId(), canvasPoint.getX(), canvasPoint.getY());
    }

    public static boolean clickInventoryItem(int itemId, int leftRight) {
        // 1. Find the slot index for the item ID
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) {
            log.warn("Inventory container is null.");
            return false;
        }

        int targetSlot = -1;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getId() == itemId) {
                targetSlot = i;
                break;
            }
        }
        if (targetSlot == -1) {
            log.warn("Item ID {} not found in inventory.", itemId);
            return false;
        }

        // 2. Get the coordinates of the inventory slot
        Point slotCenter = inventoryCoords.get(targetSlot);

        // 3. Click the inventory slot
        assert slotCenter != null;
        click(leftRight, slotCenter.getX(), slotCenter.getY());
        log.info("Clicked item {} (slot {}) at ({}, {}).", itemId, targetSlot, slotCenter.getX(), slotCenter.getY());
        return true;
    }

    public static boolean clickInventoryItem(int itemId) {
        return clickInventoryItem(itemId, MouseEvent.BUTTON1);
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

    public static void keyHold(int keyCode) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();

        eventQueue.postEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                now, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    public static void keyRelease(int keyCode) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();

        eventQueue.postEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED,
                now, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    public static void keyPressDelayed(int[] keyCodes, long delayMs) {
        for (int i = 0; i < keyCodes.length; i++) {
            final int key = keyCodes[i];
            final long delay = i * delayMs;
            scheduler.schedule(() -> keyPress(key), delay, TimeUnit.MILLISECONDS);
        }
    }

    public static void typeText(String text){
        Canvas canvas = client.getCanvas();
        if (canvas == null) return;

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

        click(MouseEvent.BUTTON1, 1300, 1000);
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

    public static Rectangle getCanvasBounds(Widget widget) {
        if (widget == null) return null;

        Rectangle bounds = widget.getBounds();
        if (bounds == null) return null;

        int x = bounds.x;
        int y = bounds.y;
        Widget parent = widget.getParent();

        while (parent != null)
        {
            Rectangle pb = parent.getBounds();
            if (pb != null)
            {
                x += pb.x;
                y += pb.y;
            }
            parent = parent.getParent();
        }

        return new Rectangle(x, y, bounds.width, bounds.height);
    }

    public static Point getBankItem(int itemId)
    {
        Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (bankItems == null || bankItems.isHidden())
        {
            return null;
        }

        Widget[] children = bankItems.getDynamicChildren();
        for (Widget child : children)
        {
            if (child == null || child.isHidden())
            {
                continue;
            }

            if (child.getItemId() == itemId)
            {
                Point pos = child.getCanvasLocation();
                return new Point(
                        pos.getX() + child.getWidth() / 2,
                        pos.getY() + child.getHeight() / 2
                );
            }
        }

        return null; // item not in bank
    }

    public static boolean stillExists(TileObject obj) {
        if (obj.getWorldView() == null) return false;

        LocalPoint lp = obj.getLocalLocation();
        int sceneX = lp.getSceneX();
        int sceneY = lp.getSceneY();
        int plane = obj.getPlane();

        Scene scene = obj.getWorldView().getScene();
        if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) return false;

        Tile tile = scene.getTiles()[plane][sceneX][sceneY];
        if (tile == null) return false;

        if (obj instanceof GameObject) {
            for (GameObject go : tile.getGameObjects()) {
                if (go != null && go.getId() == obj.getId()) return true;
            }
        } else if (obj instanceof WallObject) {
            WallObject wall = tile.getWallObject();
            return wall != null && wall.getId() == obj.getId();
        } else if (obj instanceof GroundObject) {
            GroundObject ground = tile.getGroundObject();
            return ground != null && ground.getId() == obj.getId();
        }

        return false;
    }

    public static boolean isPlayerAtCoordinates(WorldPoint targetPoint){
        Player player = client.getLocalPlayer();
        WorldPoint playerPosition = player.getWorldLocation();
        return playerPosition.equals(targetPoint);
    }

    public static final class Step {
        final Runnable action;
        final long delayMs;

        private Step(long delayMs, Runnable action) {
            this.delayMs = delayMs;
            this.action = action;
        }
    }

    public static Step step(long delayMs, Runnable action) {
        return new Step(delayMs, action);
    }

    public static void runFunctions(long delayMs, Runnable... actions) {
        if (actions == null) return;
        for (int i = 0; i < actions.length; i++) {
            final Runnable action = actions[i];
            scheduler.schedule(action, i * delayMs, TimeUnit.MILLISECONDS);
        }
    }

    public static void runSequence(Step... steps) {
        if (steps == null) return;
        long cumulative = 0;
        for (Step s : steps) {
            cumulative += s.delayMs;
            final Runnable action = s.action;
            scheduler.schedule(action, cumulative, TimeUnit.MILLISECONDS);
        }
    }
}