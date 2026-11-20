package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

import java.util.List;

@Slf4j
public class TitheFarmBot {
    private enum State { IDLE, PLANTING, WATERING, HARVEST }
    private enum Action { WAITING, ACTION, FILLING_WATER }
    private State state;
    private Action action;
    private final Client client;
    private final ApexeaConfig config;
    private boolean running = false;
    private int currentPatch = 0;
    private int currentStage = 0;
    private boolean hasClicked = false;
    private int humidifyWaitTicks = 0;

    private static final List<WorldPoint> PATCH_COORDS2 = List.of(
            new WorldPoint(1810, 3512, 0),
            new WorldPoint(1810, 3509, 0),
            new WorldPoint(1810, 3506, 0),
            new WorldPoint(1810, 3503, 0),
            new WorldPoint(1810, 3497, 0),
            new WorldPoint(1810, 3494, 0),
            new WorldPoint(1810, 3491, 0),
            new WorldPoint(1810, 3488, 0),

            new WorldPoint(1815, 3512, 0),
            new WorldPoint(1815, 3509, 0),
            new WorldPoint(1815, 3506, 0),
            new WorldPoint(1815, 3503, 0),
            new WorldPoint(1815, 3497, 0),
            new WorldPoint(1815, 3494, 0),
            new WorldPoint(1815, 3491, 0),
            new WorldPoint(1815, 3488, 0),

            new WorldPoint(1820, 3482, 0),
            new WorldPoint(1820, 3497, 0),
            new WorldPoint(1820, 3494, 0),
            new WorldPoint(1820, 3491, 0),
            new WorldPoint(1820, 3488, 0)
    );

    private static final List<WorldPoint> PATCH_COORDS = List.of(
            new WorldPoint(1815, 3512, 0),
            new WorldPoint(1810, 3512, 0),
            new WorldPoint(1810, 3509, 0),
            new WorldPoint(1815, 3509, 0),
            new WorldPoint(1815, 3506, 0),
            new WorldPoint(1810, 3506, 0),
            new WorldPoint(1810, 3503, 0),
            new WorldPoint(1815, 3503, 0),

            new WorldPoint(1815, 3497, 0),
            new WorldPoint(1810, 3497, 0),
            new WorldPoint(1810, 3494, 0),
            new WorldPoint(1815, 3494, 0),
            new WorldPoint(1815, 3491, 0),
            new WorldPoint(1810, 3491, 0),
            new WorldPoint(1810, 3488, 0),
            new WorldPoint(1815, 3488, 0),

            new WorldPoint(1820, 3482, 0),
            new WorldPoint(1820, 3488, 0),
            new WorldPoint(1820, 3491, 0),
            new WorldPoint(1820, 3494, 0),
            new WorldPoint(1820, 3497, 0)
    );

    private final int SEED_ID = 13425;
    private final int WATERING_CAN_8 = 5340;
    private final int WATERING_CAN_0 = 5331;
    private final int PATCH = 27383;

    private static final int[] PLANTS = {
            27406, // stage 1
            27409, // stage 2
            27412, // stage 3
            27415  // stage 4 (grown)
    };

    private static final int[] WATERED_PLANTS = {
            27407, // stage 1
            27410, // stage 2
            27413, // stage 3
    };

    public TitheFarmBot(Client client, ApexeaConfig config) {
        this.client = client;
        this.config = config;
    }

    public void start() {
        log.info("Tithe Farm bot has started.");
        state = State.IDLE;
        action = Action.WAITING;
        currentPatch = 0;
        currentStage = 0;
    }

    public void stop() {
        log.info("Tithe Farm bot has stopped.");
        state = State.IDLE;
        action = Action.WAITING;
        currentPatch = 0;
        currentStage = 0;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botTithe();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();

        switch (state)
        {
            case IDLE:
                tickIdle(player);
                break;
            case PLANTING:
                tickPlanting(player);
                break;
            case WATERING:
                tickWatering(player);
                break;
            case HARVEST:
                tickHarvesting(player);
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.ZEAH_WATERINGCAN_CHARGES) {
            int charges = client.getVarbitValue(VarbitID.ZEAH_WATERINGCAN_CHARGES);
        }
    }

    private void tickIdle(Player player) {
        if (GameFunctions.countItem(WATERING_CAN_8) < 8) action = Action.FILLING_WATER;
        if (GameFunctions.countItem(WATERING_CAN_8) == 16 && action.equals(Action.FILLING_WATER)) {
            action = Action.WAITING;
            hasClicked = false;
        }

        if (action.equals(Action.FILLING_WATER) && !hasClicked) {
            Point spellbookButton = GameFunctions.getButton("spellbook");
            Point humidifyButton = GameFunctions.getButton("humidify");
            Point inventoryButton = GameFunctions.getButton("inventory");
            GameFunctions.clickMultiple(new int[][] {
                    {spellbookButton.getX(), spellbookButton.getY() },
                    {humidifyButton.getX(), humidifyButton.getY() },
                    {inventoryButton.getX(), inventoryButton.getY() }
            }, 150);
            log.info("Casting Humidify");
            hasClicked = true;
            humidifyWaitTicks = 8;   // start the post-cast wait
            return;
        }

        if (humidifyWaitTicks > 0) {
            humidifyWaitTicks--;
            return;
        }

        if (GameFunctions.countItem(WATERING_CAN_8) >= 8 && action.equals(Action.WAITING)) {
            WorldPoint targetPoint = new WorldPoint(1814, 3513, 0);
            if (!GameFunctions.isPlayerAtCoordinates(targetPoint) && !hasClicked) {
                GameFunctions.walkToTile(targetPoint);
                hasClicked = true;
            }

            if (GameFunctions.isPlayerAtCoordinates(targetPoint)) {
                hasClicked = false;
                state = State.PLANTING;
                action = Action.WAITING;
            }
        }
    }

