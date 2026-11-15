package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class MiniBots {
    private enum State { IDLE, SELLING, SELL_LOOT, RIGHT_CLICK, PICK_OPTION, WAITING, BANKING }
    private static final int ALI_NPC = 3533; // Ali
    private static final int ADAMANT_SCIMMY = 1331; // Adamant Scimitar
    private static final int RUBY_RING = 1641; // Ruby Ring
    private static final int BANK_CHEST = 12309; // Ruby Ring

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private State state = State.IDLE;
    private boolean running = false;

    public MiniBots(Client client, ApexeaConfig config, ObjectScanner objectScanner){
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start() {
        log.info("Bot starting");
    }

    public void stop() {
        log.info("Bot stopping");
        state = State.IDLE;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;

        boolean shouldRun = config.botMinis();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        if(config.botMiniScript() == ApexeaConfig.MiniBots.SELL_THIEVE) {
            tickSelling(RUBY_RING);
        }
    }

    private void tickSellStuff(){
        Optional<GameObject> bankChest = objectScanner.findNearestObject(Set.of(BANK_CHEST));
        if (bankChest.isEmpty()) return;
        GameFunctions.clickObject(bankChest.get());
    }

    private void tickSelling(int itemId) {
        if (state.equals(State.SELL_LOOT) && !GameFunctions.isShopOpen()) {
                Optional<NPC> npc = objectScanner.findNearestNpc(Set.of(ALI_NPC));
                if (npc.isEmpty()) {
                    log.debug("Seller NPC {} not visible", ALI_NPC);
                    return;
                }

                GameFunctions.clickNpc(npc.get());
                state = State.WAITING;
                return;
            }

        if (state.equals(State.WAITING) && GameFunctions.isShopOpen()) {
            state = State.RIGHT_CLICK;
            return;
        }

        if (state.equals(State.RIGHT_CLICK) && GameFunctions.isShopOpen()){
            GameFunctions.clickInventoryItem(RUBY_RING, MouseEvent.BUTTON3);
        }

        if (GameFunctions.countItem(itemId) <= 0) {
            log.info("All scimitars sold - closing shop");
            GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
            state = State.BANKING;
            return;
        }
    }
}
