package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Set;

@Slf4j
public class CraftingBot {
    private enum State { IDLE, NOTING, UNNOTING, SKILLING }
    private enum Action { NONE, CLICK_CHISEL, CLICK_GEM, CLICK_NOTED_GEM, CLICK_BANK, START_CRAFTING, DEPOSIT, WITHDRAW }
    private State state;
    private Action action;
    private final Client client;
    private final ApexeaConfig config;
    private boolean running = false;
    private int idleTicks = 0;
    private final int UNCUT_RUBY = 1619;
    private final int CUT_RUBY = 1603;
    private final int NOTED_RUBY = 1620;
    private final int CHISEL = 1755;
    private final int GOLD_BAR = 2357;
    private final int RUBY_RING = 1641;
    private final Set<Integer> BANKS = Set.of(10355);
    private final Set<Integer> FURNACE = Set.of(1);

    public CraftingBot(Client client, ApexeaConfig config){
        this.client = client;
        this.config = config;
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

        if (config.scriptCrafting() == ApexeaConfig.CraftingScript.CRAFT_RUBIES){
            switch (state)
            {
                case IDLE:
                    tickIdleGems();
                    break;
                case SKILLING:
                    tickCraftingGems();
                    break;
                case NOTING:
                case UNNOTING:
                    tickBankingGems();
                    break;
            }
        }

        if (config.scriptCrafting() == ApexeaConfig.CraftingScript.CRAFT_RINGS){
            switch (state)
            {
                case IDLE:
                    tickIdleRings();
                    break;
                case SKILLING:
                    tickCraftingRings();
                    break;
                case NOTING:
                case UNNOTING:
                    tickBankingRings();
                    break;
            }
        }
    }

    // ###########################################################################
    // Craft Rubies
    // ###########################################################################
    private void tickIdleGems(){
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

    private void tickCraftingGems(){
        if (GameFunctions.countItem(UNCUT_RUBY) == 0){
            state = State.NOTING;
            log.info("Waiting for crafting all the gems...");
        }
    }

    private void tickBankingGems(){
        if (action.equals(Action.CLICK_GEM)){
            GameFunctions.clickInventoryItem(CUT_RUBY);
            action = Action.CLICK_BANK;
            log.info("Clicking cut gem.");
            return;
        }

        if (action.equals(Action.CLICK_BANK)){
            GameObject bank = ObjectScanner.findNearestGameObject(BANKS, null);
            GameFunctions.clickObject(bank);
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

    // ###########################################################################
    // Craft Ruby Rings
    // ###########################################################################
    private void tickIdleRings(){
        if (state.equals(State.IDLE) && action.equals(Action.NONE)){
            GameObject furnace = ObjectScanner.findNearestGameObject(FURNACE);
            GameFunctions.clickObject(furnace);
            action = Action.START_CRAFTING;
            return;
        }

        Widget goldInterface = client.getWidget(446, 0);
        if (action.equals(Action.START_CRAFTING) && goldInterface != null){
            GameFunctions.click(MouseEvent.BUTTON1, 1, 1);
            state = State.SKILLING;
            action = Action.CLICK_BANK;
            return;
        }
    }

    private void tickCraftingRings(){
        if (action.equals(Action.CLICK_BANK) && GameFunctions.countItem(RUBY_RING) == 13){
            GameObject bank = ObjectScanner.findNearestGameObject(BANKS);
            GameFunctions.clickObject(bank);
            action = Action.DEPOSIT;
            return;
        }

        if (action.equals(Action.DEPOSIT) && GameFunctions.isBankOpen()){
            GameFunctions.clickInventoryItem(MouseEvent.BUTTON1, RUBY_RING);
            action = Action.WITHDRAW;
        }

        if (action.equals(Action.WITHDRAW) && GameFunctions.countItem(RUBY_RING) == 0){
            Point goldBar = GameFunctions.getBankItem(GOLD_BAR);
            Point cutRuby = GameFunctions.getBankItem(CUT_RUBY);

            assert goldBar != null;
            assert cutRuby != null;
            GameFunctions.click(MouseEvent.BUTTON1, goldBar.getX(), goldBar.getY());
            GameFunctions.click(MouseEvent.BUTTON1, cutRuby.getX(), cutRuby.getY());

            state = State.IDLE;
            action = Action.START_CRAFTING;
        }
    }

    private void tickBankingRings(){

    }
}