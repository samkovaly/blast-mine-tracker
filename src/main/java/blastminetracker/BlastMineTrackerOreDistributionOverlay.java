
package blastminetracker;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import java.awt.Point;

import java.util.Map;


class BlastMineTrackerOreDistributionOverlay extends OverlayPanel
{
    private final Client client;
    private final BlastMineTrackerConfig config;
    private final ItemManager itemManager;
    private boolean panelOn;

    private Map<Integer, Map<String, Integer>> oreData;
    private int oresTotal;


    @Inject
    private BlastMineTrackerOreDistributionOverlay(BlastMineTrackerPlugin plugin, Client client, BlastMineTrackerConfig config, ItemManager itemManager)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        this.client = client;
        this.config = config;
        this.itemManager = itemManager;
        this.panelOn = false;
        panelComponent.setOrientation(ComponentOrientation.HORIZONTAL);
        panelComponent.setGap(new Point(4, 0));
        addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Blast mine tracker ore distribution overlay");
    }

    public void updateData(Map<Integer, Map<String, Integer>> oreData, int oresTotal)
    {
        this.oreData = oreData;   // same reference
        this.oresTotal = oresTotal;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        final Widget blastMineWidget = client.getWidget(InterfaceID.LovakengjBlastMiningHud.DATA);
        if (blastMineWidget == null)
        {
            return null;
        }

        if (!config.showOreDistribution())
        {
            if (panelOn)
            {
                panelOn = false;
                blastMineWidget.setHidden(false);
            }
            return null;
        }

        panelOn = true;
        blastMineWidget.setHidden(true);
        panelComponent.getChildren().clear();

        if (oreData == null || oreData.isEmpty())
        {
            return super.render(graphics);
        }

        for (Map.Entry<Integer, Map<String, Integer>> entry : oreData.entrySet())
        {
            Map<String, Integer> data = entry.getValue();
            int itemId = data.get("itemId");
            int amount = data.get("amount");

            BufferedImage image = itemManager.getImage(itemId);
            panelComponent.getChildren().add(new OrePercentComponent(image, amount, oresTotal));
        }

        return super.render(graphics);
    }
}