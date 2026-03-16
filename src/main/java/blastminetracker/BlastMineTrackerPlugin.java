package blastminetracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.awt.image.BufferedImage;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameObjectSpawned;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.ClientToolbar;

@Slf4j
@PluginDescriptor(
	name = "Blast Mine Tracker",
	tags = {"blast mine statistics", "blast mining", "improved blast mine", "mining", "blast mine tracker", "blast mine", "xiler"}
)

public class BlastMineTrackerPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Client client;

	//@Inject
	//private BlastMineTrackerConfig config;

	@Inject
	private BlastMineTrackerOreDistributionOverlay blastMineTrackerOreDistributionOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	private BlastMineTrackerPanel panel;

	private NavigationButton navButton;

	//@Inject
	//private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	private final Map<Integer, Map<String, Integer>> oreData = new LinkedHashMap<>();

	@Inject
	private BlastMineActionTracker actionTracker;

	final int excavationXP = 20;


	private void initializeOreData()
	{

		oreData.put(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER, new HashMap<>());
		oreData.get(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER).put("itemId", ItemID.COAL);
		oreData.get(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER).put("xp", 33);
		oreData.get(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER).put("price", 0);
		oreData.get(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER).put("amount", 0);

		oreData.put(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER, new HashMap<>());
		oreData.get(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER).put("itemId", ItemID.GOLD_ORE);
		oreData.get(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER).put("xp", 66);
		oreData.get(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER).put("price", 0);
		oreData.get(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER).put("amount", 0);

		oreData.put(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER, new HashMap<>());
		oreData.get(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER).put("itemId", ItemID.MITHRIL_ORE);
		oreData.get(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER).put("xp", 120);
		oreData.get(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER).put("price", 0);
		oreData.get(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER).put("amount", 0);

		oreData.put(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER, new HashMap<>());
		oreData.get(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER).put("itemId", ItemID.ADAMANTITE_ORE);
		oreData.get(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER).put("xp", 190);
		oreData.get(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER).put("price", 0);
		oreData.get(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER).put("amount", 0);

		oreData.put(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER, new HashMap<>());
		oreData.get(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER).put("itemId", ItemID.RUNITE_ORE);
		oreData.get(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER).put("xp", 260);
		oreData.get(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER).put("price", 0);
		oreData.get(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER).put("amount", 0);
	}


	@Override
	protected void startUp() throws Exception
	{
		// Build Panel
		panel = injector.getInstance(BlastMineTrackerPanel.class);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Blast Mine Tracker")
				.icon(icon)
				.priority(20)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		// Ore Distribution overlay
		overlayManager.add(blastMineTrackerOreDistributionOverlay);

		initializeOreData();
		panel.refreshState();

	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(blastMineTrackerOreDistributionOverlay);
		final Widget blastMineWidget = client.getWidget(InterfaceID.LovakengjBlastMiningHud.DATA);
		if (blastMineWidget != null)
		{
			blastMineWidget.setHidden(false);
		}

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
	}


	private void updateOreData(){
		int oresTotal = 0;

		for (Map.Entry<Integer, Map<String, Integer>> entry : oreData.entrySet()) {
			int varbitId = entry.getKey();
			Map<String, Integer> data = entry.getValue();

			int amount = client.getVarbitValue(varbitId);
			data.put("amount", amount);

			int itemId = data.get("itemId");
			int price = itemManager.getItemPrice(itemId);
			data.put("price", price);

			oresTotal += amount;
		}

		actionTracker.onGameTick(oresTotal); // action tracker needs them first!
		blastMineTrackerOreDistributionOverlay.updateData(oreData, oresTotal);
		panel.updateData(oreData);
	}
	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if (client.getGameState() != GameState.LOGGED_IN || panel == null)
		{
			return;
		}
		updateOreData();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		actionTracker.onGameStateChanged(event);
	}

	@Provides
	BlastMineTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlastMineTrackerConfig.class);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		actionTracker.onMenuOptionClicked(event);
	}

	public void excavation(){
		panel.excavation(excavationXP);
	}
	public void incrementDynamiteUsed(){
		panel.incrementDynamiteUsed();
	}

	public void collectOres(){
		panel.endSession();
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		actionTracker.onGameObjectSpawned(event);
	}

	public boolean isSessionActive()
	{
		return panel.isSessionActive();
	}
}
