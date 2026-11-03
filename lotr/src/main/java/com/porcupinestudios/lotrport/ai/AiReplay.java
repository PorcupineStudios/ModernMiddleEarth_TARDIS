package com.porcupinestudios.lotrport.ai;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Rebuilds what the AI did from the history log alone: who owns each colony and which rulers are at war. A past
 * rebuilt this way must match the live world (the phase 10 GameTest checks it).
 */
public final class AiReplay {
    /** Colony id -> owner, and wars as "a|b" with a before b. */
    public record Result(Map<UUID, UUID> owners, Set<String> wars) {}

    private AiReplay() {}

    public static Result replay(List<HistoryEntry> entries) {
        Map<UUID, UUID> owners = new TreeMap<>();
        Set<String> wars = new TreeSet<>();
        for (HistoryEntry e : entries) {
            CompoundTag d = e.data();
            if (e.type().equals(Colonies.FOUNDED_HISTORY) && d.hasUUID("colony") && d.hasUUID("founder")) {
                owners.put(d.getUUID("colony"), d.getUUID("founder"));
            } else if (e.type().equals(Colonies.TRANSFER_HISTORY) && d.hasUUID("colony")) {
                owners.put(d.getUUID("colony"), d.getUUID("to"));
            } else if (e.type().equals(AiRulers.RELATION_HISTORY)) {
                String pair = pair(d.getString("a"), d.getString("b"));
                if (d.getBoolean("war")) {
                    wars.add(pair);
                } else {
                    wars.remove(pair);
                }
            }
        }
        return new Result(owners, wars);
    }

    public static String pair(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}
