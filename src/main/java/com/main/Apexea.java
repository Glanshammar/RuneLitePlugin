package com.main;

import javax.inject.Inject;

import com.google.inject.Provides;
import com.main.bots.*;
import com.main.utils.*;
import com.main.tests.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Apexea"
)
@PluginDependency(SlayerPlugin.class)
public class Apexea extends Plugin
{
	private static final String CONFIG_GROUP = "Apexea";
	private static final List<String> BOT_KEYS = List.of(
			"botWoodcutting",
			"botThieveStalls",
			"botCombat",
			"botFiremaking",
			"botFletching",
			"botHunter",
			"botCrafting"
	);
	@Inject private ApexeaConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	@Inject private SlayerPluginService slayerService;
	@Inject private OverlayManager overlayManager;
	@Inject private ClickIndicatorOverlay clickIndicatorOverlay;
	@Inject private KeyManager keyManager;
	private ObjectScanner objectScanner;
	private AntiBotDetector antiBotDetector;
	private WoodcuttingBot woodcuttingBot;
	private ThievingBot thievingBot;
	private CombatBot combatBot;
	private FletchingBot fletchingBot;
	private FiremakingBot firemakingBot;
	private HunterBot hunterBot;
	private MiniBots miniBots;
	private CraftingBot craftingBot;

	private final KeyListener keyListener = new KeyListener()
	{
		@Override
		public void keyPressed(KeyEvent e)
		{
			if (e.getKeyCode() == KeyEvent.VK_SHIFT
					&& e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT)
			{
				Point pos = client.getMouseCanvasPosition();
				log.info("Mouse canvas position: ({}, {})", pos.getX(), pos.getY());
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}

		@Override
		public void keyTyped(KeyEvent e)
		{
		}
	};

	@Override
	protected void startUp() throws IOException {
		objectScanner = new ObjectScanner(client, config);
		eventBus.register(objectScanner);
		antiBotDetector = new AntiBotDetector(client);
		eventBus.register(antiBotDetector);
		woodcuttingBot = new WoodcuttingBot(client, config, objectScanner);
		eventBus.register(woodcuttingBot);
		thievingBot = new ThievingBot(client, config, objectScanner);
		eventBus.register(thievingBot);
		combatBot = new CombatBot(client, config, objectScanner, slayerService);
		eventBus.register(combatBot);
		firemakingBot = new FiremakingBot(client, config, objectScanner);
		eventBus.register(firemakingBot);
		fletchingBot = new FletchingBot(client, config, objectScanner);
		eventBus.register(fletchingBot);
		hunterBot = new HunterBot(client, config, objectScanner);
		eventBus.register(hunterBot);
		miniBots = new MiniBots(client, config, objectScanner);
		eventBus.register(miniBots);
		craftingBot = new CraftingBot(client, config, objectScanner);
		eventBus.register(craftingBot);

		overlayManager.add(clickIndicatorOverlay);
		GameFunctions.init(client);
		TestFunctions.init(client);
		resetConfigurations();
		keyManager.registerKeyListener(keyListener);
	}

	@Override
	protected void shutDown() {
		woodcuttingBot.stop();
		eventBus.unregister(woodcuttingBot);
		eventBus.unregister(antiBotDetector);
		eventBus.unregister(objectScanner);
		eventBus.unregister(thievingBot);
		eventBus.unregister(combatBot);
		eventBus.unregister(firemakingBot);
		eventBus.unregister(fletchingBot);
		hunterBot.stop();
		eventBus.unregister(hunterBot);
		miniBots.stop();
		eventBus.unregister(miniBots);
		craftingBot.stop();
		eventBus.unregister(craftingBot);

		overlayManager.remove(clickIndicatorOverlay);
		ClickTracker.clear();
		GameFunctions.destroy();
		TestFunctions.destroy();
		resetConfigurations();
		keyManager.unregisterKeyListener(keyListener);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!CONFIG_GROUP.equals(event.getGroup())) {
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
		if ("botFiremaking".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			firemakingBot.start();
		}
		if ("botFletching".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			fletchingBot.start();
		}
		if ("botHunter".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			hunterBot.start();
		}
		if ("botCrafting".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			craftingBot.start();
		}
		if (event.getKey().equals("funcTesting") && config.funcTesting()) {
			TestFunctions.logInventoryItems();
		}

		if (event.getKey().equals("funcTesting2") && config.funcTesting2()) {
			TestFunctions.logInventoryItems2();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		ClickTracker.clear();
	}

	@Subscribe
	private void onMenuOpened(MenuOpened event)
	{
		for (MenuEntry entry : event.getMenuEntries())
		{
			log.info("option={}, target={}, action={}, id={}, itemId={}, p0={}, p1={}",
					entry.getOption(),
					entry.getTarget(),
					entry.getType(),
					entry.getIdentifier(),
					entry.getItemId(),
					entry.getParam0(),
					entry.getParam1());
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

	private void resetConfigurations(){
		configManager.setConfiguration(CONFIG_GROUP, "botWoodcutting", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botThieveStalls", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botCombat", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botFiremaking", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botFletching", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botHunter", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botCrafting", "false");
		configManager.setConfiguration(CONFIG_GROUP, "targetName", "");
	}

	@Provides
	ApexeaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ApexeaConfig.class);
	}
}
