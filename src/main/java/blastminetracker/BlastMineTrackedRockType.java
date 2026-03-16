package blastminetracker;

import net.runelite.api.gameval.ObjectID;
import java.util.HashMap;
import java.util.Map;

public enum BlastMineTrackedRockType
{
    NORMAL(ObjectID.BLAST_MINING_WALL_01, ObjectID.BLAST_MINING_WALL_02),
    CHISELED(ObjectID.BLAST_MINING_WALL_CHISELED_01, ObjectID.BLAST_MINING_WALL_CHISELED_02),
    LOADED(ObjectID.BLAST_MINING_WALL_POT_01, ObjectID.BLAST_MINING_WALL_POT_02),
    LIT(ObjectID.BLAST_MINING_WALL_BURNING_01, ObjectID.BLAST_MINING_WALL_BURNING_02),
    EXPLODED(ObjectID.BLAST_MINING_WALL_DESTROYED_01, ObjectID.BLAST_MINING_WALL_DESTROYED_02);

    private static final Map<Integer, BlastMineTrackedRockType> LOOKUP = new HashMap<>();

    static
    {
        for (BlastMineTrackedRockType type : values())
        {
            for (int objectId : type.objectIds)
            {
                LOOKUP.put(objectId, type);
            }
        }
    }

    private final int[] objectIds;

    BlastMineTrackedRockType(int... objectIds)
    {
        this.objectIds = objectIds;
    }

    public static BlastMineTrackedRockType fromObjectId(int objectId)
    {
        return LOOKUP.get(objectId);
    }
}