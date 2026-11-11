package com.main.bots;

import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.slayer.SlayerPluginService;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class CombatBot {
    private enum State { IDLE, COMBAT, BANKING, GET_NEW_TASK, WAITING, ATTACKING, PICKUP_DROPS, RESTORING }
    private State state;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private SlayerPluginService slayerPluginService;

    private NPC currentTarget = null;
    private String slayerTaskName;
    private int slayerTaskAmount;
    private final Set<Integer> dropsList = Set.of(561, 2726, 2720, 13495, 565);
    private final Set<Integer> alchList = Set.of(1163, 1127, 1079, 1201, 1333, 1303, 44, 1149, 4087, 1123, 1393, 1399, 1391, 1397, 1395);
    private final List<Integer> prayerPotions = List.of(143, 141, 139, 2434);
    private final Set<Integer> foodItems = Set.of(385, 391);

    private boolean running = false;

    public CombatBot(Client client, ApexeaConfig config, ObjectScanner objectScanner, SlayerPluginService slayerPluginService) {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
        this.slayerPluginService = slayerPluginService;
        this.state = State.IDLE;
    }

    public void start() {
        log.info("Combat bot starting");
        if (config.botCombatScript() == ApexeaConfig.CombatScript.SLAYER) {
            slayerTaskName = slayerPluginService.getTask();
            slayerTaskAmount = slayerPluginService.getRemainingAmount();
            log.info("Slayer task: {} - Amount left: {}", slayerTaskName, slayerTaskAmount);
        }
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

        int prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
        if (prayerPoints <= 30)
            restorePrayer();
        if (playerHealth(player) <= 50)
            restoreHealth();

        if (config.botCombatScript() == ApexeaConfig.CombatScript.GEMSTONE_CRAB) botGemstoneCrab(player);
        if (config.botCombatScript() == ApexeaConfig.CombatScript.NEAREST && !player.isInteracting()) botNearestNpc(player);
        if (config.botCombatScript() == ApexeaConfig.CombatScript.SLAYER && !player.isInteracting()) botNearestNpc(player);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC deadNpc = (NPC) event.getActor();
        String npcName = deadNpc.getName();

        if (deadNpc.getInteracting() == client.getLocalPlayer()) {
            assert npcName != null;
            currentTarget = null;
            if (npcName.equals(slayerTaskName)) {
                slayerTaskAmount = slayerPluginService.getRemainingAmount();
                if (slayerTaskAmount == 0) {
                    state = State.GET_NEW_TASK;
                    slayerTaskName = null;
                }
            }
        }
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
            return;
        }
        if (state.equals(State.COMBAT) && !player.isInteracting()) {
            log.info("State -> Idle");
            state = State.IDLE;
            return;
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

    public void botNearestNpc(Player player) {
        if (currentTarget != null && currentTarget.isInteracting()) GameFunctions.clickNpc(currentTarget);

        Stream<NPC> stream = client.getNpcs().stream();
        if (config.botCombatScript() == ApexeaConfig.CombatScript.SLAYER){
            if (slayerTaskAmount == 0) return;
            stream = slayerPluginService.getTargets().stream();
        } else if (!config.botTargetName().isEmpty()) {
            stream = stream.filter(npc -> npc.getName().contains(config.botTargetName()));
        }

        NPC nearest = stream
                .filter(npc -> !npc.isDead())
                .min(Comparator.comparingInt(npc -> {
                    int dx = npc.getLocalLocation().getX() - player.getLocalLocation().getX();
                    int dy = npc.getLocalLocation().getY() - player.getLocalLocation().getY();
                    return dx * dx + dy * dy;
                }))
                .orElse(null);

        if (nearest == null) {
            log.warn("No mob found.");
            return;
        }
        GameFunctions.clickNpc(nearest);
        state = State.ATTACKING;
        currentTarget = nearest;
    }

    private void restorePrayer(){
        if (GameFunctions.ifTimeWaited(Duration.ofSeconds(3))){
            GameFunctions.resetTimer();
            return;
        }

        for (int itemId : prayerPotions) {
            int slot = GameFunctions.findSlotWithItem(itemId);
            if (slot != -1) {
                GameFunctions.clickInventorySlot(slot);
                GameFunctions.startWaitTimer();
                break;
            }
        }
    }

    private void restoreHealth(){

    }

    private int playerHealth(Player player){ return Math.round((float) ((player.getHealthRatio() - 1) * client.getRealSkillLevel(Skill.HITPOINTS)) / (player.getHealthScale() - 1)); }

    private boolean checkForDrops(){
        return false;
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
}