package com.porcupinestudios.lotrport.unit;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.ColonyAccess;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonySystemsCore;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.CoreComponents.Order;
import com.porcupinestudios.lotrport.society.CoreComponents.Unit;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Hired units and colony soldiers: one {@link Unit} component on a {@link Person}, whoever owns it. The Person is the
 * truth; the body ({@link LotrNpc}) takes its orders from it when it loads and whenever they change here. A player's
 * unit can be stationed in a colony the player manages: the colony then owns it, pays its wage every day and the unit
 * guards its post; unpaid too long, it deserts.
 */
public final class Units {
    public static final ResourceLocation STATIONED_HISTORY = LotrPort.id("unit_stationed");
    public static final ResourceLocation DISMISSED_HISTORY = LotrPort.id("unit_dismissed");
    public static final ResourceLocation DESERTION = LotrPort.id("desertion");

    private Units() {}

    public static void register() {
        UnitSettings.register();
        NeoForge.EVENT_BUS.addListener(Units::onJoin);
        // after pay, so a soldier paid today never deserts today
        ColonySystems.register(ColonySystem.of(DESERTION, ColonySystem.Cadence.DAY, Tier.ALL,
                Set.of(ColonySystemsCore.SOLDIER_PAY), ctx -> {
                    int max = UnitSettings.get().desertDays();
                    for (Person p : Society.soldiers(ctx.server(), ctx.colony())) {
                        if (p.get(CoreComponents.UNIT).map(Unit::unpaid).orElse(0) > max) {
                            dismiss(ctx.server(), p, "deserted");
                            ctx.count(DESERTION, 1);
                        }
                    }
                }));
    }

    /** A plain result: done, or why not. */
    public record Result(boolean ok, Component message) {
        static Result fail(String key, String fallback, Object... args) {
            return new Result(false, Component.translatableWithFallback(key, fallback, args));
        }

        static Result done(String key, String fallback, Object... args) {
            return new Result(true, Component.translatableWithFallback(key, fallback, args));
        }
    }

    /** Whether the player may give this unit orders: theirs, or a soldier of a colony they manage. */
    public static boolean canCommand(MinecraftServer server, UUID player, Person p) {
        if (!p.alive() || !p.has(CoreComponents.UNIT)) {
            return false;
        }
        if (p.owner().equals(Owner.player(player))) {
            return true;
        }
        return Society.colonyOf(server, p).map(c -> ColonyAccess.canManage(server, player, c)).orElse(false);
    }

    public static boolean canCommand(ServerPlayer player, LotrNpc npc) {
        return Persons.get(player.server).get(npc.getUUID())
                .map(p -> canCommand(player.server, player.getUUID(), p)).orElse(false);
    }

    /** Units the player may command: their own, then their colonies' soldiers. */
    public static List<Person> commandable(MinecraftServer server, UUID player) {
        List<Person> out = new ArrayList<>(Society.unitsOf(server, player));
        for (Colony c : ColonyAccess.managedBy(server, player)) {
            out.addAll(Society.soldiers(server, c));
        }
        return out;
    }

    /** Sneak + right-click on your unit: switch between following and guarding where it stands. */
    public static void toggle(ServerPlayer player, LotrNpc npc) {
        Person p = Persons.get(player.server).get(npc.getUUID()).orElse(null);
        if (p == null) {
            return;
        }
        Order next = npc.command() == Order.FOLLOW ? Order.GUARD : Order.FOLLOW;
        order(player.server, p, next, Optional.of(GlobalPos.of(npc.level().dimension(), npc.blockPosition())));
        player.displayClientMessage(Component.translatableWithFallback("npc.lotr_port.command."
                + next.getSerializedName(), next == Order.FOLLOW ? "%s is following you" : "%s is guarding here",
                npc.getDisplayName()), true);
    }

    /** Gives a unit an order; GUARD holds {@code at} (or where its body stands). */
    public static void order(MinecraftServer server, Person p, Order order, Optional<GlobalPos> at) {
        Unit u = p.get(CoreComponents.UNIT).orElse(null);
        if (u == null) {
            return;
        }
        Optional<GlobalPos> post = at.isPresent() ? at : body(server, p)
                .map(b -> GlobalPos.of(b.level().dimension(), b.blockPosition()));
        if (order == Order.FOLLOW && p.owner().playerId().isEmpty()) {
            order = Order.GUARD; // a colony soldier has nobody to follow
        }
        Persons.get(server).set(server, p, CoreComponents.UNIT, u.withOrder(order, post));
        applyToBody(server, p);
    }

    /** Orders every unit of the squad {@code squad} that the owner of {@code p} has. */
    public static int orderSquad(MinecraftServer server, Person p, Order order, Optional<GlobalPos> at) {
        Optional<String> squad = p.get(CoreComponents.UNIT).flatMap(Unit::squad);
        if (squad.isEmpty()) {
            order(server, p, order, at);
            return 1;
        }
        int n = 0;
        for (Person q : Persons.get(server).ownedBy(p.owner(), CoreComponents.UNIT)) {
            if (q.get(CoreComponents.UNIT).flatMap(Unit::squad).equals(squad)) {
                order(server, q, order, order == Order.GUARD ? Optional.empty() : at);
                n++;
            }
        }
        return n;
    }

