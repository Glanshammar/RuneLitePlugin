package com.main;

import javax.inject.Inject;

import com.google.inject.Provides;
import com.main.bots.CombatBot;
import com.main.bots.ThievingBot;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.main.bots.WoodcuttingBot;

import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Apexea"
)
public class Apexea extends Plugin
{
	private static final String CONFIG_GROUP = "Apexea";
	private static final List<String> BOT_KEYS = List.of(
			"botWoodcutting",
			"botThieveStalls",
			"botCombat"
	);
	@Inject private ApexeaConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	private ObjectScanner objectScanner;
	private AntiBotDetector antiBotDetector;
	private WoodcuttingBot woodcuttingBot;
	private ThievingBot thievingBot;
	private CombatBot combatBot;
	@Override
	protected void startUp() {
		objectScanner = new ObjectScanner(client, config);
		eventBus.register(objectScanner);
		antiBotDetector = new AntiBotDetector(client);
		eventBus.register(antiBotDetector);
		woodcuttingBot = new WoodcuttingBot(client, config, objectScanner);
		eventBus.register(woodcuttingBot);
		thievingBot = new ThievingBot(client, config, objectScanner);
		eventBus.register(thievingBot);
		combatBot = new CombatBot(client, config, clientThread);
		eventBus.register(combatBot);
	}

	@Override
	protected void shutDown() {
		woodcuttingBot.stop();
		eventBus.unregister(woodcuttingBot);
		eventBus.unregister(antiBotDetector);
		eventBus.unregister(objectScanner);
		eventBus.unregister(thievingBot);
		eventBus.unregister(combatBot);

		configManager.setConfiguration(CONFIG_GROUP, "botWoodcutting", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botThieveStalls", "false");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!CONFIG_GROUP.equals(event.getGroup())) {
			return;
		}
		if (!BOT_KEYS.contains(event.getKey())) {
			return;
		}
		if (!Boolean.parseBoolean(event.getNewValue())) {
			return;
		}

		for (String otherKey : BOT_KEYS) {
			if (otherKey.equals(event.getKey())) {
				continue;
			}
			if (Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, otherKey))) {
				// A different bot is already running -> revert this change
				configManager.setConfiguration(CONFIG_GROUP, event.getKey(), false);
				sendConflictMessage(event.getKey(), otherKey);
				return;
			}
		}

		if ("botWoodcutting".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			woodcuttingBot.start();
		}
		if ("botThieveStalls".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			thievingBot.start();
		}
	}

	private void sendConflictMessage(String attempted, String alreadyRunning) {
		String msg = String.format(
				"Cannot enable '%s' while '%s' is already running. " +
						"Disable '%s' first.",
				attempted, alreadyRunning, alreadyRunning
		);

		client.addChatMessage(ChatMessageType.GAMEMESSAGE,
				"", "<col=ff0000>" + msg + "</col>", null);
	}

	@Subscribe
	public void onGameTick(GameTick event) {

	}

	@Provides
	ApexeaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ApexeaConfig.class);
	}
}
