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
    // --- Chinchompa box trap object IDs ---
    private static final int BOX_TRAP_WAITING           = 9380;  // don't click
    private static final int BOX_TRAP_FAILED            = 9385;
    private static final int BOX_TRAP_SUCCESS           = 9384;
    private static final int BOX_TRAP_ANIMATION   = 5212;

    private static final Set<Integer> BOX_TRAP_IDS = Set.of(
            BOX_TRAP_WAITING, BOX_TRAP_FAILED, BOX_TRAP_SUCCESS);

    // --- Chinchompa trap ownership tracking ---
    private final Set<WorldPoint> confirmedOwnedBoxTrapTiles    = new HashSet<>();
    private final Set<WorldPoint> confirmedNotOwnedBoxTrapTiles = new HashSet<>();
    private WorldPoint pendingOwnerCheckTile = null;

    private static final Duration ACTION_COOLDOWN = Duration.ofMillis(1200);
    private static final Duration BOX_TRAP_PLACE_TIMEOUT   = Duration.ofSeconds(10);

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private Instant lastActionTime = Instant.EPOCH;

    private boolean waitingForBoxTrapPlacement = false;
    private boolean boxTrapPlacementSeen       = false;
    private Instant boxTrapWaitDeadline        = Instant.EPOCH;
    private final Map<WorldPoint, TrackedBoxTrap> trackedBoxTraps = new HashMap<>();

    public HunterBot(Client client, ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start() {
        log.info("Hunter bot starting.");
        lastActionTime = Instant.EPOCH;
        waitingForBoxTrapPlacement = false;
        boxTrapPlacementSeen       = false;
        boxTrapWaitDeadline        = Instant.EPOCH;
        trackedBoxTraps.clear();
        confirmedOwnedBoxTrapTiles.clear();
        confirmedNotOwnedBoxTrapTiles.clear();
        pendingOwnerCheckTile = null;
        objectScanner.addObjectIds(Set.of(
                BOX_TRAP_WAITING,
                BOX_TRAP_FAILED,
                BOX_TRAP_SUCCESS
        ));
    }

    public void stop() {
        log.info("Hunter bot stopping.");
        lastActionTime = Instant.EPOCH;
        waitingForBoxTrapPlacement = false;
        boxTrapPlacementSeen       = false;
        boxTrapWaitDeadline        = Instant.EPOCH;
        confirmedOwnedBoxTrapTiles.clear();
        confirmedNotOwnedBoxTrapTiles.clear();
        pendingOwnerCheckTile = null;
        trackedBoxTraps.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (AntiBotDetector.isDetected()) return;
        if (!config.botHunter()) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;


        updateBoxTrapTracking();
        if (Instant.now().isBefore(lastActionTime.plus(ACTION_COOLDOWN))) return;
        tickChinchompas(player);
    }


    private void tickChinchompas(Player player) {
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

        ObjectScanner.Target oldest = null;
        Instant oldestSeen = Instant.MAX;

        for (ObjectScanner.Target t : objectScanner.findGameObjects(
                Set.of(BOX_TRAP_FAILED, BOX_TRAP_SUCCESS))) {

            if (t.getGameObject() == null) continue;

            WorldPoint wp = t.getWorldPosition();
            if (wp == null) continue;

            TrackedBoxTrap tracked = trackedBoxTraps.get(wp);
            Instant seen = (tracked != null) ? tracked.firstSeen : Instant.now();

            if (seen.isBefore(oldestSeen)) {
                oldestSeen = seen;
                oldest     = t;
            }
        }

        if (oldest == null) {
            return;
        }

        log.info("Resetting box trap at {} (state age {} ms)",
                oldest.getWorldPosition(),
                Duration.between(oldestSeen, Instant.now()).toMillis());

        doClick(oldest.getGameObject());

        waitingForBoxTrapPlacement = true;
        boxTrapPlacementSeen       = false;
        boxTrapWaitDeadline        = Instant.now().plus(BOX_TRAP_PLACE_TIMEOUT);
    }

    // ============================================================
    //  Chinchompa trap-age tracking
    // ============================================================
    private void updateBoxTrapTracking() {
        List<ObjectScanner.Target> visible =
                objectScanner.findGameObjects(BOX_TRAP_IDS);

        // Build a quick view of what we can currently see.
        Map<WorldPoint, Integer> visibleIds = new HashMap<>();
        for (ObjectScanner.Target t : visible) {
            WorldPoint wp = t.getWorldPosition();
            if (wp == null) continue;
            visibleIds.put(wp, t.getId());
        }

        // Drop entries for traps that are no longer on-screen (picked up,
        // despawned, or out of render distance).
        trackedBoxTraps.keySet().retainAll(visibleIds.keySet());

        // Add new traps / reset age when a trap's object ID changed.
        Instant now = Instant.now();
        for (Map.Entry<WorldPoint, Integer> e : visibleIds.entrySet()) {
            WorldPoint wp    = e.getKey();
            int        newId = e.getValue();
            TrackedBoxTrap existing = trackedBoxTraps.get(wp);

            if (existing == null || existing.objectId != newId) {
                trackedBoxTraps.put(wp, new TrackedBoxTrap(newId, now));
            }
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

    private static class TrackedBoxTrap {
        final int objectId;
        final Instant firstSeen;

        TrackedBoxTrap(int objectId, Instant firstSeen) {
            this.objectId = objectId;
            this.firstSeen = firstSeen;
        }
    }
}