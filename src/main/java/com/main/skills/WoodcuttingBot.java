package com.main.skills;

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
import java.util.Set;

@Slf4j
public class WoodcuttingBot {
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
    private final IpcServer ipcServer;

    public WoodcuttingBot(Client client, IpcServer ipcServer, ApexeaConfig config, ObjectScanner objectScanner) {
        this.client = client;
        this.config = config;
        this.ipcServer = ipcServer;
        this.objectScanner = objectScanner;
    }

    public void start() {
        objectScanner.objectIds.clear();
        objectScanner.addObjectIds(Set.of(14886, 10834));
    }

    public void stop() {
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