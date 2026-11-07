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

    private static final Duration ACTION_COOLDOWN = Duration.ofMillis(500);
    private static final Duration CHOP_START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BANK_OPEN_TIMEOUT = Duration.ofSeconds(10);

    // --- priority trees, in order (worldX, worldY) ---
    private static final List<int[]> PRIORITY_TREES = List.of(
            new int[]{2844, 2960},
            new int[]{2841, 2960},
            new int[]{2844, 2966},
            new int[]{2838, 2960}
    );

    // --- bank chest position ---
    private static final int BANK_X = 2852;
    private static final int BANK_Y = 2951;

    private static final Set<Integer> TREE_IDS = Set.of(
            ObjectID.OAK_TREE, ObjectID.WILLOW_TREE, ObjectID.MAPLE_TREE,
            ObjectID.YEW_TREE, ObjectID.MAGIC_TREE, ObjectID.MAGIC_TREE_10834
    );
    private static final int BANK_CHEST = ObjectID.BANK_CHEST_14886;
    private static final int MAGIC_TREE = ObjectID.MAGIC_TREE_10834;

    private static final Set<Integer> WC_ANIMATIONS = Set.of(
            AnimationID.WOODCUTTING_BRONZE, AnimationID.WOODCUTTING_IRON,
            AnimationID.WOODCUTTING_STEEL, AnimationID.WOODCUTTING_BLACK,
            AnimationID.WOODCUTTING_MITHRIL, AnimationID.WOODCUTTING_RUNE,
            AnimationID.WOODCUTTING_DRAGON
    );

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private State state = State.IDLE;
    private Instant lastActionAt = Instant.EPOCH;
    private boolean running = false;

    // chop tracking
    private boolean choppingStarted = false;
    private Instant chopStartDeadline = Instant.EPOCH;

    // bank tracking
    private Instant bankOpenDeadline = Instant.EPOCH;
    private boolean depositClicked = false;

    public WoodcuttingBot(Client client,
                          ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start() {
        log.info("Woodcutting bot starting.");
        state = State.IDLE;
        lastActionAt = Instant.EPOCH;
        choppingStarted = false;
        depositClicked = false;
        objectScanner.addObjectIds(Set.of(BANK_CHEST, MAGIC_TREE));
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

        boolean shouldRun = config.botWoodcutting();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        // throttle actual game actions
        if (Instant.now().isBefore(lastActionAt.plus(ACTION_COOLDOWN))) return;

        switch (state) {
            case IDLE:             tickIdle(player); break;
            case CHOPPING:         tickChopping(player); break;
            case WALKING_TO_BANK:  tickWalkingToBank(player); break;
            case BANKING:          tickBanking(); break;
        }
    }

    // ============================================================
    //  IDLE — pick and click a priority tree
    // ============================================================
    private void tickIdle(Player player) {
        if (GameFunctions.isInventoryFull(client)) {
            log.info("Inventory full. Going to bank.");
            setState(State.WALKING_TO_BANK);
            return;
        }

        ObjectScanner.Target tree = pickPriorityTree(player);
        if (tree == null) {
            return;
        }

        GameFunctions.clickObject(client, tree.getGameObject());

        choppingStarted = false;
        chopStartDeadline = Instant.now().plus(CHOP_START_TIMEOUT);
        setState(State.CHOPPING);
        log.info("Found tree and started chopping.");
    }

    // ============================================================
    //  CHOPPING — wait for the tree to fall or inventory to fill
    // ============================================================
    private void tickChopping(Player player) {
        if (GameFunctions.isInventoryFull(client)) {
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
    private void tickWalkingToBank(Player player) {
        if (!GameFunctions.isInventoryFull(client)) {
            log.info("Inventory not full. Resuming");
            setState(State.IDLE);
            return;
        }

        ObjectScanner.Target bank = findBank(player);
        if (bank == null) {
            log.debug("Bank chest not visible.");
            return;
        }

        log.info("Clicking bank chest.");
        GameFunctions.clickObject(client, bank.getGameObject());

        depositClicked = false;
        bankOpenDeadline = Instant.now().plus(BANK_OPEN_TIMEOUT);
        setState(State.BANKING);
    }

    // ============================================================
    //  BANKING — deposit, then close and resume
    // ============================================================
    private void tickBanking() {
        if (!GameFunctions.isBankOpen(client)) {
            if (Instant.now().isAfter(bankOpenDeadline)) {
                log.warn("Bank never opened. Retrying.");
                setState(State.WALKING_TO_BANK);
            }
            return;
        }

        if (GameFunctions.isInventoryFull(client)) {
            if (!depositClicked) {
                log.info("Depositing inventory.");
                GameFunctions.depositInventory(client);
                lastActionAt = Instant.now();
                depositClicked = true;
            }
            return;
        }

        log.info("Inventory empty. closing bank.");
        GameFunctions.pressKey(client, KeyEvent.VK_ESCAPE);
        depositClicked = false;
        setState(State.IDLE);
    }

    // ============================================================
    //  Helpers
    // ============================================================
    private void setState(State s) {
        if (state != s) log.debug("State: {} -> {}", state, s);
        state = s;
        lastActionAt = Instant.now();
    }

    private ObjectScanner.Target pickPriorityTree(Player player) {
        int plane = player.getWorldLocation().getPlane();
        Map<WorldPoint, ObjectScanner.Target> byPos = new HashMap<>();
        for (ObjectScanner.Target t : objectScanner.findGameObjects(Set.of(MAGIC_TREE))) {
            WorldPoint wp = t.getWorldPosition();
            if (wp != null && wp.getPlane() == plane && t.getGameObject() != null) {
                byPos.put(wp, t);
            }
        }
        for (int[] p : PRIORITY_TREES) {
            WorldPoint wp = new WorldPoint(p[0], p[1], plane);
            ObjectScanner.Target t = byPos.get(wp);
            if (t != null) return t;
        }
        return null;
    }

    private ObjectScanner.Target findBank(Player player) {
        int plane = player.getWorldLocation().getPlane();
        WorldPoint bankPos = new WorldPoint(BANK_X, BANK_Y, plane);
        for (ObjectScanner.Target t : objectScanner.findGameObjects(Set.of(BANK_CHEST))) {
            if (bankPos.equals(t.getWorldPosition()) && t.getGameObject() != null) {
                return t;
            }
        }
        return null;
    }

    public boolean isPlayerWoodcutting(Player player) {
        return player != null && WC_ANIMATIONS.contains(player.getAnimation());
    }
}