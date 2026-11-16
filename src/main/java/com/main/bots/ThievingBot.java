package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ThievingBot {
    private enum State { IDLE, STEALING, SELLING, BOTCHECK, SELL_LOOT, RIGHT_CLICK_SELL, CLICK_SELL, WAITING, BANKING }
    private static final Duration SHOP_OPEN_TIMEOUT = Duration.ofSeconds(15);

    private static final int STALL = 4878; // Scimitar Stall
    private static final int ALI = 3533; // Ali
    private static final int ADAMANT_SCIMMY = 1331; // Adamant Scimitar
    private static final int RUBY_RING = 1641; // Ruby Ring
    private static final int BANK_CHEST = 12309; // Ruby Ring
    private boolean skipNextTick = false;

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private State state = State.STEALING;
    private boolean running = false;


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
    }

    public void stop() {
        log.info("Thieving bot stopping");
        state = State.IDLE;
    }

    @Subscribe
    private void onAnimationChanged(AnimationChanged event)
    {
        if (event.getActor() != client.getLocalPlayer()) return;
        if (event.getActor().getAnimation() == -1) skipNextTick = true;
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

        if (state.equals(State.SELLING) || state.equals(State.WAITING) || state.equals(State.CLICK_SELL))
            tickSelling();

        if (skipNextTick) {
            skipNextTick = false;
            return;
        }
        if (player.getAnimation() != -1) return;

        if(state == State.STEALING)
            tickStealing(player);
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
    private void tickSelling() {
        if (state.equals(State.SELLING)){
            Optional<NPC> npc = objectScanner.findNearestNpc(Set.of(ALI));
            if (npc.isEmpty()) {
                log.debug("Seller NPC {} not visible", ALI);
                return;
            }
            GameFunctions.clickNpc(npc.get());
            state = State.WAITING;
            log.info("Clicked Ali");
        }

        if (GameFunctions.countItem(ADAMANT_SCIMMY) <= 0) {
            log.info("All scimitars sold - closing shop");
            GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
            setState(State.STEALING);
            return;
        }

        if (state.equals(State.WAITING)){
            if (!GameFunctions.isShopOpen()) {
                log.warn("Shop is not open.");
                return;
            }

            log.info("Shop opened.");
            GameFunctions.clickInventoryItem(ADAMANT_SCIMMY, MouseEvent.BUTTON3);
            state = State.CLICK_SELL;
            log.info("Right clicking adamant scimitar");
            return;
        }

        if (state.equals(State.CLICK_SELL)){
            Point pos = client.getMouseCanvasPosition();
            GameFunctions.click(MouseEvent.BUTTON1, pos.getX(), pos.getY() + (15*5));
        }
    }

    // ============================================================
    //  Helpers
    // ============================================================
    private void setState(State s) {
        if (state != s) log.debug("State {} → {}", state, s);
        state = s;
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
}
