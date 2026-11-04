package com.main.data;

import net.runelite.api.coords.WorldPoint;

import java.util.Objects;

public final class PlayerDto {
    private final boolean inCombat;
    private final boolean loggedIn;
    private final int playerCurrentHp;
    private final int playerMaxHp;
    private final int playerAnimation;
    private final WorldPoint playerLocation;

    public PlayerDto(boolean inCombat, WorldPoint playerLocation, int playerCurrentHp,
                     int playerMaxHp, int playerAnimation, boolean loggedIn) {
        this.inCombat = inCombat;
        this.playerCurrentHp = playerCurrentHp;
        this.playerMaxHp = playerMaxHp;
        this.playerAnimation = playerAnimation;
        this.playerLocation = playerLocation;
        this.loggedIn = loggedIn;
    }

    public boolean inCombat() { return inCombat; }
    public int playerCurrentHp() { return playerCurrentHp; }
    public int playerMaxHp() { return playerMaxHp; }
    public int playerAnimation() { return playerAnimation; }
    public WorldPoint playerLocation() { return playerLocation; }
    public boolean playerLoggedIn() { return loggedIn; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PlayerDto)) return false;
        PlayerDto that = (PlayerDto) object;
        return inCombat == that.inCombat &&
                playerCurrentHp == that.playerCurrentHp &&
                playerMaxHp == that.playerMaxHp &&
                playerAnimation == that.playerAnimation &&
                loggedIn == that.loggedIn &&
                playerLocation == that.playerLocation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inCombat, playerCurrentHp, playerLocation, loggedIn);
    }

    @Override
    public String toString() {
        return "PlayerDto[inCombat=" + inCombat +
                ", playerCurrentHp=" + playerCurrentHp +
                ", playerMaxHp=" + playerMaxHp +
                ", playerLocation=" + playerLocation +
                ", loggedIn=" + loggedIn +
                ", playerAnimation=" + playerAnimation;
    }
}
