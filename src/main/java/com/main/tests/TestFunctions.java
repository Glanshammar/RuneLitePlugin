package com.main.tests;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;

import java.awt.*;

@Slf4j
public class TestFunctions {
    private TestFunctions(){}

    private static volatile Client client;
    public static void init(Client c)  { client = c; }
    public static void destroy()       { client = null; }
    private static int slotID;


    public static void logInventoryItems(){
        ItemContainer invContainer = client.getItemContainer(InventoryID.INVENTORY);

        if (invContainer != null) {
            for (Item item : invContainer.getItems()) {
                if (item == null) continue;

                String name = client.getItemDefinition(item.getId()).getName();
                log.info("ID {}: {} x{}", item.getId(), name, item.getQuantity());
            }
        }

        slotID = invContainer.find(1513);
        log.info("Slot for Magic logs: {}", slotID);
    }

    public static void logInventoryItems2() {
        // 1. Get the parent inventory container.
        Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
        if (inventory == null) return; // Inventory tab not open.

        // 2. Get ALL child widgets (the I0, I1, I2... slots) at once.
        //    This avoids the pitfalls of getChild(index) returning null.
        WidgetItem[] children = (WidgetItem[]) inventory.getChildren();
        if (children == null || slotID < 0 || children.length == 0) {
            log.warn("Children are null/empty.");
            return;
        }

        // 3. Grab the specific slot widget and its bounds.
        Widget slot = children[slotID].getWidget();
        if (slot == null) {
            log.warn("Slot is null.");
            return;
        }

        Rectangle bounds = slot.getBounds();
        if (bounds == null) {
            log.warn("Bounds is null.");
            return;
        }

        // 4. Calculate the center.
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        log.info("Item X: {}, Y: {}", centerX, centerY);
    }
}
