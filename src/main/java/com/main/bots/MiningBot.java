package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class MiningBot {
    private enum State { IDLE, MINING, BANKING }
    private enum Script { AMETHYST, POWER, RUNITE }

    private final Set<Integer> RUNITE = Set.of(11377, 11376);
    private final Set<Integer> AMETHYST = Set.of(11388, 11389);
    private final Set<Integer> IRON_ROCKS = Set.of(11365, 11364);

    private static final List<WorldPoint> AMETHYST_XY_SOUTH1 = List.of(
            new WorldPoint(3028, 9707, 0),
            new WorldPoint(3028, 9706, 0),
            new WorldPoint(3029, 9701, 0),
            new WorldPoint(3029, 9702, 0),
            new WorldPoint(3029, 9703, 0),
            new WorldPoint(3029, 9704, 0),
            new WorldPoint(3026, 9702, 0),
            new WorldPoint(3026, 9703, 0),
            new WorldPoint(3026, 9704, 0)
    );

    private static final List<WorldPoint> AMETHYST_XY_SOUTH2 = List.of(
            new WorldPoint(3018, 9706, 0),
            new WorldPoint(3017, 9701, 0),
            new WorldPoint(3017, 9702, 0),
            new WorldPoint(3017, 9703, 0),
            new WorldPoint(3017, 9704, 0),
            new WorldPoint(3020, 9703, 0),
            new WorldPoint(3019, 9699, 0),
            new WorldPoint(3020, 9699, 0),
            new WorldPoint(3021, 9699, 0)
    );

    private static final List<WorldPoint> AMETHYST_XY_WEST1 = List.of(
            new WorldPoint(3006, 9727, 0),
            new WorldPoint(3009, 9727, 0),
            new WorldPoint(3010, 9727, 0),
            new WorldPoint(3004, 9726, 0),
            new WorldPoint(3002, 9723, 0),
            new WorldPoint(3002, 9724, 0),
            new WorldPoint(3004, 9721, 0),
            new WorldPoint(3005, 9721, 0),
            new WorldPoint(3009, 9718, 0),
            new WorldPoint(3010, 9720, 0),
            new WorldPoint(3011, 9722, 0),
            new WorldPoint(3006, 9719, 0),
            new WorldPoint(3006, 9720, 0)
    );

    private static final List<WorldPoint> AMETHYST_XY_WEST2 = List.of(
            new WorldPoint(3010, 9714, 0),
            new WorldPoint(3011, 9716, 0),
            new WorldPoint(3011, 9708, 0),
            new WorldPoint(3012, 9710, 0),
            new WorldPoint(3012, 9711, 0),
            new WorldPoint(3012, 9712, 0),
            new WorldPoint(3009, 9706, 0),
            new WorldPoint(3008, 9710, 0),
            new WorldPoint(3008, 9711, 0),
            new WorldPoint(3008, 9712, 0)
    );

    private static final int BANK_CHEST = 58663;
    private static final int BANK_DEPOSIT = 10529;

    private final Client client;
    private final ApexeaConfig config;

    private State state = State.IDLE;
    private Script script = null;
    private boolean running = false;
    private boolean hasClicked = false;
    private TileObject currentRock = null;
    private boolean skipNextTick = false;
    private boolean justStarted = false;

    public MiningBot(Client client, ApexeaConfig config) {
        this.client = client;
        this.config = config;
    }

    public void start() {
        log.info("Starting Mining bot");
        justStarted = true;
        state = State.MINING;
        if (config.scriptMining().isAmethyst()) script = Script.AMETHYST;
        if (config.scriptMining() == ApexeaConfig.MiningScript.POWER_MINE) script = Script.POWER;
        if (config.scriptMining() == ApexeaConfig.MiningScript.RUNITE) script = Script.RUNITE;
        log.info("Script is {}", script);
    }

    public void stop() {
        log.info("Stopping Mining bot");
        state = State.IDLE;
        script = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botMining();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        if (skipNextTick) {
            skipNextTick = false;
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null) return;

        switch (state) {
            case MINING:
            case IDLE:
                tickMining();
                break;
            case BANKING:
                tickBanking();
                break;
        }
    }

    private void tickMining() {
        if (state.equals(State.IDLE) && GameFunctions.countInventoryItems() == 0){
            state = State.MINING;
            log.info("Inventory is empty. Going back to mining.");
            return;
        }
        if (GameFunctions.isInventoryFull()) {
            if (script == Script.POWER){
                GameFunctions.keyPressDelayed(new int[]{KeyEvent.VK_ENTER, KeyEvent.VK_PAGE_UP, KeyEvent.VK_ENTER}, 100);
                state = State.IDLE;
                log.info("Empty the inventory.");
                currentRock = null;
                return;
            }
            log.info("Inventory is full. Going to bank.");
            clickBank();
            state = State.BANKING;
            return;
        }

        if (currentRock != null && !GameFunctions.stillExists(currentRock) && script.equals(Script.POWER)) {
            currentRock = null;
            skipNextTick = true;
            log.info("Skipping next tick");
            return;
        }
        if (currentRock != null && GameFunctions.stillExists(currentRock)) {
            log.info("Rock still exist and is mining.");
            return;
        }

        int freeSlots = getFreeSlots();
        if (freeSlots <= 2) {
            if (script == Script.POWER){
                GameFunctions.keyPressDelayed(new int[]{KeyEvent.VK_ENTER, KeyEvent.VK_PAGE_UP, KeyEvent.VK_ENTER}, 100);
                state = State.IDLE;
                log.info("Empty the inventory.");
                currentRock = null;
                return;
            }
            log.info("Inventory nearly full ({} free slots), heading to bank", freeSlots);
            state = State.BANKING;
            clickBank();
            return;
        }

        TileObject rock = null;
        switch (script) {
            case RUNITE:
                rock = ObjectScanner.findNearestGameObject(RUNITE, null);
                break;
            case POWER:
                rock = ObjectScanner.findNearestGameObject(IRON_ROCKS, null);
                break;
            case AMETHYST:
                if (config.scriptMining() == ApexeaConfig.MiningScript.AMETHYST_SOUTH_1)
                    rock = ObjectScanner.findObjectCoords(AMETHYST, AMETHYST_XY_SOUTH1);
                if (config.scriptMining() == ApexeaConfig.MiningScript.AMETHYST_SOUTH_2)
                    rock = ObjectScanner.findObjectCoords(AMETHYST, AMETHYST_XY_SOUTH2);
                if (config.scriptMining() == ApexeaConfig.MiningScript.AMETHYST_WEST_1)
                    rock = ObjectScanner.findObjectCoords(AMETHYST, AMETHYST_XY_WEST1);
                if (config.scriptMining() == ApexeaConfig.MiningScript.AMETHYST_WEST_2)
                    rock = ObjectScanner.findObjectCoords(AMETHYST, AMETHYST_XY_WEST2);
                break;
        }

        if (rock == null) return;
        if (justStarted) justStarted = false;
        GameFunctions.clickObject(rock);
        currentRock = rock;
        log.info("Clicked rock. Going to mine.");
    }

    private void tickBanking() {
        if (currentRock != null) currentRock = null;
        if (GameFunctions.isBankOpen() && !hasClicked) {
            GameFunctions.depositInventory();
            hasClicked = true;
        }

        if (GameFunctions.countInventoryItems() == 0){
            state = State.MINING;
            if (GameFunctions.isBankOpen())
                GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
            hasClicked = false;
            log.info("Banking done. Going to mining.");
        }
    }

    private void clickBank() {
        GameObject bank = ObjectScanner.findNearestGameObject(Set.of(BANK_CHEST, BANK_DEPOSIT), null);
        GameFunctions.clickObject(bank);
    }

    private int getFreeSlots() {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return 0;
        int used = 0;
        for (Item item : inv.getItems()) {
            if (item != null && item.getId() != -1) used++;
        }
        return inv.size() - used;
    }
}