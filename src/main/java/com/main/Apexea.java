package com.main;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.main.ipc.IpcServer;
import com.main.ipc.WidgetPublisher;
import com.main.utils.ObjectScanner;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.main.woodcutting.WoodcuttingBot;

import java.io.IOException;

@Slf4j
@PluginDescriptor(
	name = "Apexea"
)
public class Apexea extends Plugin
{
	private static final int IPC_PORT = 8765;
	private static final String CONFIG_GROUP = "Apexea";
	@Inject private ApexeaConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	private IpcServer ipcServer;
	private AntiBotDetector antiBotDetector;
	private WoodcuttingBot woodcuttingBot;
	private ObjectScanner objectScanner;
	private WidgetPublisher widgetPublisher;

	@Override
	protected void startUp() {
		try {
			this.ipcServer = new IpcServer(IPC_PORT);
		} catch (IOException e) {
			log.warn("Could not start IPC server on port {}: {}", IPC_PORT, e.getMessage());
		}

		objectScanner = new ObjectScanner(client, ipcServer, config);
		eventBus.register(objectScanner);
		antiBotDetector = new AntiBotDetector(client, this::onCheckDetected);
		eventBus.register(antiBotDetector);
		woodcuttingBot = new WoodcuttingBot(client, ipcServer, config, objectScanner);
		eventBus.register(woodcuttingBot);
		widgetPublisher = new WidgetPublisher(client, ipcServer);
		eventBus.register(widgetPublisher);

		widgetPublisher.start();

		configManager.setConfiguration(CONFIG_GROUP, "botWC", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botCrabs", "false");
	}

	@Override
	protected void shutDown() {
		woodcuttingBot.stop();
		ipcServer.stop();
		eventBus.unregister(woodcuttingBot);
		eventBus.unregister(antiBotDetector);
		eventBus.unregister(objectScanner);
		eventBus.unregister(widgetPublisher);

		configManager.setConfiguration(CONFIG_GROUP, "botWC", "false");
		configManager.setConfiguration(CONFIG_GROUP, "botCrabs", "false");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!CONFIG_GROUP.equals(event.getGroup())) {
			return;
		}

		if ("botWC".equals(event.getKey())
				&& Boolean.parseBoolean(event.getNewValue())) {
			woodcuttingBot.start();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		widgetPublisher.publishWidgets();
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
