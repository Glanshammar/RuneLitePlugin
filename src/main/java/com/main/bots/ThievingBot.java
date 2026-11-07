package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
public class ThievingBot {
    private enum State { IDLE, STEALING, SELLING }

    private static final Duration ACTION_COOLDOWN = Duration.ofMillis(300);
    private static final Duration STEAL_TIMEOUT = Duration.ofMillis(50);
    private static final Duration SHOP_OPEN_TIMEOUT = Duration.ofSeconds(15);

    private static final int STALL = 4878; // Scimitar Stall
    private static final int SELLER_NPC = 3533; // Ali
    private static final int SELL_ITEM = 1331; // Adamant Scimitar

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private State state = State.STEALING;
    private Instant lastActionAt = Instant.EPOCH;
    private boolean running = false;

    // steal tracking
    private int lastStolenCount = 0;
    private Instant stealStartDeadline = Instant.EPOCH;

    // shop tracking
    private Instant shopOpenDeadline = Instant.EPOCH;

    public ThievingBot(Client client, ApexeaConfig config, ObjectScanner objectScanner){
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start() {
        log.info("Thieving bot starting");
        state = State.STEALING;
        lastActionAt = Instant.EPOCH;
        lastStolenCount = 0;
        objectScanner.addObjectIds(Set.of(STALL));
    }

    public void stop() {
        log.info("Thieving bot stopping");
        state = State.IDLE;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        // if (AntiBotDetector.isDetected()) return;

        boolean shouldRun = config.botThieveStalls();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        if(state == State.STEALING){
            tickStealing(player);
        } else if (state == State.SELLING) {
            tickSelling(player);
        }
    }

    // ============================================================
    //  IDLE — click stall, or transition to SELLING when full
    // ============================================================
    private void tickStealing(Player player) {
        if (Instant.now().isBefore(lastActionAt.plus(ACTION_COOLDOWN))) return;

        if (GameFunctions.isInventoryFull(client)) {
            if (GameFunctions.countItem(client, SELL_ITEM) == 0) {
                log.error("Inventory full but no adamant scimitars — stopping");
                stop();
                running = false;
                return;
            }

            setState(State.SELLING);
            shopOpenDeadline = Instant.EPOCH;
            return;
        }

        ObjectScanner.Target stall = findStall(player);
        if (stall == null) {
            log.debug("Stall {} not visible", STALL);
            return;
        }

        log.info("Stealing from stall at {}", stall.getWorldPosition());
        GameFunctions.clickObject(client, stall.getGameObject());

        lastStolenCount = GameFunctions.countItem(client, SELL_ITEM);
        stealStartDeadline = Instant.now().plus(STEAL_TIMEOUT);
        setState(State.STEALING);
    }

    // ============================================================
    //  SELLING — click NPC once, wait for shop, sell one per tick
    // ============================================================
    private void tickSelling(Player player) {
        if (!isShopOpen()) {
            if (shopOpenDeadline.equals(Instant.EPOCH)) {
                NPC npc = findNpc(SELLER_NPC, player);
                if (npc == null) {
                    log.debug("Seller NPC {} not visible", SELLER_NPC);
                    return;
                }
                log.info("Clicking seller NPC");
                GameFunctions.clickNpc(client, npc);
                shopOpenDeadline = Instant.now().plus(SHOP_OPEN_TIMEOUT);
                lastActionAt = Instant.now();
                return;
            }
            if (Instant.now().isAfter(shopOpenDeadline)) {
                log.warn("Shop never opened — retrying");
            }
            return;
        }

        int slot = findSlotWithItem(SELL_ITEM);
        if (slot < 0) {
            log.info("All scimitars sold — closing shop");
            GameFunctions.keyPress(client, KeyEvent.VK_ESCAPE);
            lastActionAt = Instant.now();
            setState(State.STEALING);
            return;
        }

        log.debug("Selling scimitar in slot {}", slot);
        clickInventorySlot(slot);
        lastActionAt = Instant.now();
    }

    // ============================================================
    //  Helpers
    // ============================================================
    private void setState(State s) {
        if (state != s) log.debug("State {} → {}", state, s);
        state = s;
        lastActionAt = Instant.now();
    }

    private ObjectScanner.Target findStall(Player player) {
        int plane = player.getWorldLocation().getPlane();
        List<ObjectScanner.Target> stalls = objectScanner.findGameObjects(Set.of(STALL));
        for (ObjectScanner.Target t : stalls) {
            if (t.getWorldPosition() != null
                    && t.getWorldPosition().getPlane() == plane
                    && t.getGameObject() != null) {
                return t;
            }
        }
        return null;
    }

    private NPC findNpc(int npcId, Player player) {
        int plane = player.getWorldLocation().getPlane();
        NPC best = null;
        int bestDist = Integer.MAX_VALUE;
        for (NPC npc : client.getNpcs()) {
            if (npc == null || npc.getId() != npcId) continue;
            WorldPoint wp = npc.getWorldLocation();
            if (wp == null || wp.getPlane() != plane) continue;
            int d = player.getWorldLocation().distanceTo(wp);
            if (d < bestDist) { bestDist = d; best = npc; }
        }
        return best;
    }

    private int findSlotWithItem(int itemId) {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return -1;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getId() == itemId) return i;
        }
        return -1;
    }

    private void clickInventorySlot(int slot) {
        Widget inv = client.getWidget(InterfaceID.Inventory.ITEMS);
        if (inv == null || inv.isHidden()) return;
        Widget[] children = inv.getChildren();
        if (children == null || slot >= children.length) return;
        Widget slotWidget = children[slot];
        if (slotWidget == null || slotWidget.isHidden()) return;
        GameFunctions.clickWidget(client, slotWidget);
    }

    private boolean isShopOpen() {
        Widget inv = client.getWidget(InterfaceID.Inventory.ITEMS);
        if (inv == null || inv.isHidden()) return false;
        Widget[] children = inv.getChildren();
        if (children == null) return false;
        for (Widget slot : children) {
            if (slot == null) continue;
            String[] actions = slot.getActions();
            if (actions == null) continue;
            for (String a : actions) {
                if (a != null && a.toLowerCase().contains("sell")) return true;
            }
        }
        return false;
    }
}
