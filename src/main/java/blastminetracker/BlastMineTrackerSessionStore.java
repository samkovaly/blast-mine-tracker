package blastminetracker;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import java.time.Instant;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.runelite.client.config.ConfigManager;


@Singleton
@Slf4j
public class BlastMineTrackerSessionStore
{
    private static final String GROUP = "blastminetracker";

    private static final String CURRENT_SESSION_KEY = "current_session";
    private static final String PREVIOUS_SESSION_PREFIX = "previous_session_";

    private final ConfigManager configManager;
    private final Gson gson;

    @Inject
    public BlastMineTrackerSessionStore(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    public void saveCurrentSession(SessionSnapshot snapshot)
    {
        String json = gson.toJson(snapshot);
        configManager.setConfiguration(GROUP, CURRENT_SESSION_KEY, json);
    }

    @Nullable
    public SessionSnapshot loadCurrentSession()
    {
        return readSessionByKey(CURRENT_SESSION_KEY);
    }

    public void finalizeCurrentSession()
    {
        SessionSnapshot current = loadCurrentSession();
        if (current == null)
        {
            return;
        }

        String archiveId = sanitizeForKey(current.getStartedAt());
        String archiveKey = PREVIOUS_SESSION_PREFIX + archiveId;

        configManager.setConfiguration(GROUP, archiveKey, gson.toJson(current));

        clearCurrentSession();
    }

    public void clearCurrentSession()
    {
        configManager.unsetConfiguration(GROUP, CURRENT_SESSION_KEY);
    }

    public List<SessionSnapshot> loadPreviousSessions()
    {
        List<SessionSnapshot> sessions = new ArrayList<>();

        for (String key : configManager.getConfigurationKeys(GROUP + "." + PREVIOUS_SESSION_PREFIX))
        {
            // key will look like: blastminetracker.previous_session_...
            String shortKey = key.substring((GROUP + ".").length());
            SessionSnapshot snapshot = readSessionByKey(shortKey);
            if (snapshot != null)
            {
                sessions.add(snapshot);
            }
        }

        sessions.sort(
                Comparator.comparing(
                        BlastMineTrackerSessionStore::sortStartedAt
                ).reversed()
        );

        return sessions;
    }

    @Nullable
    private SessionSnapshot readSessionByKey(String key)
    {
        String json = configManager.getConfiguration(GROUP, key);
        if (json == null || json.isEmpty())
        {
            return null;
        }

        try
        {
            return gson.fromJson(json, SessionSnapshot.class);
        }
        catch (JsonSyntaxException ex)
        {
            log.warn("Failed to parse session config for key {}", key, ex);
            return null;
        }
    }

    private static Instant sortStartedAt(SessionSnapshot s)
    {
        return Instant.parse(s.getStartedAt());
    }

    private static String sanitizeForKey(String value)
    {
        return value.replace(":", "_").replace(".", "_");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSnapshot
    {
        private String startedAt;

        private int ticksElapsed;
        private int dynamiteUsed;
        private double excavationXPGained;

        private String sessionTimeText;
        private String sessionStatusText;
        private String xpToCollectText;
        private String xpPerHourText;
        private String grossProfitText;
        private String dynamiteCostText;
        private String netProfitText;
        private String netGpPerHourText;

        private Map<Integer, Integer> oreAmounts = new LinkedHashMap<>();
    }
}