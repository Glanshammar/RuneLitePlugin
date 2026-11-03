package com.main;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class AntiBotDetector {

    private final Client client;
    private final Runnable onCheckDetected;

    public AntiBotDetector(Client client, Runnable onCheckDetected) {
        this.client = client;
        this.onCheckDetected = onCheckDetected;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            String msg = event.getMessage();
            if (msg.contains("What is") || msg.contains("How many")
                    || msg.contains("Spell") || msg.contains("random")) {
                log.info("Check via chat: {}", msg);
                onCheckDetected.run();
            }
        }
    }
}