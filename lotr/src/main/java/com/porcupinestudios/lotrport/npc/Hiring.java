package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.Pledges;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hiring units from a captain: the captain lists its units in chat with a [Hire] link per unit ({@code /lotr_hire}),
 * and {@link #hire} checks alignment, pledge and coins, takes the coins and spawns the unit as the player's.
 */
public final class Hiring {
    /** The outcome of a hire: the new unit, or why not. */
    public record Result(Optional<LotrNpc> unit, Component problem) {
        static Result ok(LotrNpc unit) {
            return new Result(Optional.of(unit), Component.empty());
        }

        static Result fail(String key, String fallback, Object... args) {
            return new Result(Optional.empty(), Component.translatableWithFallback(key, fallback, args));
        }
    }

    private Hiring() {}

    public static List<UnitPool.Unit> units(LotrNpc captain) {
        NpcType type = captain.npcType();
        return type == null ? List.of() : type.units().flatMap(NpcData::units).map(UnitPool::units).orElse(List.of());
    }

    /** Lists the captain's units to the player, each with its price and a [Hire] link. */
    public static void offer(ServerPlayer player, LotrNpc captain) {
        List<UnitPool.Unit> units = units(captain);
        player.sendSystemMessage(Component.translatableWithFallback("npc.lotr_port.hire.title",
                "%s hires out (you have %s coins):", captain.getDisplayName(), Coins.count(player))
                .withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < units.size(); i++) {
            UnitPool.Unit u = units.get(i);
            String command = "/lotr_hire " + captain.getUUID() + " " + i;
            MutableComponent link = Component.translatableWithFallback("npc.lotr_port.hire.link", "[Hire]")
                    .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
            player.sendSystemMessage(Component.literal(" ").append(link).append(" ").append(name(u.npc()))
                    .append(Component.translatableWithFallback("npc.lotr_port.hire.price",
                            ": %s coins, %s alignment%s", u.cost(), u.alignment(), u.pledge() ? ", pledged" : "")));
        }
    }

    /** Hires the captain's unit number {@code index} for the player, if they qualify and can pay. */
    public static Result hire(ServerPlayer player, LotrNpc captain, int index) {
        List<UnitPool.Unit> units = units(captain);
        if (!captain.isAlive() || index < 0 || index >= units.size()) {
            return Result.fail("npc.lotr_port.hire.none", "There is no such unit to hire");
        }
        UnitPool.Unit unit = units.get(index);
        Optional<ResourceLocation> faction = captain.faction();
        if (faction.isPresent() && Alignment.get(player, faction.get()) < unit.alignment()) {
            return Result.fail("npc.lotr_port.hire.alignment", "You need %s alignment to hire this unit",
                    unit.alignment());
        }
        if (unit.pledge() && faction.isPresent() && !Pledges.pledge(player).equals(faction)) {
            return Result.fail("npc.lotr_port.hire.pledge", "You must be pledged to hire this unit");
        }
        if (Coins.count(player) < unit.cost()) {
            return Result.fail("npc.lotr_port.hire.coins", "You need %s coins", unit.cost());
        }
        Optional<LotrNpc> spawned = LotrNpc.spawn((ServerLevel) captain.level(), unit.npc(), captain.getX(),
                captain.getY(), captain.getZ());
        if (spawned.isEmpty()) {
            return Result.fail("npc.lotr_port.hire.unknown", "Unknown unit %s", unit.npc().toString());
        }
        Coins.pay(player, unit.cost());
        LotrNpc npc = spawned.get();
        npc.hireTo(player);
        People.get(player.server).hired(player.server, npc, player.getUUID());
        unit.mount().ifPresent(m -> mount(npc, m, player));
        return Result.ok(npc);
    }

    /** Puts a hired rider on its mount: an NPC type (hired too) or a vanilla animal (a horse, tamed and saddled). */
    public static void mount(LotrNpc rider, ResourceLocation mount, ServerPlayer owner) {
        ServerLevel level = (ServerLevel) rider.level();
        net.minecraft.world.entity.Entity m;
        if (NpcData.type(mount).isPresent()) {
            Optional<LotrNpc> n = LotrNpc.spawn(level, mount, rider.getX(), rider.getY(), rider.getZ());
            if (n.isEmpty()) {
                return;
            }
            if (owner != null) {
                n.get().hireTo(owner);
            }
            m = n.get();
        } else {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(mount);
            if (type.isEmpty()) {
                return;
            }
            m = type.get().create(level);
            if (m == null) {
                return;
            }
            m.moveTo(rider.getX(), rider.getY(), rider.getZ(), rider.getYRot(), 0);
            if (m instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h) {
                h.setTamed(true);
                h.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE), null);
            }
            if (m instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }
            level.addFreshEntity(m);
        }
        rider.startRiding(m, true);
    }

    private static Component name(ResourceLocation npc) {
        return Component.translatable("npc." + npc.getNamespace() + "." + npc.getPath());
    }
}