    public static void setSquad(MinecraftServer server, Person p, String name) {
        Unit u = p.get(CoreComponents.UNIT).orElse(null);
        if (u == null) {
            return;
        }
        String clean = name.strip();
        int max = UnitSettings.get().squadNameLength();
        if (clean.length() > max) {
            clean = clean.substring(0, max);
        }
        Persons.get(server).set(server, p, CoreComponents.UNIT, u.withSquad(Optional.of(clean)));
    }

    /**
     * Stations a player's unit in a colony they manage: the colony owns it from now, pays {@code wage} a day and it
     * guards where it stands (the Hall if its body is not loaded).
     */
    public static Result station(MinecraftServer server, UUID player, Person p, Colony colony) {
        if (!p.alive() || !p.owner().equals(Owner.player(player)) || !p.has(CoreComponents.UNIT)) {
            return Result.fail("unit.lotr_port.not_yours", "That unit is not yours to station");
        }
        if (!ColonyAccess.canManage(server, player, colony)) {
            return Result.fail("unit.lotr_port.not_manager", "You can't station units in %s", colony.name());
        }
        Persons persons = Persons.get(server);
        Unit u = p.get(CoreComponents.UNIT).orElseThrow();
        GlobalPos post = body(server, p).map(b -> GlobalPos.of(b.level().dimension(), b.blockPosition()))
                .orElse(GlobalPos.of(colony.dimension(), colony.hall()));
        persons.set(server, p, CoreComponents.UNIT, u.withOrder(Order.GUARD, Optional.of(post))
                .withWage(UnitSettings.get().wage()).withUnpaid(0));
        persons.setOwner(server, p, colony.asOwner(), "stationed");
        applyToBody(server, p);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("unit", p.id());
        tag.putUUID("colony", colony.id());
        tag.putUUID("by", player);
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), STATIONED_HISTORY,
                post.dimension(), Optional.of(post.pos()), tag));
        return Result.done("unit.lotr_port.stationed", "%s now serves %s", p.name(), colony.name());
    }

    /** Sends a unit away: it serves nobody (deserted, dismissed). The record stays, without its unit component. */
    public static void dismiss(MinecraftServer server, Person p, String reason) {
        Persons persons = Persons.get(server);
        Owner was = p.owner();
        persons.set(server, p, CoreComponents.UNIT, null);
        persons.setOwner(server, p, Owner.NONE, reason);
        applyToBody(server, p);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("unit", p.id());
        tag.putString("from", was.encode());
        tag.putString("reason", reason);
        GlobalPos at = p.lastSeen().orElse(GlobalPos.of(server.overworld().dimension(), BlockPos.ZERO));
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), DISMISSED_HISTORY,
                at.dimension(), Optional.of(at.pos()), tag));
    }

    /** The loaded body of a Person, if any (its last dimension first, then every level). */
    public static Optional<LotrNpc> body(MinecraftServer server, Person p) {
        if (p.lastSeen().isPresent()) {
            ServerLevel level = server.getLevel(p.lastSeen().get().dimension());
            if (level != null && level.getEntity(p.id()) instanceof LotrNpc npc) {
                return Optional.of(npc);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(p.id());
            if (e instanceof LotrNpc npc) {
                return Optional.of(npc);
            }
        }
        return Optional.empty();
    }

    /** Makes a loaded body match its record. */
    public static void applyToBody(MinecraftServer server, Person p) {
        body(server, p).ifPresent(npc -> apply(npc, p));
    }

    private static void apply(LotrNpc npc, Person p) {
        Optional<Unit> u = p.alive() ? p.get(CoreComponents.UNIT) : Optional.empty();
        if (u.isEmpty()) {
            if (npc.owner().isPresent() || npc.garrison().isPresent()) {
                npc.applyUnit(null, null, Order.NONE, null);
            }
            return;
        }
        BlockPos post = u.get().post().filter(g -> g.dimension().equals(npc.level().dimension()))
                .map(GlobalPos::pos).orElse(null);
        npc.applyUnit(p.owner().playerId().orElse(null), p.owner().colonyId().orElse(null), u.get().order(), post);
    }

    /** A body that loads takes its orders from its record (they may have changed while it was unloaded). */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LotrNpc npc)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (npc.owner().isEmpty() && npc.garrison().isEmpty()) {
            return; // only bodies that served someone can be out of date
        }
        Persons.get(level.getServer()).get(npc.getUUID()).ifPresent(p -> apply(npc, p));
    }

    /** Opens (or refreshes) the unit screen, with {@code focus} selected. */
    public static void openScreen(ServerPlayer player, Optional<UUID> focus) {
        UnitNet.send(player, focus, true);
    }

    /** A colony's name for screens. */
    static String colonyName(MinecraftServer server, Person p) {
        return Society.colonyOf(server, p).map(Colony::name).orElse("");
    }

    static Colonies colonies(MinecraftServer server) {
        return Colonies.get(server);
    }
}
