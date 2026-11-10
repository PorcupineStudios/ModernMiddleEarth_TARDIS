package com.porcupinestudios.lotrport.content.thrown;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrItems;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * LOTR's thrown weapons and missiles (missing-features task 28): spears, throwing axes and darts fly as
 * {@link ThrownWeapon}s carrying their item; pebbles, conkers, fire pots, mystery webs and plates as
 * {@link ThrownMissile}s; orc bombs burn down as {@link OrcBomb}s. Which item does what is {@code converter/thrown.py};
 * LOTR's numbers are {@code thrown/settings.json}.
 */
public final class Thrown {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownWeapon>> WEAPON = TYPES.register(
            "thrown_weapon", () -> EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20)
                    .build(LotrPort.MOD_ID + ":thrown_weapon"));
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownMissile>> MISSILE = TYPES.register(
            "thrown_missile", () -> EntityType.Builder.<ThrownMissile>of(ThrownMissile::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10)
                    .build(LotrPort.MOD_ID + ":thrown_missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<OrcBomb>> ORC_BOMB = TYPES.register(
            "orc_bomb", () -> EntityType.Builder.<OrcBomb>of(OrcBomb::new, MobCategory.MISC)
                    .fireImmune().sized(0.98f, 0.98f).clientTrackingRange(10).updateInterval(10)
                    .build(LotrPort.MOD_ID + ":orc_bomb"));

    private static final CodecJsonLoader<Map<String, Float>> SETTINGS =
            new CodecJsonLoader<>("thrown", Codec.unboundedMap(Codec.STRING, Codec.FLOAT));

    private Thrown() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SETTINGS));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(Thrown::dispensers));
    }

    /** One of LOTR's numbers from {@code thrown/settings.json}, or the given LOTR value until data loads. */
    public static float num(String key, float lotr) {
        Map<String, Float> s = SETTINGS.entries().get(LotrPort.id("settings"));
        Float v = s == null ? null : s.get(key);
        return v == null ? lotr : v;
    }

    /** LOTR's dispenser behaviours (LOTRDispenseSpear, ThrowingAxe, Dart, Pebble, Conker, FirePot, Plate, ...). */
    private static void dispensers() {
        LotrItems.BY_ID.forEach((id, holder) -> {
            Item item = holder.get();
            if (item instanceof ThrowingItem t) {
                DispenserBlock.registerBehavior(item, new DefaultDispenseItemBehavior() {
                    @Override
                    protected ItemStack execute(BlockSource source, ItemStack stack) {
                        Level level = source.level();
                        Direction facing = source.state().getValue(DispenserBlock.FACING);
                        Position at = DispenserBlock.getDispensePosition(source);
                        if (t.dispense(level, at, facing, stack.copyWithCount(1))) {
                            stack.shrink(1);
                            level.levelEvent(1002, source.pos(), 0);
                        }
                        return stack;
                    }
                });
            } else if (item instanceof net.minecraft.world.item.BlockItem bi
                    && bi.getBlock() instanceof OrcBombBlock) {
                // LOTRDispenseOrcBomb: lights the bomb in front of the dispenser
                DispenserBlock.registerBehavior(item, new DefaultDispenseItemBehavior() {
                    @Override
                    protected ItemStack execute(BlockSource source, ItemStack stack) {
                        Level level = source.level();
                        var pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                        level.addFreshEntity(new OrcBomb(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                                bi.getBlock().defaultBlockState(), null));
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.TNT_PRIMED,
                                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                        stack.shrink(1);
                        return stack;
                    }
                });
            }
        });
    }

    /** An item a dispenser can launch. */
    public interface ThrowingItem {
        boolean dispense(Level level, Position at, Direction facing, ItemStack one);
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }
}
