package com.porcupinestudios.lotrport.content.farm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * LOTR's farming (missing-features task 32): crops ({@link LotrCropBlock}), berry bushes, corn and grapevines. Which
 * block is which comes from {@code converter/farming.py}; LOTR's numbers are {@code farming/settings.json}.
 */
public final class Farming {
    /** LOTR's growth numbers (read from LOTRBlockBerryBush, Corn and Grapevine). */
    public record Settings(int light, int berryDivisor, int berryMin, int berryMax, int cornHeight, int cornDivisor,
            int cornCobDivisor, int grapeHeight, int grapeDivisor, int grapeMin, int grapeMax) {
        public static final Settings DEFAULT = new Settings(9, 50, 1, 4, 3, 25, 100, 3, 25, 1, 3);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("light", 9).forGetter(Settings::light),
                Codec.INT.optionalFieldOf("berry_divisor", 50).forGetter(Settings::berryDivisor),
                Codec.INT.optionalFieldOf("berry_min", 1).forGetter(Settings::berryMin),
                Codec.INT.optionalFieldOf("berry_max", 4).forGetter(Settings::berryMax),
                Codec.INT.optionalFieldOf("corn_height", 3).forGetter(Settings::cornHeight),
                Codec.INT.optionalFieldOf("corn_divisor", 25).forGetter(Settings::cornDivisor),
                Codec.INT.optionalFieldOf("corn_cob_divisor", 100).forGetter(Settings::cornCobDivisor),
                Codec.INT.optionalFieldOf("grape_height", 3).forGetter(Settings::grapeHeight),
                Codec.INT.optionalFieldOf("grape_divisor", 25).forGetter(Settings::grapeDivisor),
                Codec.INT.optionalFieldOf("grape_min", 1).forGetter(Settings::grapeMin),
                Codec.INT.optionalFieldOf("grape_max", 3).forGetter(Settings::grapeMax))
                .apply(i, Settings::new));
    }

    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("farming", Settings.CODEC);

    private Farming() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SETTINGS));
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }

    /**
     * How well a plant at {@code pos} grows, as vanilla's and LOTR's getGrowthFactor: 1, plus each farmland block
     * under it and around (moist 3, dry 1; the ones around count a quarter).
     */
    public static float growth(BlockGetter level, BlockPos pos) {
        float f = 1f;
        BlockPos soil = pos.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState s = level.getBlockState(soil.offset(dx, 0, dz));
                float g = 0f;
                if (s.getBlock() instanceof FarmBlock) {
                    g = s.getValue(FarmBlock.MOISTURE) > 0 ? 3f : 1f;
                }
                f += dx != 0 || dz != 0 ? g / 4f : g;
            }
        }
        return f;
    }

    /** Whether there is light enough to grow (LOTR: block light above 8). */
    public static boolean lit(Level level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) >= settings().light();
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    /** Drops {@code count} of an item at a block, as a harvest by hand. */
    static void drop(Level level, BlockPos pos, Item item, int count) {
        if (count > 0) {
            Block.popResource(level, pos, new ItemStack(item, count));
        }
    }

}
