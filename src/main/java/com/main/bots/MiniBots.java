package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class MiniBots {
    private enum State { IDLE, SELLING,  WAITING, BANKING }
    private enum Action { NONE, SELL_LOOT, HOLD_SHIFT, PICK_OPTION, WITHDRAW, RELEASE_KEY, WAITING }
    private static final int ALI_NPC = 3533;
    private static final int ADAMANT_SCIMMY = 1331;
    private static final int RUBY_RING = 1641;
    private static final int BANK_CHEST = 12309;

    private final Client client;
    private final ApexeaConfig config;

    private State state = State.IDLE;
    private Action action = Action.NONE;
    private boolean running = false;
    private int ticksWaited = 0;
    public boolean isShopOpen = false;

    public MiniBots(Client client, ApexeaConfig config){
        this.client = client;
        this.config = config;
    }

    public void start() {
        log.info("Bot starting");
        state = State.IDLE;
        action = Action.NONE;
        isShopOpen = false;
    }

    public void stop() {
        log.info("Bot stopping");
        state = State.IDLE;
        action = Action.NONE;
        isShopOpen = false;
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

        if(config.scriptMinis() == ApexeaConfig.MiniScripts.SELL_THIEVE) {
            if (state.equals(State.IDLE)) state = State.SELLING;
            tickSelling(RUBY_RING);
        }
    }

    private void tickSelling(int itemId) {
        if (state.equals(State.SELLING) && action.equals(Action.NONE)) {
                NPC npc = ObjectScanner.findNearestNpc(ALI_NPC);
                if (npc == null) {
                    log.debug("Seller NPC {} not visible", ALI_NPC);
                    return;
                }

                log.info("Clicking Ali");
                GameFunctions.clickNpc(npc);
                state = State.WAITING;
                action = Action.WAITING;
                return;
            }

        /*
        if (state.equals(State.WAITING) && action.equals(Action.WAITING) && ticksWaited < 12 ){
            ticksWaited++;
            return;
        }
         */

        if (state.equals(State.WAITING) && action.equals(Action.WAITING) && isShopOpen) {
            state = State.SELLING;
            action = Action.HOLD_SHIFT;
            GameFunctions.keyHold(KeyEvent.VK_SHIFT);
            log.info("Holding shift key");
            return;
        }

        if (state.equals(State.SELLING) && action.equals(Action.HOLD_SHIFT) && isShopOpen){
            GameFunctions.clickInventoryItem(itemId, MouseEvent.BUTTON1);
            action = Action.RELEASE_KEY;
            log.info("Selling rings");
            return;
        }

        if (GameFunctions.countItem(itemId) <= 0 && action.equals(Action.RELEASE_KEY) && isShopOpen) {
            log.info("All rings sold - closing shop");
            GameFunctions.keyRelease(KeyEvent.VK_SHIFT);
            GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
            state = State.BANKING;
            action = Action.NONE;
            ticksWaited = 0;
            isShopOpen = false;
            return;
        }

        if (state.equals(State.BANKING) && action.equals(Action.NONE)){
            GameObject bankChest = ObjectScanner.findNearestGameObject(BANK_CHEST);
            GameFunctions.clickObject(bankChest);
            action = Action.WITHDRAW;
            log.info("Clicking bank");
            return;
        }

        if (state.equals(State.BANKING) && action.equals(Action.WITHDRAW) && GameFunctions.isBankOpen()){
            GameFunctions.click(MouseEvent.BUTTON1, 1275, 420);
            state = State.WAITING;
            log.info("Withdrawing ruby rings");
            return;
        }

        if (state.equals(State.WAITING) && action.equals(Action.WITHDRAW)){
            if (GameFunctions.countItem(RUBY_RING) == 27){
                GameFunctions.keyPress(KeyEvent.VK_ESCAPE);
                state = State.SELLING;
                action = Action.NONE;
                log.info("Ruby rings withdrawn. Restarting.");
            }
            return;
        }
    }
}
