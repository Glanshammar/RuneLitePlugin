package com.main;

import javax.inject.Inject;

import com.google.inject.Provides;
import com.main.bots.ThievingBot;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.main.bots.WoodcuttingBot;

@Slf4j
@PluginDescriptor(
	name = "Apexea"
)
public class Apexea extends Plugin
{
	private static final String CONFIG_GROUP = "Apexea";
	@Inject private ApexeaConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	private AntiBotDetector antiBotDetector;
	private WoodcuttingBot woodcuttingBot;
	private ThievingBot thievingBot;
	private ObjectScanner objectScanner;

	@Override
	protected void startUp() {
		objectScanner = new ObjectScanner(client, config);
		eventBus.register(objectScanner);
		antiBotDetector = new AntiBotDetector(client, this::onCheckDetected);
		eventBus.register(antiBotDetector);
		woodcuttingBot = new WoodcuttingBot(client, config, objectScanner);
		eventBus.register(woodcuttingBot);
		thievingBot = new ThievingBot(client, config, objectScanner);
		eventBus.register(thievingBot);
	}

	@Override
	protected void shutDown() {
		woodcuttingBot.stop();
		eventBus.unregister(woodcuttingBot);
		eventBus.unregister(antiBotDetector);
		eventBus.unregister(objectScanner);
		eventBus.unregister(thievingBot);

		configManager.setConfiguration(CONFIG_GROUP, "botWoodcutting", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botThievingStalls", "false");
	}


	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		log.info("MENU type={} id={} itemId={} p0={} p1={} opt='{}' target='{}'",
				event.getMenuAction(),
				event.getId(),
				event.getItemId(),
				event.getParam0(),
				event.getParam1(),
				event.getMenuOption(),
				event.getMenuTarget());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!CONFIG_GROUP.equals(event.getGroup())) {
			return;
		}

		if ("botWoodcutting".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			woodcuttingBot.start();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {

	}

	@Provides
	ApexeaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ApexeaConfig.class);
	}

	private void onCheckDetected() {
		log.warn("Anti-bot check triggered");
	}
}
