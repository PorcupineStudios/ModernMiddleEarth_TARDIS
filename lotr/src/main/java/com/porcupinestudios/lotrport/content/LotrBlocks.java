package com.porcupinestudios.lotrport.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.crafting.FactionTableBlock;
import com.porcupinestudios.lotrport.drink.KegBlock;
import com.porcupinestudios.lotrport.machine.DisplayBlock;
import com.porcupinestudios.lotrport.machine.EntJarBlock;
import com.porcupinestudios.lotrport.machine.KebabStandBlock;
import com.porcupinestudios.lotrport.machine.LotrChestBlock;
import com.porcupinestudios.lotrport.machine.MachineBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every converted LOTR block, registered from the generated {@code blocks.json}. Each entry names a kind (cube,
 * pillar, slab, stairs...) that picks the vanilla block class; numbers come straight from the old game.
 */
public final class LotrBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    /** Registered blocks by id, with their generated entry, in file order. */
    public static final Map<String, DeferredBlock<Block>> BY_ID = new LinkedHashMap<>();
    static final Map<String, JsonObject> ENTRIES = new LinkedHashMap<>();

    private LotrBlocks() {}

    public static void register(IEventBus modBus) {
        for (JsonObject e : ContentData.read("blocks.json")) {
            String id = e.get("id").getAsString();
            String kind = ContentData.str(e, "kind", "cube");
            Function<BlockBehaviour.Properties, Block> factory = factory(kind, e);
            BY_ID.put(id, BLOCKS.registerBlock(id, factory, properties(kind, e)));
            ENTRIES.put(id, e);
        }
        BLOCKS.register(modBus);
    }

    private static Function<BlockBehaviour.Properties, Block> factory(String kind, JsonObject e) {
        if (kind.equals("keg")) {
            return p -> new KegBlock(p, box(e)); // LOTR's barrel; a facing block too
        }
        String machine = ContentData.str(e, "machine", "");
        if (!machine.isEmpty()) {
            float[] box = box(e);
            return switch (machine) {
                case "forge", "oven", "millstone", "unsmeltery" -> p -> new MachineBlock(p, box, machine);
                case "plate", "weapon_rack", "armor_stand" -> p -> new DisplayBlock(p, box, machine);
                case "kebab_stand" -> p -> new KebabStandBlock(p, box);
                case "ent_jar" -> p -> new EntJarBlock(p, box);
                default -> p -> new LotrChestBlock(p, box);
            };
        }
        if (ContentData.bool(e, "facing", false)) {
            float[] box = "shaped".equals(kind) ? box(e) : null;
            return p -> new FacingBlock(p, box);
        }
        return switch (kind) {
            case "gate" -> p -> new GateBlock(p, ContentData.bool(e, "full", false));
            case "pillar" -> RotatedPillarBlock::new;
            case "slab" -> SlabBlock::new;
            case "stairs" -> p -> new StairBlock(Blocks.STONE.defaultBlockState(), p);
            case "wall" -> WallBlock::new;
            case "fence" -> FenceBlock::new;
            case "fence_gate" -> p -> new FenceGateBlock(WoodType.OAK, p);
            case "pane" -> IronBarsBlock::new;
            case "door" -> p -> new DoorBlock(BlockSetType.OAK, p);
            case "trapdoor" -> p -> new TrapDoorBlock(BlockSetType.OAK, p);
            case "ladder" -> LadderBlock::new;
            case "leaves" -> LeavesBlock::new;
            case "plant" -> PlantBlock::new;
            case "sapling" -> LotrSaplingBlock::new;
            case "torch" -> LotrTorchBlock::new;
            case "bed" -> LotrBedBlock::new;
            case "carpet" -> CarpetBlock::new;
            case "shaped" -> p -> new ShapedBlock(p, box(e));
            case "faction_table" -> FactionTableBlock::new;
            case "button" -> p -> new ButtonBlock(BlockSetType.STONE, 20, p);
            case "pressure_plate" -> p -> new PressurePlateBlock(BlockSetType.STONE, p);
            default -> Block::new;
        };
    }

    /** The machine a block is ({@code forge}, {@code plate}, {@code chest}...; see {@code machine/Machines}), or "". */
    public static String machine(String id) {
        JsonObject e = ENTRIES.get(id);
        return e == null ? "" : ContentData.str(e, "machine", "");
    }

    /** A block's {@code texture} (chest and kebab stand textures under {@code textures/entity/block/}), or null. */
    public static String texture(String id) {
        JsonObject e = ENTRIES.get(id);
        return e == null ? null : ContentData.str(e, "texture", null);
    }

    private static float[] box(JsonObject e) {
        if (!e.has("shape")) {
            return null;
        }
        JsonArray a = e.getAsJsonArray("shape");
        float[] box = new float[6];
        for (int i = 0; i < 6; i++) {
            box[i] = a.get(i).getAsFloat();
        }
        return box;
    }

    private static BlockBehaviour.Properties properties(String kind, JsonObject e) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of();
        float hardness = ContentData.num(e, "hardness", 1.5f);
        float resistance = ContentData.num(e, "resistance", 6f);
        if (hardness < 0) {
            p.strength(-1f, 3600000f).noLootTable();
        } else {
            p.strength(hardness, resistance);
        }
        int light = ContentData.integer(e, "light", 0);
        if (light > 0) {
            p.lightLevel(state -> light);
        }
        p.sound(sound(ContentData.str(e, "sound", "stone")));
        if (ContentData.bool(e, "requiresTool", false)) {
            p.requiresCorrectToolForDrops();
        }
        float friction = ContentData.num(e, "friction", 0.6f);
        if (friction != 0.6f) {
            p.friction(friction);
        }
        if (!ContentData.bool(e, "solid", true)) {
            p.noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false);
        }
        if (!ContentData.bool(e, "collision", true)) {
            p.noCollission();
        }
        switch (kind) {
            case "leaves" -> p.randomTicks().isValidSpawn((s, l, pos, type) -> false)
                    .pushReaction(PushReaction.DESTROY);
            case "plant", "sapling", "torch", "button", "pressure_plate" ->
                    p.noCollission().pushReaction(PushReaction.DESTROY);
            case "door", "trapdoor", "bed" -> p.pushReaction(PushReaction.DESTROY);
            case "gate" -> p.noOcclusion().isViewBlocking((s, l, pos) -> false);
            default -> { }
        }
        if (e.has("render")) {
            p.noOcclusion(); // drawn by a block entity renderer
        }
        switch (kind) {
            default -> { }
        }
        return p;
    }

    /** LOTR's treasure piles: their own break, place and step sounds (LOTRBlockTreasurePile). */
    private static final SoundType TREASURE = new DeferredSoundType(1f, 1f, lotr("block.treasure.break"),
            lotr("block.treasure.step"), lotr("block.treasure.place"), lotr("block.treasure.step"),
            lotr("block.treasure.step"));
    /** LOTR's plates: they shatter (LOTRBlockPlate), otherwise stone. */
    private static final SoundType PLATE = new DeferredSoundType(1f, 1f, lotr("block.plate.break"),
            () -> SoundEvents.STONE_STEP, () -> SoundEvents.STONE_PLACE, () -> SoundEvents.STONE_HIT,
            () -> SoundEvents.STONE_FALL);

    private static Supplier<SoundEvent> lotr(String id) {
        return () -> LotrSounds.get(id).map(Holder::value).orElse(SoundEvents.STONE_BREAK);
    }

    static SoundType sound(String old) {
        return switch (old) {
            case "lotr:treasure" -> TREASURE;
            case "lotr:plate" -> PLATE;
            case "wood" -> SoundType.WOOD;
            case "gravel" -> SoundType.GRAVEL;
            case "grass" -> SoundType.GRASS;
            case "cloth" -> SoundType.WOOL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "ladder" -> SoundType.LADDER;
            case "anvil" -> SoundType.ANVIL;
            case "glass" -> SoundType.GLASS;
            case "metal" -> SoundType.METAL;
            default -> SoundType.STONE;
        };
    }
}
