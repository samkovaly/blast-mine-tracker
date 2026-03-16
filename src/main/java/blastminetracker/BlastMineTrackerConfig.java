package blastminetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("blastminetracker")
public interface BlastMineTrackerConfig extends Config
{
	@ConfigItem(
			position = 0,
			keyName = "showOreDistribution",
			name = "Show ore distribution",
			description = "Configures whether or not the ore distribution overlay is displayed."
	)
	default boolean showOreDistribution()
	{
		return true;
	}

}