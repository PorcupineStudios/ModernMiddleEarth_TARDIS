package com.porcupinestudios.lotrport.content.woodset;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The modern parts of LOTR's woods ({@code converter/woodsets.py}: entries marked {@code woodset} in blocks.json and
 * items.json): one {@link WoodType} per wood for its signs, the sign block entity types, the boats, and stripping
 * (an axe turns {@code <wood>_wood} and {@code <wood>_wood_beam} into their {@code stripped_} blocks).
 */
public final class WoodSets {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);

    /** Wood types by base name ("mallorn"), named {@code lotr_port:<base>}: made when this class loads. */
    private static final Map<String, WoodType> TYPES = new LinkedHashMap<>();
    /** Stripped block id by the id it is stripped from. */
    private static final Map<String, String> STRIPPED = new LinkedHashMap<>();

    static {
        for (JsonObject e : ContentData.read("blocks.json")) {
            String base = ContentData.str(e, "woodset", null);
            if (base == null) {
                continue;
            }
            TYPES.computeIfAbsent(base, b -> WoodType.register(new WoodType(LotrPort.MOD_ID + ":" + b,
                    BlockSetType.register(new BlockSetType(LotrPort.MOD_ID + ":" + b)))));
            String id = e.get("id").getAsString();
            if (ContentData.bool(e, "stripped", false) && id.startsWith("stripped_")) {
                STRIPPED.put(id.substring("stripped_".length()), id);
            }
        }
    }

    public static final Supplier<BlockEntityType<LotrSignBlockEntity>> SIGN = BLOCK_ENTITIES.register("sign",
            () -> BlockEntityType.Builder.of(LotrSignBlockEntity::new, blocks(LotrSignBlocks.Standing.class,
                    LotrSignBlocks.Wall.class)).build(null));
    public static final Supplier<BlockEntityType<LotrHangingSignBlockEntity>> HANGING_SIGN = BLOCK_ENTITIES.register(
            "hanging_sign", () -> BlockEntityType.Builder.of(LotrHangingSignBlockEntity::new,
                    blocks(LotrSignBlocks.Ceiling.class, LotrSignBlocks.WallHanging.class)).build(null));
    public static final Supplier<EntityType<LotrBoat>> BOAT = ENTITIES.register("boat",
            () -> EntityType.Builder.<LotrBoat>of(LotrBoat::new, MobCategory.MISC).sized(1.375f, 0.5625f)
                    .eyeHeight(0.5625f).clientTrackingRange(10).build("boat"));
    public static final Supplier<EntityType<LotrChestBoat>> CHEST_BOAT = ENTITIES.register("chest_boat",
            () -> EntityType.Builder.<LotrChestBoat>of(LotrChestBoat::new, MobCategory.MISC).sized(1.375f, 0.5625f)
                    .eyeHeight(0.5625f).clientTrackingRange(10).build("chest_boat"));

    private WoodSets() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(WoodSets::onStrip);
    }

    /** The wood type of a LOTR wood (its signs' look and sounds). */
    public static WoodType type(String base) {
        WoodType t = TYPES.get(base);
        return t != null ? t : WoodType.OAK;
    }

    /** Every LOTR wood type (the client registers their sign pictures and models). */
    public static Map<String, WoodType> types() {
        return Collections.unmodifiableMap(TYPES);
    }

    @SafeVarargs
    private static Block[] blocks(Class<? extends Block>... kinds) {
        return LotrBlocks.BY_ID.values().stream().map(Supplier::get)
                .filter(b -> java.util.Arrays.stream(kinds).anyMatch(k -> k.isInstance(b))).toArray(Block[]::new);
    }

    /** An axe on a LOTR log or beam: the stripped block, same axis. */
    private static void onStrip(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP) {
            return;
        }
        BlockState state = event.getState();
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String stripped = STRIPPED.get(id);
        if (stripped == null || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(LotrPort.MOD_ID)
                || !LotrBlocks.BY_ID.containsKey(stripped)) {
            return;
        }
        BlockState out = LotrBlocks.BY_ID.get(stripped).get().defaultBlockState();
        if (state.hasProperty(RotatedPillarBlock.AXIS) && out.hasProperty(RotatedPillarBlock.AXIS)) {
            out = out.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        event.setFinalState(out);
    }

    /** The stripped block for a log or beam id, or null (for tests). */
    public static String strippedOf(String id) {
        return STRIPPED.get(id);
    }
}
