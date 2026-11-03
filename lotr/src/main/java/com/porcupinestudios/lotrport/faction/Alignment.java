package com.porcupinestudios.lotrport.faction;

import com.porcupinestudios.lotrport.LotrConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.event.AlignmentChangeEvent;
import com.porcupinestudios.lotrport.player.PlayerLotrData;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Reads and changes alignment. Every change goes through {@link #set}, which fires {@link AlignmentChangeEvent} and
 * writes the change to the world history log. Alignment stays within +-{@link #MAX}, as in LOTR.
 */
public final class Alignment {
    public static final ResourceLocation HISTORY_TYPE = LotrPort.id("alignment_change");
    public static final float MAX = 10000f;

    private Alignment() {}

    public static float get(Player player, ResourceLocation faction) {
        return player.getData(LotrRegistries.PLAYER_DATA).alignment(faction);
    }

    public static void set(ServerPlayer player, ResourceLocation faction, float value) {
        value = Math.max(-MAX, Math.min(MAX, value));
        PlayerLotrData data = player.getData(LotrRegistries.PLAYER_DATA);
        float old = data.alignment(faction);
        if (old == value) {
            return;
        }
        player.setData(LotrRegistries.PLAYER_DATA, data.withAlignment(faction, value));
        if (LotrConfig.RECORD_ALIGNMENT_HISTORY.get()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player.getUUID());
            tag.putString("faction", faction.toString());
            tag.putFloat("old", old);
            tag.putFloat("new", value);
            long time = player.server.overworld().getGameTime();
            HistoryLog.get(player.server).append(new HistoryEntry(time, HISTORY_TYPE,
                    player.level().dimension(), Optional.of(player.blockPosition()), tag));
        }
        NeoForge.EVENT_BUS.post(new AlignmentChangeEvent(player, faction, old, value));
    }

    public static void add(ServerPlayer player, ResourceLocation faction, float amount) {
        set(player, faction, get(player, faction) + amount);
    }
}
