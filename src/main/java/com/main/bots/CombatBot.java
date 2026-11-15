package com.main.bots;

import com.main.Apexea;
import com.main.ApexeaConfig;
import com.main.utils.GameFunctions;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.slayer.SlayerPluginService;

import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CombatBot {
    private enum State { IDLE, COMBAT, BANKING, GET_NEW_TASK, WAITING, ATTACKING, PICKUP_DROPS, PICKUP_WAITING }
    private State state;
    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private final SlayerPluginService slayerPluginService;

    private NPC currentTarget = null;
    private final Set<Integer> alchList = loadIds("alch_items.txt");
    private final Set<Integer> dropsList = loadIds("drops_items.txt");
    private final List<Integer> prayerPotions = List.of(143, 141, 139, 2434);
    private final Set<Integer> foodItems = Set.of(385, 391);

    private boolean running = false;

    public CombatBot(Client client, ApexeaConfig config, ObjectScanner objectScanner, SlayerPluginService slayerPluginService) throws IOException {
        this.client = client;
        this.config = config;
        this.objectScanner = objectScanner;
        this.slayerPluginService = slayerPluginService;
    }

    public void start() {
        log.info("Combat bot starting");
        state = State.IDLE;
    }
    public void stop() {
        log.info("Combat bot stopping");
    }

    private static Set<Integer> loadIds(String resource) throws IOException {
        try (var lines = new BufferedReader(
                new InputStreamReader(Objects.requireNonNull(
                        Apexea.class.getClassLoader().getResourceAsStream(resource))))) {
            return lines.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toUnmodifiableSet());
        }
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

        if (!player.isInteracting()) state = State.IDLE;
        if (player.isInteracting()) state = State.COMBAT;

        int prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
        if (prayerPoints <= 30)
            restorePrayer();
        if (playerHealth(player) <= 50)
            restoreHealth();

        if (state == State.PICKUP_DROPS) {
            handlePickupDrops(player);
            return;
        }

        if (config.botCombatScript() == ApexeaConfig.CombatScript.GEMSTONE_CRAB) botGemstoneCrab(player);
        if (config.botCombatScript() == ApexeaConfig.CombatScript.NEAREST && state.equals(State.IDLE)) botNearestNpc(player);
        if (config.botCombatScript() == ApexeaConfig.CombatScript.SLAYER && state.equals(State.IDLE)) botNearestNpc(player);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC deadNpc = (NPC) event.getActor();
        String npcName = deadNpc.getName();
        if (npcName == null) return;

        if ("Gemstone Crab".equals(npcName)) return;
        if (deadNpc.getInteracting() != client.getLocalPlayer()) return;
        if (deadNpc == currentTarget) currentTarget = null;
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
        // 1. If an NPC is already attacking the player, the player MUST attack it
        //    Single-way combat rejects any other target.
        NPC attacker = getNpcTargetingPlayer(player);
        if (attacker != null)
        {
            currentTarget = attacker;          // override stale target
            GameFunctions.clickNpc(attacker);
            state = State.COMBAT;
            log.info("Attacking mob. Going to COMBAT.");
            return;
        }

        // 2. Attack the current target if still valid.
        if (currentTarget != null
                && !currentTarget.isDead()
                && client.getNpcs().contains(currentTarget)) {
            GameFunctions.clickNpc(currentTarget);
            state = State.COMBAT;
            log.info("Attacking mob. Going to COMBAT.");
            return;
        }

        currentTarget = null;

        // 3. Pick the nearest matching NPC.
        Stream<NPC> stream = client.getNpcs().stream();
        if (config.botCombatScript() == ApexeaConfig.CombatScript.SLAYER) {
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
        state = State.COMBAT;
        currentTarget = nearest;
        log.info("Attacking mob. Going to COMBAT.");
    }

    private void restorePrayer(){
        for (int itemId : prayerPotions) {
            if (GameFunctions.clickInventoryItem(itemId)) break;
        }
    }

    private void restoreHealth(){
        for (int itemId : foodItems) {
            if (GameFunctions.clickInventoryItem(itemId)) break;
        }
    }

    private void handlePickupDrops(Player player) {
        // Wait for combat to finish (e.g. the death animation or an auto-retaliate target).
        if (player.isInteracting()) return;

        // Combined pickup list — dropsList / alchList are already Sets of item IDs,
        // and ObjectScanner.findNearestGroundItem takes a Set<Integer>, so no conversion needed.
        Set<Integer> allPickupIds = new HashSet<>(dropsList);
        allPickupIds.addAll(alchList);

        Optional<ObjectScanner.Target> item = objectScanner.findNearestGroundItem(allPickupIds);
        if (item.isPresent()) {
            ObjectScanner.Target t = item.get();
            log.info("Picking up {} at {}", t.getName(), t.getWorldPosition());
            GameFunctions.clickGroundItem(t.getTile(), t.getTileItem());
        } else {
            log.info("All drops collected — returning to IDLE");
            if (config.botCombatScript() == ApexeaConfig.CombatScript.SLAYER && slayerPluginService.getRemainingAmount() == 0) {
                state = State.GET_NEW_TASK;   // preserve slayer-task-completion handling
            } else {
                state = State.IDLE;
            }
        }
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

    private NPC getNpcTargetingPlayer(Player player)
    {
        final int MULTICOMBAT = 4605;
        if ((client.getVarbitValue(MULTICOMBAT) != 0)) return null;

        for (NPC npc : client.getNpcs())
        {
            if (npc == null || npc.isDead()) continue;
            if (npc.getInteracting() == player) return npc;
        }
        return null;
    }
}