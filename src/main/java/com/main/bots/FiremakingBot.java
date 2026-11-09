package com.main.bots;

import com.main.AntiBotDetector;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.util.List;
import java.util.Set;


public class FiremakingBot {
    private enum State { IDLE, FIREMAKING, BANKING }
    private static final int BANK_X = 3253;
    private static final int BANK_Y = 3419;
    private static final int BANK_CHEST = ObjectID.BANK_BOOTH_10583;
    private static final int FIRE = ObjectID.FORESTERS_CAMPFIRE;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private int notedLogsSlot;
    private ObjectScanner.Target bankTarget;
    private State state;

    public FiremakingBot(Client client, ApexeaConfig config, ObjectScanner objectScanner){
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
        this.state = State.IDLE;
    }

    public void start(){
        this.notedLogsSlot = GameFunctions.findSlotWithItem(1514); //Noted Magic logs
        this.bankTarget = findBank(client.getLocalPlayer());
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
            case IDLE:             tickIdle(); break;
            case FIREMAKING:         tickFiremaking(); break;
            case BANKING:          tickBanking(); break;
        }
    }

    private void tickIdle(){
        List<ObjectScanner.Target> fireList = objectScanner.findGameObjects(Set.of(FIRE));
        if (fireList.isEmpty()) return;
        GameFunctions.clickObject(fireList.get(0).getGameObject());
        state = State.FIREMAKING;
    }

    private void tickFiremaking(){
        if (GameFunctions.countItem(1513) == 0)
            state = State.BANKING;
    }

    private void tickBanking(){
        GameFunctions.clickInventorySlot(this.notedLogsSlot);
        GameFunctions.clickObject(this.bankTarget.getGameObject());
        state = State.IDLE;
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
