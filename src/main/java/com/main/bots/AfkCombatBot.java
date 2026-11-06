package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class AfkCombatBot {
    private static final Duration LOGOUT_RETRY_COOLDOWN = Duration.ofSeconds(2);

    private final Client client;
    private final ClientThread clientThread;
    private final ApexeaConfig config;

    private Instant lastCombatAt = Instant.EPOCH;
    private Instant lastLogoutAttemptAt = Instant.EPOCH;
    private boolean running = false;

    public AfkCombatBot(Client client, ApexeaConfig config, ClientThread clientThread) {
        this.client = client;
        this.config = config;
        this.clientThread = clientThread;
    }

    public void start() {
        log.info("AFK combat bot starting");
        lastCombatAt = Instant.now();
        lastLogoutAttemptAt = Instant.EPOCH;
    }

    public void stop() {
        log.info("AFK combat bot stopping");
    }

    // ============================================================
    //  Combat detection — any signal updates lastCombatAt
    // ============================================================
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!running) return;
        Player player = client.getLocalPlayer();
        if (player != null && event.getActor() == player) {
            lastCombatAt = Instant.now();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() == GameState.LOGIN_SCREEN) GameFunctions.login(client, config.username(), config.password());

        boolean shouldRun = config.botAfkCombat();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        if (player.getInteracting() != null) {
            lastCombatAt = Instant.now();
            return;
        }

        for (NPC npc : client.getNpcs()) {
            if (npc != null && npc.getInteracting() == player) {
                lastCombatAt = Instant.now();
                return;
            }
        }

        // --- out-of-combat check ---
        Duration outOfCombat = Duration.between(lastCombatAt, Instant.now());
        if (outOfCombat.getSeconds() < 13) return;
        if (Instant.now().isBefore(lastLogoutAttemptAt.plus(LOGOUT_RETRY_COOLDOWN))) return;

        lastLogoutAttemptAt = Instant.now();
        GameFunctions.logout(client, clientThread);
    }
}
