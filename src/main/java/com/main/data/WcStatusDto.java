package com.main.data;

import java.util.Objects;

public final class WcStatusDto {
    private final boolean woodcutting;
    private final boolean inventoryFull;
    private final boolean bankOpen;

    public WcStatusDto(boolean woodcutting, boolean inventoryFull, boolean bankOpen) {
        this.woodcutting = woodcutting;
        this.inventoryFull = inventoryFull;
        this.bankOpen = bankOpen;
    }

    public boolean woodcutting() { return woodcutting; }
    public boolean inventoryFull() { return inventoryFull; }
    public boolean bankOpen() { return bankOpen; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WcStatusDto)) return false;
        WcStatusDto that = (WcStatusDto) o;
        return woodcutting == that.woodcutting &&
                inventoryFull == that.inventoryFull &&
                bankOpen == that.bankOpen;
    }

    @Override
    public int hashCode() {
        return Objects.hash(woodcutting, inventoryFull, bankOpen);
    }

    @Override
    public String toString() {
        return "StatusDto[woodcutting=" + woodcutting +
                ", inventoryFull=" + inventoryFull +
                ", bankOpen=" + bankOpen + "]";
    }
}