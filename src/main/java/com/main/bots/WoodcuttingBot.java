package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class WoodcuttingBot {

    private enum State { IDLE, CHOPPING, WALKING_TO_BANK, BANKING }

    private static final Duration CHOP_START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BANK_OPEN_TIMEOUT = Duration.ofSeconds(10);

    // --- priority trees, in order (worldX, worldY) ---
    private static final List<int[]> PRIORITY_TREES = List.of(
            new int[]{2844, 2960},
            new int[]{2841, 2960},
            new int[]{2844, 2966},
            new int[]{2838, 2960}
    );

    private static final int BANK_CHEST = ObjectID.BANK_CHEST_14886;
    private static final int MAGIC_TREE = ObjectID.MAGIC_TREE_10834;

    private final Client client;
    private final ApexeaConfig config;

    private State state = State.IDLE;

    // chop tracking
    private boolean choppingStarted = false;
    private Instant chopStartDeadline = Instant.EPOCH;

    // bank tracking
    private Instant bankOpenDeadline = Instant.EPOCH;
    private boolean depositClicked = false;

    public WoodcuttingBot(Client client, ApexeaConfig config) {
        this.client = client;
        this.config = config;
    }

    public void start() {
        log.info("Woodcutting bot starting.");
        state = State.IDLE;
        choppingStarted = false;
        depositClicked = false;
    }

    public void stop() {
        log.info("Woodcutting bot stopping.");
        state = State.IDLE;
        choppingStarted = false;
        depositClicked = false;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (AntiBotDetector.isDetected()) return;

        boolean running = config.botWoodcutting();
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        switch (state) {
            case IDLE:             tickIdle(player); break;
            case CHOPPING:         tickChopping(player); break;
            case WALKING_TO_BANK:  tickWalkingToBank(); break;
            case BANKING:          tickBanking(); break;
        }
    }

    // ============================================================
    //  IDLE — pick and click a priority tree
    // ============================================================
    private void tickIdle(Player player) {
        if (GameFunctions.isInventoryFull()) {
            log.info("Inventory full. Going to bank.");
            setState(State.WALKING_TO_BANK);
            return;
        }

        GameObject tree = pickTree(player);
        if (tree == null) {
            return;
        }

        GameFunctions.clickObject(tree);

        choppingStarted = false;
        chopStartDeadline = Instant.now().plus(CHOP_START_TIMEOUT);
        setState(State.CHOPPING);
        log.info("Found tree and started chopping.");
    }

    // ============================================================
    //  CHOPPING — wait for the tree to fall or inventory to fill
    // ============================================================
    private void tickChopping(Player player) {
        if (GameFunctions.isInventoryFull()) {
            setState(State.WALKING_TO_BANK);
            return;
        }

        if (isPlayerWoodcutting(player)) {
            choppingStarted = true;
            return;
        }

        if (!choppingStarted) {
            if (Instant.now().isAfter(chopStartDeadline)) {
                log.warn("Chop never started. Retrying.");
                setState(State.IDLE);
            }
            return;
        }

        setState(State.IDLE);
    }

    // ============================================================
    //  WALKING_TO_BANK — click the bank chest
    // ============================================================
    private void tickWalkingToBank() {
        if (!GameFunctions.isInventoryFull()) {
            log.info("Inventory not full. Resuming");
            setState(State.IDLE);
            return;
        }

        GameObject bank = ObjectScanner.findNearestGameObject(BANK_CHEST);
        if (bank == null) {
            log.debug("Bank chest not visible.");
            return;
        }

        log.info("Clicking bank chest.");
        GameFunctions.clickObject(bank);

        depositClicked = false;
        bankOpenDeadline = Instant.now().plus(BANK_OPEN_TIMEOUT);
        setState(State.BANKING);
    }

    // ============================================================
    //  BANKING — deposit, then close and resume
    // ============================================================
    private void tickBanking() {
        if (!GameFunctions.isBankOpen()) {
            if (Instant.now().isAfter(bankOpenDeadline)) {
                log.warn("Bank never opened. Retrying.");
                setState(State.WALKING_TO_BANK);
            }
            return;
        }

        if (GameFunctions.isInventoryFull()) {
            if (!depositClicked) {
                log.info("Depositing inventory.");
                GameFunctions.depositInventory();
                depositClicked = true;
            }
            return;
        }

        log.info("Inventory empty. closing bank.");
        GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
        depositClicked = false;
        setState(State.IDLE);
    }

    // ============================================================
    //  Helpers
    // ============================================================
    private void setState(State s) {
        if (state != s) log.debug("State: {} -> {}", state, s);
        state = s;
    }

    private GameObject pickTree(Player player) {
        int playerPlane = player.getWorldLocation().getPlane();
        Map<WorldPoint, GameObject> byPos = new HashMap<>();
        for (GameObject object : ObjectScanner.findGameObjects(Set.of(MAGIC_TREE))) {
            WorldPoint wp = object.getWorldLocation();
            if (wp.getPlane() == playerPlane) {
                byPos.put(wp, object);
            }
        }
        for (int[] p : PRIORITY_TREES) {
            WorldPoint wp = new WorldPoint(p[0], p[1], playerPlane);
            GameObject object = byPos.get(wp);
            if (object != null) return object;
        }
        return null;
    }

    public boolean isPlayerWoodcutting(Player player) {
        return player != null && player.getAnimation() == AnimationID.WOODCUTTING_DRAGON;
    }
}