package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class TemplateBot {
    private enum State { IDLE, BANKING, SKILLING }
    private State state;
    private final Client client;
    private final ApexeaConfig config;
    private boolean running = false;

    public TemplateBot(Client client, ApexeaConfig config){
        this.client = client;
        this.config = config;
    }

    public void start(){
        log.info("Bot has started.");
        state = State.IDLE;
    }

    public void stop(){
        log.info("Bot has stopped.");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botFletching();
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
            case SKILLING:
                tickSkilling(player);
                break;
            case BANKING:
                tickBanking(player);
                break;
        }
    }

    private void tickIdle(Player player){
        log.info("State = IDLE");
    }

    private void tickSkilling(Player player){
        log.info("State = SKILLING");
    }

    private void tickBanking(Player player){
        log.info("State = BANKING");
    }
}