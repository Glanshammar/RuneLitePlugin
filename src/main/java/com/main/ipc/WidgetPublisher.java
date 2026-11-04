package com.main.ipc;

import com.main.data.WidgetDto;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WidgetPublisher {
    private final Client client;
    private final IpcServer ipcServer;
    public List<WidgetDto> widgets;

    public WidgetPublisher(Client client, IpcServer ipcServer){
        this.client = client;
        this.ipcServer = ipcServer;
        this.widgets = new ArrayList<>();
    }

    public void start(){
        addWidget(widgets, "deposit", client.getWidget(InterfaceID.Bankmain.DEPOSITINV));
        addWidget(widgets, "alch", client.getWidget(InterfaceID.MagicSpellbook.HIGH_ALCHEMY));
        addWidget(widgets, "logout", client.getWidget(InterfaceID.LOGOUT, 69));
    }

    public void publishWidgets() {
        if (ipcServer == null) return;
        ipcServer.publishWidgets(widgets);
    }

    private void addWidget(List<WidgetDto> out, String name, Widget w) {
        if (w == null) return;

        boolean visible = !w.isHidden();
        Rectangle b = w.getBounds();

        // If hidden, bounds are usually all zeros — publish but mark invisible
        if (!visible) {
            out.add(new WidgetDto(name, -1, -1, -1, -1, 0, 0, false));
            return;
        }

        // Center in canvas space
        int canvasX = b.x + b.width / 2;
        int canvasY = b.y + b.height / 2;

        // Center in screen space (what Python needs for pyautogui-style clicks)
        int screenX = -1, screenY = -1;
        if (client.getCanvas() != null) {
            java.awt.Point screen = SwingUtilities.convertPoint(
                    client.getCanvas(),
                    new java.awt.Point(canvasX, canvasY),
                    null
            );
            screenX = screen.x;
            screenY = screen.y;
        }

        out.add(new WidgetDto(name, canvasX, canvasY, screenX, screenY,
                b.width, b.height, true));
    }
}
