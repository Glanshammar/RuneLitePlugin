package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.ObjectScanner;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

public class TemplateBot {
    private enum State { IDLE, BANKING, SKILLING }
    private State state;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private boolean running = false;

    public TemplateBot(Client client, ApexeaConfig config, ObjectScanner objectScanner){
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
    }

    public void start(){
        state = State.IDLE;
    }

    public void stop(){
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botFletching();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        if (state.equals(State.IDLE)) tickFunctionOne();
        if (state.equals(State.SKILLING)) tickFunctionTwo();
    }

    private void tickFunctionOne(){

    }

    private void tickFunctionTwo(){

    }
}