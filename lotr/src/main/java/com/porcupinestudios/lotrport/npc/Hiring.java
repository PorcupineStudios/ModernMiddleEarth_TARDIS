package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.faction.Pledges;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    public static final ResourceLocation VIEW = com.porcupinestudios.lotrport.LotrPort.id("hire");

    /** Opens the captain's hire screen: each unit with its price and what it needs. */
    public static void offer(ServerPlayer player, LotrNpc captain) {
        com.porcupinestudios.lotrport.ui.Views.open(player, new com.porcupinestudios.lotrport.ui.View.Target(VIEW,
                captain.getUUID().toString(), ""), true);
    }

    public static void registerView() {
        com.porcupinestudios.lotrport.ui.Views.register(VIEW, new HireView());
    }

    /** The hire screen (context: the captain's UUID); only while the captain is alive and near. */
    static final class HireView implements com.porcupinestudios.lotrport.ui.ViewProvider {
        static Optional<LotrNpc> captain(ServerPlayer player, String context) {
            try {
                return Optional.ofNullable(player.serverLevel().getEntity(java.util.UUID.fromString(context)))
                        .filter(e -> e instanceof LotrNpc n && n.isAlive() && e.distanceToSqr(player) < 16 * 16)
                        .map(e -> (LotrNpc) e);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<com.porcupinestudios.lotrport.ui.View> build(ServerPlayer player, String context, String tab) {
            Optional<LotrNpc> c = captain(player, context);
            if (c.isEmpty()) {
                return Optional.empty();
            }
            LotrNpc captain = c.get();
            List<UnitPool.Unit> units = units(captain);
            Optional<ResourceLocation> faction = captain.faction();
            float have = faction.map(f -> Alignment.get(player, f)).orElse(0f);
            boolean pledged = faction.isPresent() && Pledges.pledge(player).equals(faction);
            int coins = Coins.count(player);
            List<Integer> rows = new java.util.ArrayList<>();
            for (int i = 0; i < units.size(); i++) {
                rows.add(i);
            }
            var page = com.porcupinestudios.lotrport.ui.TableBuilder.of(rows, i -> Integer.toString(i))
                    .column(com.porcupinestudios.lotrport.ui.Column.text("unit", tr("col.unit", "Unit"), 4),
                            i -> com.porcupinestudios.lotrport.ui.Cell.text(name(units.get(i).npc()).getString()))
                    .column(com.porcupinestudios.lotrport.ui.Column.number("cost", tr("col.cost", "Cost"), 2), i -> {
                        int cost = units.get(i).cost();
                        return com.porcupinestudios.lotrport.ui.Cell.number(cost, cost + "c", coins >= cost
                                ? com.porcupinestudios.lotrport.ui.Tone.NORMAL : com.porcupinestudios.lotrport.ui.Tone.BAD);
                    })
                    .column(com.porcupinestudios.lotrport.ui.Column.number("alignment", tr("col.alignment",
                            "Alignment"), 2), i -> {
                        float a = units.get(i).alignment();
                        return com.porcupinestudios.lotrport.ui.Cell.number(a, Integer.toString(Math.round(a)), have >= a
                                ? com.porcupinestudios.lotrport.ui.Tone.GOOD : com.porcupinestudios.lotrport.ui.Tone.BAD);
                    })
                    .column(com.porcupinestudios.lotrport.ui.Column.text("pledge", tr("col.pledge", "Pledge"), 2), i ->
                            !units.get(i).pledge() ? com.porcupinestudios.lotrport.ui.Cell.text("-",
                                    com.porcupinestudios.lotrport.ui.Tone.MUTED)
                            : com.porcupinestudios.lotrport.ui.Cell.text("needed", pledged
                                    ? com.porcupinestudios.lotrport.ui.Tone.GOOD : com.porcupinestudios.lotrport.ui.Tone.BAD))
                    .column(com.porcupinestudios.lotrport.ui.Column.text("mount", tr("col.mount", "Rides"), 2), i ->
                            com.porcupinestudios.lotrport.ui.Cell.text(units.get(i).mount().map(m -> m.getPath()
                                    .replace('_', ' ')).orElse("-"), com.porcupinestudios.lotrport.ui.Tone.MUTED))
                    .open("hire")
                    .rowAction(com.porcupinestudios.lotrport.ui.Action.of("hire", tr("hire", "Hire")))
                    .button(com.porcupinestudios.lotrport.ui.Action.disabled("purse", Component.literal("You have "
                            + coins + " coins, " + Math.round(have) + " alignment"), tr("purse",
                            "Your coins and alignment with this captain's faction")))
                    .empty(tr("none", "This captain has nobody to hire out"))
                    .build();
            return Optional.of(new com.porcupinestudios.lotrport.ui.View(VIEW, context, Component.translatableWithFallback(
                    "ui.lotr_port.hire.title", "%s hires out", captain.getDisplayName()), List.of(), "", page,
                    Optional.empty()));
        }

        @Override
        public Component denied() {
            return tr("gone", "The captain is gone");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<LotrNpc> c = captain(player, context);
            if (c.isEmpty() || !action.equals("hire")) {
                return Result.NONE;
            }
            int index;
            try {
                index = Integer.parseInt(row);
            } catch (NumberFormatException e) {
                return Result.NONE;
            }
            Hiring.Result r = hire(player, c.get(), index);
            return Result.say(r.unit().isPresent() ? Component.translatableWithFallback("ui.lotr_port.hire.done",
                    "Hired %s", r.unit().get().getDisplayName()).withStyle(ChatFormatting.GREEN)
                    : r.problem().copy().withStyle(ChatFormatting.RED));
        }

        static Component tr(String key, String fallback) {
            return Component.translatableWithFallback("ui.lotr_port.hire." + key, fallback);
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
        Society.hired(player.server, npc, player.getUUID());
        unit.mount().ifPresent(m -> {
            var armour = npc.npcType() == null ? java.util.List.<ResourceLocation>of()
                    : npc.npcType().gear().getOrDefault("mount_armor", java.util.List.of());
            mount(npc, m, player, armour.isEmpty() ? null : armour.get(npc.getRandom().nextInt(armour.size())));
        });
        return Result.ok(npc);
    }

    /** Puts a hired rider on its mount: an NPC type (hired too) or a mount (tamed and saddled; the owner's, if any). */
    public static void mount(LotrNpc rider, ResourceLocation mount, ServerPlayer owner) {
        mount(rider, mount, owner, null);
    }

    /** As {@link #mount(LotrNpc, ResourceLocation, ServerPlayer)}, with armour for the mount if it fits. */
    public static void mount(LotrNpc rider, ResourceLocation mount, ServerPlayer owner, ResourceLocation armour) {
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
            if (m instanceof com.porcupinestudios.lotrport.mount.LotrHorse h) {
                h.finalizeSpawn(level, level.getCurrentDifficultyAt(h.blockPosition()),
                        net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
                h.setNpcOwned();
                if (owner != null) {
                    h.setOwnerUUID(owner.getUUID());
                }
            } else if (m instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h) {
                h.setTamed(true);
                h.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE), null);
            }
            if (owner != null && m instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }
            if (armour != null && m instanceof net.minecraft.world.entity.Mob mob) {
                var stack = new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(armour));
                if (mob.isBodyArmorItem(stack)) {
                    mob.setBodyArmorItem(stack);
                }
            }
            level.addFreshEntity(m);
        }
        rider.startRiding(m, true);
    }

    private static Component name(ResourceLocation npc) {
        return Component.translatable("npc." + npc.getNamespace() + "." + npc.getPath());
    }
}
