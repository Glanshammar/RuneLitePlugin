package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import com.main.utils.Waiter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
public class ThievingBot {
    private enum State { IDLE, STEALING, SELLING, BOTCHECK }
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
    private int idleTicks;
    private boolean waitingForDetection = false;

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
        idleTicks = 0;
        objectScanner.addObjectIds(Set.of(STALL));
    }

    public void stop() {
        log.info("Thieving bot stopping");
        state = State.IDLE;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (AntiBotDetector.isDetected()) return;

        boolean shouldRun = config.botThieveStalls();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        if (player.getAnimation() != -1) { idleTicks = 0; } // Is active
        if (player.getAnimation() == -1) { idleTicks++; } // Is not active
        if (idleTicks >= 8) { state = State.BOTCHECK; }

        if(state == State.STEALING){
            tickStealing(player);
        } else if (state == State.SELLING) {
            tickSelling(player);
        } else if (state == State.BOTCHECK) {
            tickBotcheck(player);
        }
    }

    private void tickBotcheck(Player player){
        if (AntiBotDetector.isDetected()) return;
        if (idleTicks <= 14) return;

        if (waitingForDetection) {
            if (AntiBotDetector.isDetected()) {
                waitingForDetection = false;
            }
            return; // do nothing else this tick
        }

        Widget inputWidget = client.getWidget(WidgetInfo.CHATBOX_TITLE);

        if (inputWidget == null) {
            ObjectScanner.Target stall = findStall(player);
            if (stall == null) return;
            GameFunctions.clickObject(stall.getGameObject());
        }
    }

    // ============================================================
    //  IDLE — click stall, or transition to SELLING when full
    // ============================================================
    private void tickStealing(Player player) {
        if (GameFunctions.isInventoryFull()) {
            setState(State.SELLING);
            shopOpenDeadline = Instant.EPOCH;
            return;
        }

        ObjectScanner.Target stall = findStall(player);
        if (stall == null) return;
        GameFunctions.clickObject(stall.getGameObject());
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
                GameFunctions.clickNpc(npc);
                shopOpenDeadline = Instant.now().plus(SHOP_OPEN_TIMEOUT);
                lastActionAt = Instant.now();
                return;
            }
            if (Instant.now().isAfter(shopOpenDeadline)) {
                log.warn("Shop never opened — retrying");
            }
            return;
        }

        int slot = GameFunctions.findSlotWithItem(SELL_ITEM);
        log.info("Scimitar slot: {}", slot);
        if (slot < 0) {
            log.info("All scimitars sold — closing shop");
            GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
            lastActionAt = Instant.now();
            setState(State.STEALING);
            return;
        }

        log.debug("Selling scimitar in slot {}", slot);
        GameFunctions.clickInventorySlot(slot);
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
