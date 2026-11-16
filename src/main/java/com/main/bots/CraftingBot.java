package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import org.lwjgl.system.linux.Stat;

import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class CraftingBot {
    private enum State { IDLE, NOTING, UNNOTING, SKILLING }
    private enum Action { NONE, CLICK_CHISEL, CLICK_GEM, CLICK_NOTED_GEM, CLICK_BANK, START_CRAFTING }
    private State state;
    private Action action;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private boolean running = false;
    private int idleTicks = 0;
    private final int UNCUT_RUBY = 1619;
    private final int CUT_RUBY = 1603;
    private final int NOTED_RUBY = 1620;
    private final int CHISEL = 1755;

    public CraftingBot(Client client, ApexeaConfig config, ObjectScanner objectScanner){
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start(){
        log.info("Starting Crafting bot.");
        state = State.IDLE;
        action = Action.NONE;
    }

    public void stop(){
        log.info("Stopping Crafting bot.");
        state = State.IDLE;
        action = Action.NONE;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botCrafting();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        switch (state)
        {
            case IDLE:
                tickIdle();
                break;
            case SKILLING:
                tickCrafting();
                break;
            case NOTING:
            case UNNOTING:
                tickBanking();
                break;
        }
    }

    private void tickIdle(){
        if (action.equals(Action.NONE) || action.equals(Action.CLICK_CHISEL)){
            GameFunctions.clickInventoryItem(CHISEL);
            action = Action.CLICK_GEM;
            log.info("Clicking chisel.");
            return;
        }

        if (action.equals(Action.CLICK_GEM) && GameFunctions.countItem(UNCUT_RUBY) >= 1){
            GameFunctions.clickInventoryItem(UNCUT_RUBY);
            action = Action.START_CRAFTING;
            log.info("Clicking gem.");
            return;
        }

        if (action.equals(Action.START_CRAFTING) && idleTicks < 2) {
            idleTicks++;
            log.info("Waiting...");
            return;
        }

        if (action.equals(Action.START_CRAFTING)){
            GameFunctions.keyPress(KeyEvent.VK_SPACE);
            state = State.SKILLING;
            action = Action.CLICK_GEM;
            idleTicks = 0;
            log.info("Starting to craft.");
        }
    }

    private void tickCrafting(){
        if (GameFunctions.countItem(UNCUT_RUBY) == 0){
            state = State.NOTING;
            log.info("Waiting for crafting all the gems...");
        }
    }

    private void tickBanking(){
        if (action.equals(Action.CLICK_GEM)){
            GameFunctions.clickInventoryItem(CUT_RUBY);
            action = Action.CLICK_BANK;
            log.info("Clicking cut gem.");
            return;
        }

        if (action.equals(Action.CLICK_BANK)){
            Optional<GameObject> bank = objectScanner.findNearestObject(Set.of(10355));
            GameFunctions.clickObject(bank.get());
            log.info("Clicking bank.");
            if (state.equals(State.NOTING)){
                action = Action.CLICK_NOTED_GEM;
                state = State.UNNOTING;
                return;
            }
            if (state.equals(State.UNNOTING)){
                state = State.IDLE;
                action = Action.CLICK_CHISEL;
            }

            return;
        }

        if (action.equals(Action.CLICK_NOTED_GEM) && GameFunctions.countItem(CUT_RUBY) == 0){
            GameFunctions.clickInventoryItem(NOTED_RUBY);
            action = Action.CLICK_BANK;
            log.info("Clicking noted gems.");
            return;
        }
    }
}