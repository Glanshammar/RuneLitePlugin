package com.main;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.main.woodcutting.TreeDetector;
import com.main.woodcutting.TreeOverlay;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ObjectID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Apexea"
)
public class Apexea extends Plugin
{
	Set<Integer> treeIds = Set.of(
			ObjectID.OAK_TREE,
			ObjectID.WILLOW_TREE,
			ObjectID.MAPLE_TREE,
			ObjectID.YEW_TREE,
			ObjectID.MAGIC_TREE,
			ObjectID.MAGIC_TREE_10834
	);

	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;
	@Inject
	private OverlayManager overlayManager;
	private AntiBotDetector antiBotDetector;
	private TreeDetector treeDetector;
	private TreeOverlay treeOverlay;
	private List<TreeDetector.TreeInfo> visibleTrees = Collections.emptyList();

	@Override
	protected void startUp() {
		antiBotDetector = new AntiBotDetector(client, this::onCheckDetected);
		eventBus.register(antiBotDetector);
		treeDetector = new TreeDetector(client, treeIds);
		treeOverlay = new TreeOverlay(this::getVisibleTrees);
		overlayManager.add(treeOverlay);
	}

	@Override
	protected void shutDown() {
		eventBus.unregister(antiBotDetector);
		eventBus.unregister(eventBus);
		eventBus.unregister(treeDetector);
		overlayManager.remove(treeOverlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {

	}

	@Subscribe
	public void onGameTick(GameTick event) {
		visibleTrees = treeDetector.findTrees();
	}

	@Provides
	ApexeaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ApexeaConfig.class);
	}

	private void onCheckDetected() {
		log.warn("Anti-bot check triggered — pausing bot");
	}

	private List<TreeDetector.TreeInfo> getVisibleTrees() {
		return visibleTrees;
	}
}