    private void tickPlanting(Player player) {
        if (action.equals(Action.WAITING)) {
            if (currentPatch == PATCH_COORDS.size()){
                currentPatch = 0;
                state = State.WATERING;
                action = Action.WAITING;
                log.info("Going from planting to watering.");
                return;
            }

            GameObject patch = getCurrentPatchObject(PATCH);
            GameFunctions.runFunctions(100,
                    () -> GameFunctions.clickInventoryItem(SEED_ID),
                    () -> {
                        for (int attempt = 1; attempt <= 5; attempt++) {
                            if (GameFunctions.clickObject(patch)) {
                                log.info("Patch clicked on attempt {}", attempt);
                                return;
                            }
                            log.info("Patch click failed, retrying ({}/5)", attempt);
                        }
                        log.warn("Patch click failed after 5 attempts");
                    }
            );

            action = Action.ACTION;
        }

        if (action.equals(Action.ACTION) && ObjectScanner.objectExistsAt(PLANTS[0], PATCH_COORDS.get(currentPatch))){
            currentPatch++;
            action = Action.WAITING;
            return;
        }
    }

    private void tickWatering(Player player) {
        if (!action.equals(Action.WAITING)) return;

        if (currentPatch >= PATCH_COORDS.size()) {
            currentPatch = 0;
            currentStage++;
            hasClicked = false;
            if (currentStage == 3) state = State.HARVEST;
            return;
        }

        log.info("Current stage: {}, current patch: {}", currentStage, currentPatch);

        WorldPoint coord = PATCH_COORDS.get(currentPatch);

        // This patch is already watered → advance to the next one.
        if (ObjectScanner.objectExistsAt(WATERED_PLANTS[currentStage], coord)) {
            currentPatch++;
            hasClicked = false;
            return;
        }

        // We clicked this patch; wait for the game to register the watering.
        if (hasClicked) return;

        GameObject patchObject = getCurrentPatchObject(PLANTS[currentStage]);
        if (patchObject == null) {
            log.warn("Patch object is null at patch {}", currentPatch);
            return;
        }

        GameFunctions.clickObject(patchObject);
        hasClicked = true;
        log.info("Clicked plant for watering at patch {}", currentPatch);
    }

    private void tickHarvesting(Player player) {
        if (!action.equals(Action.WAITING)) return;

        // All patches done → reset and go back to idle for the next batch.
        if (currentPatch >= PATCH_COORDS.size()) {
            currentPatch = 0;
            currentStage = 0;
            hasClicked = false;
            state = State.IDLE;
            action = Action.WAITING;
            log.info("Harvest complete. Back to idle.");
            return;
        }

        WorldPoint coord = PATCH_COORDS.get(currentPatch);

        // We've already clicked this patch → wait for the plant to disappear.
        if (hasClicked) {
            if (!ObjectScanner.objectExistsAt(PLANTS[3], coord)) {
                log.info("Patch {} harvested.", currentPatch);
                currentPatch++;
                hasClicked = false;
            }
            return;
        }

        // Not clicked yet → click the finished plant on this patch.
        GameObject grownPlant = getCurrentPatchObject(PLANTS[3]);
        if (grownPlant == null) {
            // Hasn't grown yet (or already gone for some reason) — wait.
            log.info("Waiting for grown plant at patch {}.", currentPatch);
            return;
        }

        GameFunctions.clickObject(grownPlant);
        hasClicked = true;
        log.info("Clicked grown plant at patch {}.", currentPatch);
    }

    private WorldPoint getCurrentPatchLocation() {
        if (currentPatch < 0 || currentPatch >= PATCH_COORDS.size()) return null;
        return PATCH_COORDS.get(currentPatch);
    }

    private GameObject getCurrentPatchObject(int patchObjectId) {
        WorldPoint wp = getCurrentPatchLocation();
        if (wp == null) return null;

        Scene scene = client.getScene();
        if (scene == null) return null;

        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) return null; // patch is outside the loaded scene

        Tile tile = scene.getTiles()[wp.getPlane()][lp.getSceneX()][lp.getSceneY()];
        if (tile == null) return null;

        for (GameObject object : tile.getGameObjects()) {
            if (object != null && object.getId() == patchObjectId) return object;
        }
        return null;
    }
}