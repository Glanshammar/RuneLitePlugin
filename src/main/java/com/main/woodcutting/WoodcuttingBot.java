package com.main.woodcutting;

import com.main.ApexeaConfig;
import com.main.ipc.IpcServer;
import com.main.data.WcStatusDto;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Slf4j
public class WoodcuttingBot {

    private enum State { IDLE, CHOPPING, WALKING_TO_BANK, BANKING }

    private static final Duration ACTION_COOLDOWN = Duration.ofMillis(700);

    private static final Set<Integer> TREE_IDS = Set.of(
            ObjectID.OAK_TREE,
            ObjectID.WILLOW_TREE,
            ObjectID.MAPLE_TREE,
            ObjectID.YEW_TREE,
            ObjectID.MAGIC_TREE,
            ObjectID.MAGIC_TREE_10834
    );

    private static final int BANK_CHEST = ObjectID.BANK_CHEST_14886;

    private static final Set<Integer> WC_ANIMATIONS = Set.of(
            AnimationID.WOODCUTTING_BRONZE,
            AnimationID.WOODCUTTING_IRON,
            AnimationID.WOODCUTTING_STEEL,
            AnimationID.WOODCUTTING_BLACK,
            AnimationID.WOODCUTTING_MITHRIL,
            AnimationID.WOODCUTTING_RUNE,
            AnimationID.WOODCUTTING_DRAGON
    );

    private final Client client;
    private final ApexeaConfig config;
    private final ObjectScanner objectScanner;
    private State state = State.IDLE;
    private Instant lastActionAt = Instant.EPOCH;
    private final IpcServer ipcServer;

    public WoodcuttingBot(Client client, IpcServer ipcServer, ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.ipcServer = ipcServer;
        this.objectScanner = objectScanner;
    }

    public void start() {
        state = State.IDLE;
        lastActionAt = Instant.EPOCH;
        objectScanner.objectIds.clear();
        objectScanner.addObjectIds(Set.of(14886, 10834));
    }

    public void stop() {
        state = State.IDLE;
        objectScanner.objectIds.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (!config.botWC()) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        objectScanner.publishObjects();
        publishStatus();

        /*
        if (Instant.now().isBefore(lastActionAt.plus(ACTION_COOLDOWN))) return;

        switch (state) {
            case IDLE:
                tickIdle(player);
                break;
            case CHOPPING:
                tickChopping(player);
                break;
            case WALKING_TO_BANK:
                tickWalkingToBank();
                break;
            case BANKING:
                tickBanking();
                break;
        }
         */
    }

    private void publishStatus() {
        if (ipcServer == null) return;

        Player player = client.getLocalPlayer();
        ipcServer.publishStatus(new WcStatusDto(
                isPlayerWoodcutting(player),
                isInventoryFull(),
                isBankOpen()
        ));
    }

    private void tryClickTree() {
        objectScanner.findNearestObject(TREE_IDS).ifPresent(tree -> {
            ObjectComposition def = client.getObjectDefinition(tree.getId());
            String name = (def != null && def.getName() != null) ? def.getName() : "Tree";
            log.info("Clicking tree: {}", name);
            state = State.CHOPPING;
            lastActionAt = Instant.now();
        });
    }

    private void tryClickBank() {
        objectScanner.findNearestObject(Set.of(BANK_CHEST)).ifPresent(bank -> {
            log.info("Clicking bank chest");
            state = State.WALKING_TO_BANK;
            lastActionAt = Instant.now();
        });
    }

    private void depositInventory() {
        Widget deposit = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (deposit == null) {
            log.warn("Deposit-inventory widget not found; falling back to no-op");
            lastActionAt = Instant.now();
            return;
        }
        log.info("Depositing inventory");
        lastActionAt = Instant.now();
    }

    public boolean isPlayerWoodcutting(Player player) {
        return player != null && WC_ANIMATIONS.contains(player.getAnimation());
    }

    public boolean isInventoryFull() {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        return inv != null && inv.count() == inv.size();
    }

    private boolean isBankOpen() {
        Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return w != null && !w.isHidden();
    }
}