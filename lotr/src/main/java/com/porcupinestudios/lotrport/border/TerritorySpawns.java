package com.porcupinestudios.lotrport.border;

import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import com.porcupinestudios.lotrport.npc.NpcType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Who spawns in land that changed hands (LOTR's conquest spawning): {@link com.porcupinestudios.lotrport.npc.NpcSpawner}
 * asks here for the table of a spot after looking up the biome's. Land still with its original holder keeps the
 * biome's table untouched. In taken land, with {@code conquered_share} the holder's own people come
 * ({@code border_spawns/<faction>}; a kingdom sends its culture's people), else the biome's, with the holder's enemies
 * weighted by {@code enemy_factor}. Then {@link Event} lets other code (KubeJS, other mods, later systems: a kingdom's
 * own races, garrisons) change or clear the table.
 */
public final class TerritorySpawns {
    private TerritorySpawns() {}

    /**
     * Posted for every people spawn attempt in Middle-earth (after the default rules). {@link #setTable} replaces what
     * spawns; an empty table entries list means nothing spawns.
     */
    public static final class SpawnEvent extends Event {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Optional<ResourceLocation> holder;
        private final Optional<ResourceLocation> original;
        private final NpcSpawns.Table biomeTable;
        private NpcSpawns.Table table;

        public SpawnEvent(ServerLevel level, BlockPos pos, Optional<ResourceLocation> holder,
                Optional<ResourceLocation> original, NpcSpawns.Table biomeTable, NpcSpawns.Table table) {
            this.level = level;
            this.pos = pos;
            this.holder = holder;
            this.original = original;
            this.biomeTable = biomeTable;
            this.table = table;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos pos() {
            return pos;
        }

        /** Who holds the land now. */
        public Optional<ResourceLocation> holder() {
            return holder;
        }

        /** Who held it on the original map. */
        public Optional<ResourceLocation> original() {
            return original;
        }

        /** Whether the land changed hands since the original map. */
        public boolean taken() {
            return !holder.equals(original);
        }

        public NpcSpawns.Table biomeTable() {
            return biomeTable;
        }

        public NpcSpawns.Table table() {
            return table;
        }

        public void setTable(NpcSpawns.Table table) {
            this.table = table;
        }
    }

    /** The table to spawn from at {@code pos}, given the biome's. */
    public static NpcSpawns.Table adjust(ServerLevel level, BlockPos pos, NpcSpawns.Table biome, RandomSource random) {
        Optional<ResourceLocation> holder = Borders.ownerAt(level, pos);
        Optional<ResourceLocation> original = Borders.baseOwnerAt(level, pos);
        NpcSpawns.Table table = biome;
        if (holder.isPresent() && !holder.equals(original)) {
            table = taken(level.getServer(), holder.get(), biome, random);
        }
        if (!com.porcupinestudios.lotrport.world.LotrDimensions.isMiddleEarth(level)) {
            return table;
        }
        SpawnEvent event = new SpawnEvent(level, pos, holder, original, biome, table);
        NeoForge.EVENT_BUS.post(event);
        return event.table();
    }

    /** The default rule for land the holder took. */
    static NpcSpawns.Table taken(MinecraftServer server, ResourceLocation holder, NpcSpawns.Table biome,
            RandomSource random) {
        BorderData.Settings s = BorderData.settings();
        Optional<NpcSpawns.Table> own = BorderData.spawns(peopleOf(server, holder));
        if (own.isPresent() && !own.get().entries().isEmpty() && random.nextFloat() < s.conqueredShare()) {
            return own.get();
        }
        List<NpcSpawns.Entry> entries = new ArrayList<>();
        for (NpcSpawns.Entry e : biome.entries()) {
            Optional<ResourceLocation> f = NpcData.type(e.npc()).flatMap(NpcType::faction);
            boolean enemy = f.isPresent() && Factions.relation(server, holder, f.get()).isBad();
            int w = enemy ? Math.round(e.weight() * s.enemyFactor()) : e.weight();
            if (w > 0) {
                entries.add(new NpcSpawns.Entry(e.npc(), w, e.min(), e.max(), e.dark()));
            }
        }
        return new NpcSpawns.Table(entries, biome.cap(), biome.chance(), biome.interval(), biome.hostilesInDay(),
                biome.radius(), biome.countOnlyAnimals(), biome.generationChance());
    }

    /** Whose people a holder sends: a great power its own, a kingdom its culture's. */
    public static ResourceLocation peopleOf(MinecraftServer server, ResourceLocation holder) {
        return Realms.get(server).get(holder).flatMap(Realms.Realm::culture).orElse(holder);
    }
}
