package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.util.Optional;
import java.util.Set;

@Slf4j
public class TearsOfGuthixBot {

    private enum State { IDLE, COLLECTING, WAITING }

    private static final Set<Integer> BLUE_TEARS = Set.of(
            ObjectID.BLUE_TEARS,        // 6661
            ObjectID.BLUE_TEARS_6665    // 6665
    );

    private static final int STONE_BOWL = ObjectID.BOWL;

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private State state = State.IDLE;
    private boolean running = false;
    private int ticksSinceClick = 0;

    private static final int MIN_TICKS_BETWEEN_CLICKS = 3;

    public TearsOfGuthixBot(Client client, ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start() {
        log.info("Tears of Guthix bot starting");
        state = State.IDLE;
        ticksSinceClick = 0;
    }

    public void stop() {
        log.info("Tears of Guthix bot stopping");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botTOG();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        // If the stone bowl is not wielded / in inventory, the player isn’t in the minigame.
        if (!hasStoneBowl()) {
            log.debug("Stone bowl not found – not in Tears of Guthix?");
            return;
        }

        ticksSinceClick++;

        // Re-scan every tick because streams relocate every 16 ticks.
        Optional<GameObject> blueStream = objectScanner.findNearestObject(BLUE_TEARS);

        if (blueStream.isEmpty()) {
            // Between stream cycles there may be a brief moment with no blue tears visible.
            if (state != State.WAITING) {
                log.debug("No blue tear stream in scene – waiting");
                state = State.WAITING;
            }
            return;
        }

        // Throttle clicks so we don’t hammer the same stream every tick.
        if (ticksSinceClick < MIN_TICKS_BETWEEN_CLICKS) {
            return;
        }

        GameObject stream = blueStream.get();

        // If we are already collecting from this exact stream, do nothing.
        if (state == State.COLLECTING && isInteractingWith(stream)) {
            return;
        }

        log.info("Clicking blue tear stream (object id {})", stream.getId());
        GameFunctions.clickObject(stream);

        state = State.COLLECTING;
        ticksSinceClick = 0;
    }

    private boolean hasStoneBowl() {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item != null && item.getId() == STONE_BOWL) return true;
            }
        }
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item != null && item.getId() == STONE_BOWL) return true;
            }
        }
        return false;
    }

    private boolean isInteractingWith(GameObject object) {
        Actor interacting = client.getLocalPlayer().getInteracting();
        return false;
    }
}