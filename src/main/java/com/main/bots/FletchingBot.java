package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.time.Duration;

@Slf4j
public class FletchingBot {
    private enum State { IDLE, WAITING, FLETCHING, CLICK_KNIFE, CLICK_LOG, CLICK_NOTED_LOGS, CLICK_BANK, CLICK_BOW, WAITING_LOGS }
    private State state;
    private final Client client;
    private final ApexeaConfig config;
    private final int BANK = 10355;
    private final int MAGIC_LOGS = 1513;
    private final int MAGIC_LOGS_NOTED = 1514;
    private final int MAGIC_LONGBOW = 70;
    private final int KNIFE = 946;
    private boolean running = false;

    public FletchingBot(Client client, ApexeaConfig config){
        this.client = client;
        this.config = config;
    }

    public void start(){
        state = State.IDLE;
        log.info("Fletching bot started.");
    }

    public void stop(){
        log.info("Fletching bot stopped.");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botFletching();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        // Route to the correct state machine on every tick.
        switch (state) {
            case IDLE:
                if (GameFunctions.countItem(MAGIC_LOGS) >= 25) {
                    state = State.CLICK_KNIFE;
                }
                break;

            case CLICK_KNIFE:
            case CLICK_LOG:
            case WAITING:
                tickFletching();
                break;

            case FLETCHING:
            case CLICK_BOW:
            case CLICK_BANK:
            case CLICK_NOTED_LOGS:
                tickBanking();
                break;
        }
    }

    private void tickFletching() {
        switch (state) {
            case CLICK_KNIFE:
                GameFunctions.clickInventoryItem(KNIFE);
                state = State.CLICK_LOG;
                GameFunctions.startTimer();
                break;

            case CLICK_LOG:
                if (GameFunctions.ifTimeWaited(Duration.ofMillis(300))) {
                    GameFunctions.clickInventoryItem(MAGIC_LOGS);
                    state = State.WAITING;
                }
                break;

            case WAITING:
                Widget widget = client.getWidget(46071824);
                if (widget == null) log.warn("Fletching interface is null.");
                if (widget != null && !widget.isHidden()) {
                    log.warn("Fletching interface found.");
                    GameFunctions.clickWidget(widget);
                    state = State.FLETCHING;
                }
                break;
        }
    }

    private void tickBanking() {
        switch (state) {
            case FLETCHING:
                if (GameFunctions.countItem(MAGIC_LOGS) == 0
                        && GameFunctions.countItem(MAGIC_LONGBOW) >= 25) {
                    state = State.CLICK_BOW;
                }
                break;

            case CLICK_BOW:
                GameFunctions.clickInventoryItem(MAGIC_LONGBOW);
                state = State.CLICK_BANK;
                GameFunctions.startTimer();
                break;

            case CLICK_BANK:
                ObjectScanner.findNearestGameObject(BANK);
                state = GameFunctions.countItem(MAGIC_LONGBOW) > 0
                        ? State.CLICK_NOTED_LOGS
                        : State.WAITING_LOGS;
                break;

            case CLICK_NOTED_LOGS:
                if (GameFunctions.countItem(MAGIC_LONGBOW) == 0) {
                    GameFunctions.clickInventoryItem(MAGIC_LOGS_NOTED);
                    state = State.CLICK_BANK;
                    GameFunctions.startTimer();
                }
                break;

            case WAITING_LOGS:
                if (GameFunctions.countItem(MAGIC_LOGS) >= 25) {
                    state = State.IDLE;
                }
                break;
        }
    }
}
