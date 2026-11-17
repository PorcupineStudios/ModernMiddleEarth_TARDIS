package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * No vanilla (or other mods') monsters in Middle-earth (Aaron 2026-11-17): LOTR's orcs, wights, trolls and spiders are
 * its monsters. Natural, chunk, structure, patrol and reinforcement spawns of any {@code MONSTER}-category mob that
 * isn't ours are cancelled there (phantoms, pillager patrols and zombie reinforcements included). Spawn eggs,
 * commands, spawners and summons still work. Exceptions: entity type tag {@code lotr_port:allowed_in_middle_earth}.
 */
public final class NoMonsters {
    public static final TagKey<EntityType<?>> ALLOWED =
            TagKey.create(Registries.ENTITY_TYPE, LotrPort.id("allowed_in_middle_earth"));
    private static final Set<MobSpawnType> BLOCKED = Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE, MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.EVENT);

    private NoMonsters() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NoMonsters::onSpawn);
    }

    /** Whether a natural spawn of {@code type} by {@code how} is turned away in {@code level}. */
    public static boolean blocked(ServerLevel level, EntityType<?> type, MobSpawnType how) {
        return type.getCategory() == MobCategory.MONSTER && BLOCKED.contains(how)
                && !EntityType.getKey(type).getNamespace().equals(LotrPort.MOD_ID) && !type.is(ALLOWED)
                && LotrDimensions.isMiddleEarth(level);
    }

    private static void onSpawn(FinalizeSpawnEvent event) {
        if (event.getLevel().getLevel() instanceof ServerLevel level
                && blocked(level, event.getEntity().getType(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
        }
    }
}
