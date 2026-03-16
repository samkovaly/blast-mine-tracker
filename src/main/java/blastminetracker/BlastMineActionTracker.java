package blastminetracker;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;

@Slf4j
@Singleton
public class BlastMineActionTracker
{
    private final Client client;
    private final BlastMineTrackerPlugin plugin;

    private final Map<WorldPoint, BlastMineTrackedRockType> rocks = new HashMap<>();

    private final Map<WorldPoint, Integer> pendingExcavateClicks = new HashMap<>();
    private final Map<WorldPoint, Integer> pendingCavityClicks = new HashMap<>();

    int oresTotal = -1;

    @Inject
    public BlastMineActionTracker(BlastMineTrackerPlugin plugin, Client client)
    {
        this.plugin = plugin;
        this.client = client;
    }

    public void resetSession()
    {
        rocks.clear();
        pendingExcavateClicks.clear();
        pendingCavityClicks.clear();
        this.oresTotal = -1;
    }


    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        final String option = event.getMenuOption();
        final String target = event.getMenuTarget();

        if ("Excavate".equals(option) && target != null && target.contains("Hard rock"))
        {
            WorldView wv = client.getTopLevelWorldView();
            WorldPoint point = WorldPoint.fromScene(wv, event.getParam0(), event.getParam1(), wv.getPlane());
            pendingExcavateClicks.put(point, 0);

        }

        if ("Load".equals(option) && target != null && target.contains("Cavity"))
        {
            WorldView wv = client.getTopLevelWorldView();
            WorldPoint point = WorldPoint.fromScene(wv, event.getParam0(), event.getParam1(), wv.getPlane());
            pendingCavityClicks.put(point, 0);
        }

    }

    private void ageAndExpirePending(Map<WorldPoint, Integer> pendingClicks, int maxTicks)
    {
        pendingClicks.replaceAll((point, ticks) -> ticks + 1);
        pendingClicks.entrySet().removeIf(entry -> entry.getValue() > maxTicks);
    }

    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        final GameObject gameObject = event.getGameObject();
        final BlastMineTrackedRockType newType = BlastMineTrackedRockType.fromObjectId(gameObject.getId());

        if (newType == null)
        {
            return;
        }

        final WorldPoint worldPoint = gameObject.getWorldLocation();
        final BlastMineTrackedRockType oldType = rocks.get(worldPoint);

        if (oldType == null || oldType != newType)
        {
            rocks.put(worldPoint, newType);

            if (newType == BlastMineTrackedRockType.CHISELED)
            {
                Integer removed = pendingExcavateClicks.remove(worldPoint);
                if (removed != null)
                {
                    plugin.excavation();
                }
            }

            if (newType == BlastMineTrackedRockType.LOADED)
            {
                Integer removed = pendingCavityClicks.remove(worldPoint);
                if (removed != null)
                {
                    plugin.incrementDynamiteUsed();
                }
            }
        }
    }

    public void onGameTick(int oresTotal)
    {
        ageAndExpirePending(pendingExcavateClicks, 25);
        ageAndExpirePending(pendingCavityClicks, 25);

        if (this.oresTotal == -1){
            this.oresTotal = oresTotal; // initial setting (can be to 0 or to some previous oresTotal when loading into the game
        }else{
            if(oresTotal < this.oresTotal){

                if (plugin.isSessionActive()) {
                    plugin.collectOres();
                    resetSession();
                }
            }else{
                this.oresTotal = oresTotal;
            }
        }
    }

    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING
                || event.getGameState() == GameState.HOPPING
                || event.getGameState() == GameState.LOGIN_SCREEN)
        {
            resetSession();
        }
    }
}