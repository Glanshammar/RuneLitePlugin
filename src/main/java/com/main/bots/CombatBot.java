package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class CombatBot {
    private enum State { IDLE, COMBAT, BANKING, TRAVELING, WAITING, ATTACKING }
    private State state;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;

    private boolean running = false;

    public CombatBot(Client client, ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
        this.state = State.IDLE;
    }

    public void start() {
        log.info("Combat bot starting");
    }
    public void stop() {
        log.info("Combat bot stopping");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean shouldRun = config.botCombat();
        if (shouldRun != running) {
            if (shouldRun) start(); else stop();
            running = shouldRun;
        }
        if (!running) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        if (config.botCombatScript() == ApexeaConfig.CombatScript.GEMSTONE_CRAB) botGemstoneCrab(player);
        if (config.botCombatScript() == ApexeaConfig.CombatScript.NEAREST && getCurrentTarget() == null) botNearestNpc();
    }

    private NPC getCurrentTarget() {
        Player local = client.getLocalPlayer();
        return client.getNpcs().stream()
                .filter(NPC::isInteracting)
                .filter(npc -> npc.getInteracting() == local)
                .findFirst()
                .orElse(null);
    }

    private int getNpcHp(NPC npc) {
        int healthRatio = npc.getHealthRatio();   // 0.healthScale, or -1 if unknown
        int healthScale = npc.getHealthScale();   // usually 30, higher for bosses
        if (healthRatio < 0 || healthScale <= 0) {
            return -1; // unknown
        }

        int maxHp = npc.getComposition().getStats()[NPCComposition.STAT_HITPOINTS];
        return (int) Math.round((double) healthRatio / healthScale * maxHp);
    }

    private int getMaxHp(NPC npc) {
        return npc.getComposition().getStats()[NPCComposition.STAT_HITPOINTS];
    }

    public void botGemstoneCrab(Player player) {
        // Crab exist - attack it
        if (!state.equals(State.COMBAT)) {
            Optional<NPC> crab = objectScanner.findNearestNpc(Set.of(14779));
            if (crab.isPresent()) {
                log.info("Clicking Gemstone Crab");
                GameFunctions.clickNpc(crab.get());
                state = State.ATTACKING;
            }
        }

        // Checks
        if (!state.equals(State.COMBAT) && player.isInteracting()) {
            log.info("State -> Combat");
            state = State.COMBAT;
        }
        if (state.equals(State.COMBAT) && !player.isInteracting()) {
            log.info("State -> Idle");
            state = State.IDLE;
        }
        if (state.equals(State.ATTACKING)) return;
        if (state.equals(State.COMBAT)) return;
        if (state.equals(State.WAITING)) return;

        // Click the cave to travel to the next crab
        Optional<GameObject> caveObject = objectScanner.findNearestObject(Set.of(57631));
        if (caveObject.isPresent()) {
            log.info("Gemstone Crab not found — clicking cave");
            GameFunctions.clickObject(caveObject.get());
            state = State.WAITING;
        } else {
            log.debug("Neither Gemstone Crab nor cave object in scene");
        }
    }

    public void botNearestNpc(Set<Integer> npcIds) {
        Player player = client.getLocalPlayer();
        if (player.isInteracting()) {
            if (state == State.ATTACKING)
                state = State.COMBAT;
            return;
        }

        int currentHp = client.getVarpValue(45);
        int prayerPoints    = client.getVarpValue(1352);

        if (currentHp <= 45) {
            eatFood();
            return;
        }
        if (prayerPoints <= 25) {
            prayerPotion();
            return;
        }

        List<ObjectScanner.Target> npcs = objectScanner.findNpcs(npcIds);

        for (ObjectScanner.Target target : npcs) {
            NPC npc = target.getNpc();
            if (npc == null) continue;
            if (npc.isDead()) continue;

            log.info("Attacking {} ", npc.getName());
            GameFunctions.clickNpc(npc);
            state = State.ATTACKING;
            return;
        }

        log.debug("No attackable NPCs found{}",
                npcIds == null || npcIds.isEmpty() ? "" : " matching " + npcIds);
    }

    public void botNearestNpc() {
        botNearestNpc(null);
    }

    private void prayerPotion(){

    }

    private void eatFood(){

    }
}