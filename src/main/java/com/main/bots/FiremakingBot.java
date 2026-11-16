package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;


public class FiremakingBot {
    private enum State { IDLE, FIREMAKING, CLICK_NOTED_LOGS, CLICK_BANK, CLICK_FIRE }
    private static final int BANK_X = 3253;
    private static final int BANK_Y = 3419;
    private static final int BANK_CHEST = ObjectID.BANK_BOOTH_10583;
    private static final int FIRE = ObjectID.FORESTERS_CAMPFIRE;
    private static final int MAGIC_LOGS = 1513;
    private static final int NOTED_LOGS = 1514;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private ObjectScanner.Target bankTarget;
    private State state;

    public FiremakingBot(Client client, ApexeaConfig config, ObjectScanner objectScanner){
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
        this.state = State.IDLE;
    }

    public void start(){
        this.bankTarget = findBank(client.getLocalPlayer());
    }

    public void stop(){
        this.bankTarget = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (AntiBotDetector.isDetected()) return;

        boolean running = config.botFiremaking();
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        switch (state) {
            case IDLE:
                tickIdle(); break;
            case FIREMAKING: case CLICK_FIRE:
                tickFiremaking(); break;
            case CLICK_NOTED_LOGS:
            case CLICK_BANK:
                tickBanking(); break;
        }
    }

    private void tickIdle(){
        if (GameFunctions.countItem(MAGIC_LOGS) == 0){
            state = State.CLICK_NOTED_LOGS;
            return;
        }
        List<ObjectScanner.Target> fireList = objectScanner.findGameObjects(Set.of(FIRE));
        if (fireList.isEmpty()) return;
        GameFunctions.clickObject(fireList.get(0).getGameObject());
        state = State.FIREMAKING;
    }

    private void tickFiremaking(){
        if (GameFunctions.countItem(1513) == 0)
            state = State.CLICK_NOTED_LOGS;
    }

    private void tickBanking(){
        if (state.equals(State.CLICK_NOTED_LOGS)){
            GameFunctions.clickInventoryItem(NOTED_LOGS);
            state = State.CLICK_BANK;
            return;
        }

        if (state.equals(State.CLICK_BANK)){
            GameFunctions.clickObject(this.bankTarget.getGameObject());
            state = State.CLICK_FIRE;
        }
    }

    private ObjectScanner.Target findBank(Player player) {
        int plane = player.getWorldLocation().getPlane();
        WorldPoint bankPos = new WorldPoint(BANK_X, BANK_Y, plane);
        for (ObjectScanner.Target t : objectScanner.findGameObjects(Set.of(BANK_CHEST))) {
            if (bankPos.equals(t.getWorldPosition()) && t.getGameObject() != null) {
                return t;
            }
        }
        return null;
    }
}
