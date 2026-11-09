package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.GateBlock;
import com.porcupinestudios.lotrport.drink.Drinks;
import com.porcupinestudios.lotrport.drink.KegBlock;
import com.porcupinestudios.lotrport.drink.KegBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR's gates open and shut as one gate (by hand and by redstone), keep the gate behind shut, connect their textures
 * as LOTR's and are doors to the pathfinder; kegs brew LOTR's ale to potent, pour it, pipe it as a fluid with its
 * strength into a vessel, and load LOTR's structure barrels; drinks do what LOTR's did.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KegGateTests {
    private KegGateTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    @GameTest(template = "empty")
    public static void gatesOpenAsOne(GameTestHelper helper) {
        Block gate = BuiltInRegistries.BLOCK.get(LotrPort.id("gondorian_gate"));
        helper.assertTrue(gate instanceof GateBlock, "gondorian_gate is not a gate");
        ServerLevel level = helper.getLevel();
        BlockState shut = gate.defaultBlockState().setValue(GateBlock.FACING, Direction.NORTH);
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                level.setBlock(base.offset(dx, dy, 0), shut, 3);
            }
        }
        BlockPos behind = base.south();
        level.setBlock(behind, shut, 3);
        helper.assertTrue(GateBlock.connects(level, base, shut, base.east()), "gate blocks side by side don't connect");
        helper.assertFalse(GateBlock.connects(level, base, shut, behind), "a gate behind connects");
        helper.assertTrue(shut.getBlockPathType(level, base, null) == PathType.DOOR_WOOD_CLOSED,
                "a shut gate isn't a door to the pathfinder");

        helper.assertTrue(GateBlock.setOpen(level, base, true), "the gate didn't open");
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                helper.assertTrue(level.getBlockState(base.offset(dx, dy, 0)).getValue(GateBlock.OPEN),
                        "part of the gate stayed shut");
            }
        }
        helper.assertFalse(level.getBlockState(behind).getValue(GateBlock.OPEN), "the gate behind opened too");
        BlockState open = level.getBlockState(base);
        helper.assertTrue(open.getCollisionShape(level, base).isEmpty(), "an open gate still blocks");
        helper.assertTrue(level.getBlockState(base).getBlock() == gate, "opening the gate removed it");
        helper.assertTrue(GateBlock.connects(level, base, open, base.below())
                        && GateBlock.connects(level, base, open, base.below().east()),
                "an open gate's frame doesn't stop at the ground");
        helper.assertTrue(GateBlock.setOpen(level, base, false), "the gate didn't shut");

        level.setBlock(base.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(level.getBlockState(base.offset(1, 1, 0)).getValue(GateBlock.OPEN),
                "redstone didn't open the whole gate");
        level.setBlock(base.west(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(level.getBlockState(base).getValue(GateBlock.OPEN), "losing redstone didn't shut it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void kegsBrewPourAndPipe(GameTestHelper helper) {
        Block kegBlock = BuiltInRegistries.BLOCK.get(LotrPort.id("keg"));
        helper.assertTrue(kegBlock instanceof KegBlock, "keg is not a keg");
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, kegBlock.defaultBlockState(), 3);
        KegBlockEntity keg = (KegBlockEntity) level.getBlockEntity(pos);
        for (int i = 0; i < 6; i++) {
            keg.setItem(i, new ItemStack(Items.WHEAT));
        }
        for (int i = 6; i < 9; i++) {
            keg.setItem(i, new ItemStack(Items.WATER_BUCKET));
        }
        helper.assertTrue(keg.startBrewing(), "six wheat and three water buckets didn't start ale");
        helper.assertTrue(keg.mode() == KegBlockEntity.BREWING && keg.drink().is(item("ale")), "the keg isn't brewing ale");
        helper.assertTrue(keg.getItem(6).is(Items.BUCKET) && keg.getItem(0).isEmpty(), "brewing didn't use its inputs");
        helper.assertFalse(keg.stopBrewing(), "a weak brew could be stopped before its first step");
        for (int t = 0; t < 4 * 12000; t++) {
            keg.tick();
        }
        helper.assertTrue(keg.mode() == KegBlockEntity.FULL && Drinks.strength(keg.drink()) == 4,
                "ale didn't brew to potent: mode " + keg.mode() + ", strength " + Drinks.strength(keg.drink()));

        ItemStack poured = keg.pour();
        helper.assertTrue(poured.is(item("ale")) && Drinks.strength(poured) == 4, "the keg didn't pour potent ale");

        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        helper.assertTrue(fluids != null, "the keg has no fluid handler");
        FluidStack out = fluids.drain(250, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(out.getAmount() == 250 && BuiltInRegistries.FLUID.getKey(out.getFluid())
                .equals(LotrPort.id("ale")) && out.getOrDefault(Drinks.STRENGTH.get(), -1) == 4,
                "the keg didn't drain 250 mB of potent ale: " + out);
        helper.assertTrue(keg.amount() == 14 * 250, "the keg holds " + keg.amount() + " mB, not 14 servings");
        helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "the keg's water tank didn't take water");

        ItemStack goblet = new ItemStack(item("golden_goblet"));
        IFluidHandlerItem cup = goblet.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(cup != null && cup.fill(out, IFluidHandler.FluidAction.EXECUTE) == 250,
                "a golden goblet didn't take a serving");
        ItemStack filled = cup.getContainer();
        helper.assertTrue(filled.is(item("ale")) && Drinks.vesselId(filled) == 2 && Drinks.strength(filled) == 4,
                "the goblet isn't potent ale in a golden goblet: " + filled);
        IFluidHandlerItem full = filled.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(full != null && full.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount() == 250
                && full.getContainer().is(item("golden_goblet")), "draining the goblet didn't leave the goblet");

        // a LOTR barrel in a structure: its drink in slot 9, full (converter/structures.py keg)
        BlockPos pos2 = pos.east(2);
        level.setBlock(pos2, kegBlock.defaultBlockState(), 3);
        KegBlockEntity barrel = (KegBlockEntity) level.getBlockEntity(pos2);
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) 9);
        stack.putString("id", "lotr_port:mead");
        stack.putInt("count", 5);
        CompoundTag components = new CompoundTag();
        components.putInt("lotr_port:drink_strength", 3);
        stack.put("components", components);
        ListTag list = new ListTag();
        list.add(stack);
        CompoundTag tag = new CompoundTag();
        tag.putByte("Mode", (byte) 2);
        tag.put("Items", list);
        barrel.loadWithComponents(tag, level.registryAccess());
        helper.assertTrue(barrel.mode() == KegBlockEntity.FULL && barrel.amount() == 5 * 250
                && barrel.drink().is(item("mead")) && Drinks.strength(barrel.drink()) == 3,
                "a structure's barrel didn't load as 5 strong meads");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void drinksWorkAsLotr(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        ItemStack brew = new ItemStack(item("athelas_brew"));
        brew.set(Drinks.STRENGTH.get(), 2); // moderate: LOTR's factor 1
        brew.set(Drinks.VESSEL.get(), item("wooden_cup").builtInRegistryHolder());
        ItemStack left = brew.finishUsingItem(helper.getLevel(), player);
        helper.assertFalse(player.hasEffect(MobEffects.POISON), "the athelas brew didn't take away poison");
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        helper.assertTrue(strength != null && strength.getDuration() > 2300,
                "the athelas brew didn't give LOTR's 2 minutes of strength: " + strength);
        helper.assertTrue(left.is(item("wooden_cup")), "drinking didn't leave the wooden cup: " + left);

        player.getFoodData().setFoodLevel(4);
        ItemStack juice = new ItemStack(item("apple_juice"));
        juice.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 10,
                "apple juice gave " + (player.getFoodData().getFoodLevel() - 4) + " food, not LOTR's 6");
        helper.succeed();
    }
}
