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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class HunterBot {

    // --- Red Salamander object IDs ---
    private static final int YOUNG_TREE                 = 8990;
    private static final int SALAMANDER_TRAP_WAITING    = 8992;  // don't click
    private static final int SALAMANDER_TRAP_FAILED     = 62525;
    private static final int SALAMANDER_TRAP_SUCCESS    = 8986;

    // --- Red Salamander item IDs ---
    private static final int RED_SALAMANDER_ITEM        = 10147;
    private static final int SMALL_NET                  = 954;
    private static final int ROPE                       = 303;

    // --- Chinchompa box trap object IDs ---
    private static final int BOX_TRAP_WAITING           = 9380;  // don't click
    private static final int BOX_TRAP_FAILED            = 9385;
    private static final int BOX_TRAP_SUCCESS           = 9384;
    private static final int BOX_TRAP_ANIMATION   = 5212;

    // --- Priority trap spots for salamanders (worldX, worldY) ---
    private static final List<int[]> SALAMANDER_TRAP_SPOTS = List.of(
            new int[]{2449, 3228},
            new int[]{2451, 3225},
            new int[]{2447, 3225}
    );

    private static final Duration ACTION_COOLDOWN = Duration.ofMillis(1200);
    private static final Duration EMPTY_COOLDOWN  = Duration.ofSeconds(3);
    private static final Duration BOX_TRAP_PLACE_TIMEOUT   = Duration.ofSeconds(10);

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private Instant lastActionTime = Instant.EPOCH;
    private Instant lastEmptyTime  = Instant.EPOCH;

    private boolean waitingForBoxTrapPlacement = false;
    private boolean boxTrapPlacementSeen       = false;
    private Instant boxTrapWaitDeadline        = Instant.EPOCH;

    public HunterBot(Client client, ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start() {
        log.info("Hunter bot starting.");
        lastActionTime = Instant.EPOCH;
        lastEmptyTime  = Instant.EPOCH;
        waitingForBoxTrapPlacement = false;
        boxTrapPlacementSeen       = false;
        boxTrapWaitDeadline        = Instant.EPOCH;
        objectScanner.addObjectIds(Set.of(
                YOUNG_TREE,
                SALAMANDER_TRAP_WAITING,
                SALAMANDER_TRAP_FAILED,
                SALAMANDER_TRAP_SUCCESS,
                BOX_TRAP_WAITING,
                BOX_TRAP_FAILED,
                BOX_TRAP_SUCCESS
        ));
    }

    public void stop() {
        log.info("Hunter bot stopping.");
        lastActionTime = Instant.EPOCH;
        lastEmptyTime  = Instant.EPOCH;
        waitingForBoxTrapPlacement = false;
        boxTrapPlacementSeen       = false;
        boxTrapWaitDeadline        = Instant.EPOCH;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (AntiBotDetector.isDetected()) return;
        if (!config.botHunter()) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        if (Instant.now().isBefore(lastActionTime.plus(ACTION_COOLDOWN))) return;

        ApexeaConfig.HunterScript script = config.botHunterScript();
        if (script == ApexeaConfig.HunterScript.SALAMANDERS) {
            tickSalamanders(player);
        } else if (script == ApexeaConfig.HunterScript.CHINCHOMPAS) {
            tickChinchompas(player);
        }
    }

    // ============================================================
    //  RED SALAMANDERS
    // ============================================================
    private void tickSalamanders(Player player) {
        // 1. ::empty check — 20+ salamanders AND no net AND no rope
        int salamanders = GameFunctions.countItem(RED_SALAMANDER_ITEM);
        int nets        = GameFunctions.countItem(SMALL_NET);
        int ropes       = GameFunctions.countItem(ROPE);

        if (salamanders >= 20 && nets == 0 && ropes == 0) {
            if (Instant.now().isAfter(lastEmptyTime.plus(EMPTY_COOLDOWN))) {
                log.info("Emptying inventory ({} salamanders, 0 net, 0 rope).", salamanders);
                GameFunctions.sendCommand("::empty");
                lastEmptyTime = Instant.now();
            }
            return;
        }

        // 2. Priority: reset failed / successful traps
        ObjectScanner.Target resetTarget = findNearestOf(
                Set.of(SALAMANDER_TRAP_FAILED, SALAMANDER_TRAP_SUCCESS));
        if (resetTarget != null) {
            log.info("Resetting salamander trap at {}", resetTarget.getWorldPosition());
            doClick(resetTarget.getGameObject());
            return;
        }

        // 3. Count active traps (waiting + failed + success)
        int plane = player.getWorldLocation().getPlane();
        Set<WorldPoint> activeTrapPositions = new HashSet<>();
        for (ObjectScanner.Target t : objectScanner.findGameObjects(
                Set.of(SALAMANDER_TRAP_WAITING,
                        SALAMANDER_TRAP_FAILED,
                        SALAMANDER_TRAP_SUCCESS))) {
            if (t.getWorldPosition() != null) {
                activeTrapPositions.add(t.getWorldPosition());
            }
        }

        if (activeTrapPositions.size() >= SALAMANDER_TRAP_SPOTS.size()) {
            log.debug("All {} trap slots are active; nothing to set.", activeTrapPositions.size());
            return;
        }

        // 4. Set a trap on a young tree at a priority spot without an active trap
        Map<WorldPoint, ObjectScanner.Target> treesByPos = new HashMap<>();
        for (ObjectScanner.Target t : objectScanner.findGameObjects(Set.of(YOUNG_TREE))) {
            WorldPoint wp = t.getWorldPosition();
            if (wp != null && wp.getPlane() == plane) {
                treesByPos.put(wp, t);
            }
        }

        for (int[] pos : SALAMANDER_TRAP_SPOTS) {
            WorldPoint wp = new WorldPoint(pos[0], pos[1], plane);
            if (activeTrapPositions.contains(wp)) continue;
            ObjectScanner.Target tree = treesByPos.get(wp);
            if (tree != null && tree.getGameObject() != null) {
                log.info("Setting salamander trap on young tree at {}", wp);
                doClick(tree.getGameObject());
                return;
            }
        }
    }

    // ============================================================
    //  RED CHINCHOMPAS
    // ============================================================
    private void tickChinchompas(Player player) {
        // If we clicked a box trap, wait for it to finish being re-set up
        // (player plays the placement animation 5212, then returns to -1).
        if (waitingForBoxTrapPlacement) {
            int anim = player.getAnimation();

            if (anim == BOX_TRAP_ANIMATION) {
                if (!boxTrapPlacementSeen) {
                    log.debug("Box trap placement animation started.");
                }
                boxTrapPlacementSeen = true;
                return;
            }

            if (boxTrapPlacementSeen && anim == -1) {
                log.debug("Box trap re-setup complete; ready to click next.");
                waitingForBoxTrapPlacement = false;
                boxTrapPlacementSeen       = false;
                return;
            }

            if (Instant.now().isAfter(boxTrapWaitDeadline)) {
                log.warn("Box trap setup animation never completed; resuming.");
                waitingForBoxTrapPlacement = false;
                boxTrapPlacementSeen       = false;
            }
            return;
        }

        ObjectScanner.Target resetTarget = findNearestOf(
                Set.of(BOX_TRAP_FAILED, BOX_TRAP_SUCCESS));
        if (resetTarget != null) {
            log.info("Resetting box trap at {}", resetTarget.getWorldPosition());
            doClick(resetTarget.getGameObject());

            waitingForBoxTrapPlacement = true;
            boxTrapPlacementSeen       = false;
            boxTrapWaitDeadline        = Instant.now().plus(BOX_TRAP_PLACE_TIMEOUT);
        }
    }

    // ============================================================
    //  Helpers
    // ============================================================
    private void doClick(GameObject obj) {
        if (obj == null) return;
        GameFunctions.clickObject(obj);
        lastActionTime = Instant.now();
    }

    private ObjectScanner.Target findNearestOf(Set<Integer> ids) {
        return objectScanner.findGameObjects(ids).stream().findFirst().orElse(null);
    }
}