package com.main.ipc;

import com.main.data.PlayerDto;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

@Slf4j
public class PlayerPublisher {
    private final Client client;
    private final IpcServer ipcServer;

    public PlayerPublisher(Client client, IpcServer ipcServer) {
        this.client = client;
        this.ipcServer = ipcServer;
    }

    public void publish() {
        if (ipcServer == null) return;

        boolean isOnline = client.getGameState() != GameState.LOGIN_SCREEN;

        Player player = client.getLocalPlayer();
        if (player == null) {
            ipcServer.publishCombat(new PlayerDto(false, new WorldPoint(0, 0, 0), 10, 10, -1, true));
            return;
        }

        int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int baseHp    = client.getRealSkillLevel(Skill.HITPOINTS);
        int anim      = player.getAnimation();
        Actor interacting = player.getInteracting();
        boolean inCombat = interacting instanceof NPC || interacting instanceof Player;

        ipcServer.publishCombat(new PlayerDto(inCombat, player.getWorldLocation(), currentHp, baseHp, anim, isOnline));
    }
}