package com.porcupinestudios.lotrport.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.colony.members.Members;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.land.Protection;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.CoreComponents.Order;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.SocietyEvent;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.lotrport.unit.Units;
import com.porcupinestudios.lotrport.war.War.Army;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Colony wars (replacing LOTR's conquest): declared with a reason and a warning, fought by armies of real soldiers
 * that muster, march for a time that grows with distance and appear at the target. A siege needs both sides online
 * and is won by holding the Hall's zone with no defender in it; its land opens to the attackers only while it lasts.
 * A raid goes only against an offline colony, is small and capped, is fought in the abstract and never breaks a
 * block. Score and exhaustion decide the peace. AI rulers' wars are kept here too ({@link #aiWar}).
 */
public final class Wars extends SavedData {
    public static final String FILE = "lotr_port_wars";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation DECLARED_HISTORY = LotrPort.id("war_declared");
    public static final ResourceLocation ACTIVE_HISTORY = LotrPort.id("war_active");
    public static final ResourceLocation MUSTER_HISTORY = LotrPort.id("army_mustered");
    public static final ResourceLocation SIEGE_HISTORY = LotrPort.id("siege");
    public static final ResourceLocation RAID_HISTORY = LotrPort.id("raid");
    public static final ResourceLocation ENDED_HISTORY = LotrPort.id("war_ended");
    public static final ResourceLocation VASSAL_TRIBUTE = LotrPort.id("vassal_tribute");
    public static final String CONQUEST = "conquest";
    private static final int TICK = 20;
    private static final int MAX_REPORTS = 30;

    /** A vassal colony's liege: the colony it pays every day. */
    public record Liege(UUID colony) {
        public static final Codec<Liege> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("colony").forGetter(Liege::colony)).apply(i, Liege::new));
    }

    public static final ComponentType<Liege> LIEGE = ComponentType.register(LotrPort.id("liege"), Liege.CODEC);

    /** Done, or why not. */
    public record Result(boolean ok, Component message, Optional<War> war) {
        static Result fail(String key, String fallback, Object... args) {
            return new Result(false, Component.translatableWithFallback(key, fallback, args), Optional.empty());
        }

        static Result done(War war, String key, String fallback, Object... args) {
            return new Result(true, Component.translatableWithFallback(key, fallback, args), Optional.of(war));
        }
    }

    private static final SavedData.Factory<Wars> FACTORY = new SavedData.Factory<>(Wars::new, Wars::load, null);

    private final Map<UUID, War> wars = new LinkedHashMap<>();
    /** Colony -> when it was last raided. */
    private final Map<UUID, Long> raided = new HashMap<>();
    /** Soldiers home from a war whose bodies wait for their Hall to be loaded. */
    private final Set<UUID> homecoming = new HashSet<>();

    public static Wars get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public static void register() {
        WarSettings.register();
        NeoForge.EVENT_BUS.addListener(Wars::onTick);
        NeoForge.EVENT_BUS.addListener(Wars::onDied);
        NeoForge.EVENT_BUS.addListener(Wars::onLogin);
        Protection.exception((player, claim, perm) -> claim.owner() instanceof Owner.OfColony c
                && !perm.equals(Perm.MEMBERS) && !perm.equals(Perm.TREASURY) && !perm.equals(Perm.WAR)
                && get(player.server).besieging(player.server, player.getUUID(), c.id()));
        ColonySystems.register(ColonySystem.of(VASSAL_TRIBUTE, ColonySystem.Cadence.DAY, Tier.ALL, Set.of(), ctx -> {
            Optional<Liege> liege = ctx.colony().get(LIEGE);
            Optional<Colony> lord = liege.flatMap(l -> Colonies.get(ctx.server()).get(l.colony()));
            if (lord.isEmpty()) {
                return;
            }
            long owed = (long) WarSettings.get().peace().vassalTribute() * ctx.days();
            ctx.count(VASSAL_TRIBUTE, (int) pay(ctx.server(), ctx.colony().asOwner(), lord.get().asOwner(), owed));
        }));
    }

    // ---- queries ----

    public Optional<War> get(UUID id) {
        return Optional.ofNullable(wars.get(id));
    }

    /** Finds a war by its full id or the start of it (chat links use the short form). */
    public Optional<War> find(String id) {
        return wars.values().stream().filter(w -> w.id.toString().startsWith(id.toLowerCase())).findFirst();
    }

    public List<War> all() {
        return List.copyOf(wars.values());
    }

    /** Wars not ended that involve this party. */
    public List<War> of(Owner party) {
        return wars.values().stream().filter(w -> w.state != War.State.ENDED && w.involves(party)).toList();
    }

    /** The war (not ended) between these two parties, either way round. */
    public Optional<War> between(Owner a, Owner b) {
        return wars.values().stream().filter(w -> w.state != War.State.ENDED && w.involves(a) && w.involves(b))
                .findFirst();
    }

    public boolean atWar(Owner a, Owner b) {
        return between(a, b).isPresent();
    }

    /** Whether the player is on the attacking side of a siege of this colony that is going on now. */
    public boolean besieging(MinecraftServer server, UUID player, UUID colony) {
        for (War w : wars.values()) {
            if (w.state == War.State.ACTIVE && w.defender.equals(Owner.colony(colony))
                    && w.armies.stream().anyMatch(a -> a.stage == Army.Stage.SIEGE)
                    && colonyOf(server, w.attacker).map(c -> Permissions.isMember(player, c)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** Soldiers of any army (they are away from home). */
    public boolean away(UUID person) {
        for (War w : wars.values()) {
            for (Army a : w.armies) {
                if (a.soldiers.contains(person)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Optional<Colony> colonyOf(MinecraftServer server, Owner party) {
        return party.colonyId().flatMap(id -> Colonies.get(server).get(id));
    }

    /** A party's name for chat: the colony's name or the AI realm's id. */
    public static String name(MinecraftServer server, Owner party) {
        return switch (party) {
            case Owner.OfColony c -> Colonies.get(server).get(c.id()).map(Colony::name).orElse("a lost colony");
            case Owner.OfRealm r -> com.porcupinestudios.lotrport.faction.Realms.get(server).get(r.id())
                    .map(x -> x.name()).orElse(r.id().toString());
            default -> "nobody";
        };
    }

    /**
     * Whether someone may fight for this side now: an online player with the {@code war} permission (the owner or a
     * stand-in). AI colonies and realms are always there.
     */
    public static boolean online(MinecraftServer server, Owner party) {
        Optional<Colony> colony = colonyOf(server, party);
        if (colony.isEmpty()) {
            return party instanceof Owner.OfRealm;
        }
        if (colony.get().ai()) {
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Permissions.can(p.getUUID(), colony.get(), Perm.WAR)) {
                return true;
            }
        }
        return false;
    }

    // ---- declaring ----

    /**
     * {@code by} declares war for {@code attacker} on {@code defender}: needs the war permission, a reason and no war
     * between them yet. The war may be fought after the warning time.
     */
    public Result declare(MinecraftServer server, UUID by, Colony attacker, Colony defender, War.Goal goal,
            String reason, Optional<UUID> subject) {
        if (!Permissions.can(by, attacker, Perm.WAR)) {
            return Result.fail("war.lotr_port.no_permission", "You may not make war for %s", attacker.name());
        }
        if (attacker.id().equals(defender.id())) {
            return Result.fail("war.lotr_port.self", "A colony can't make war on itself");
        }
        if (reason.isBlank()) {
            return Result.fail("war.lotr_port.no_reason", "A war needs a reason");
        }
        if (attacker.get(LIEGE).map(l -> l.colony().equals(defender.id())).orElse(false)
                && goal != War.Goal.FREE_VASSAL) {
            return Result.fail("war.lotr_port.rebel", "A vassal can only fight its liege to go free");
        }
        if (goal == War.Goal.FREE_VASSAL) {
            UUID vassal = subject.orElse(attacker.id());
            boolean ok = Colonies.get(server).get(vassal).flatMap(v -> v.get(LIEGE))
                    .map(l -> l.colony().equals(defender.id())).orElse(false);
            if (!ok) {
                return Result.fail("war.lotr_port.not_vassal", "That colony is not %s's vassal", defender.name());
            }
            subject = Optional.of(vassal);
        }
        if (goal == War.Goal.VASSAL && defender.get(LIEGE).isPresent()) {
            return Result.fail("war.lotr_port.has_liege", "%s already serves a liege", defender.name());
        }
        return declare(server, attacker.asOwner(), defender.asOwner(), goal, reason, subject,
                WarSettings.get().armies().warningTicks());
    }

    /** Declares a war between any two parties (AI rulers use it too). */
    public Result declare(MinecraftServer server, Owner attacker, Owner defender, War.Goal goal, String reason,
            Optional<UUID> subject, int warning) {
        if (atWar(attacker, defender)) {
            return Result.fail("war.lotr_port.already", "%s and %s are already at war", name(server, attacker),
                    name(server, defender));
        }
        long now = now(server);
        War war = new War(UUID.randomUUID(), attacker, defender, goal, subject, reason, now, now + warning,
                warning <= 0 ? War.State.ACTIVE : War.State.WARNING, new War.Tally(0, 0, 0, 0, -1), List.of(),
                Optional.empty(), List.of(), "");
        wars.put(war.id, war);
        setDirty();
        CompoundTag tag = tag(war);
        tag.putString("goal", goal.getSerializedName());
        tag.putString("reason", reason);
        tag.putLong("active_from", war.activeFrom);
        log(server, DECLARED_HISTORY, war, tag);
        String text = name(server, attacker) + " declared war on " + name(server, defender) + " ("
                + goal.getSerializedName().replace('_', ' ') + "): " + reason;
        report(server, war, text);
        return Result.done(war, "war.lotr_port.declared", "%s declared war on %s. Fighting may begin in %s.",
                name(server, attacker), name(server, defender), minutes(warning));
    }

    // ---- armies ----

    /** Soldiers of the attacking colony that could march now (alive, not already away). */
    public List<Person> available(MinecraftServer server, Colony colony) {
        List<Person> out = new ArrayList<>();
        for (Person p : Society.soldiers(server, colony)) {
            if (p.alive() && !away(p.id())) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Sends up to {@code count} soldiers of the attacking colony against the defender. A siege needs someone with
     * the war permission online on both sides; a raid needs the defender's side offline, is capped and waits out the
     * colony's raid cooldown. Each soldier costs coins from the treasury; the defender is warned at once.
     */
    public Result muster(MinecraftServer server, UUID by, War war, Army.Kind kind, int count) {
        WarSettings.Armies s = WarSettings.get().armies();
        Optional<Colony> attacker = colonyOf(server, war.attacker);
        Optional<Colony> defender = colonyOf(server, war.defender);
        if (attacker.isEmpty() || defender.isEmpty()) {
            return Result.fail("war.lotr_port.no_colony", "Only colonies can muster armies");
        }
        if (!Permissions.can(by, attacker.get(), Perm.WAR)) {
            return Result.fail("war.lotr_port.no_permission", "You may not make war for %s", attacker.get().name());
        }
        if (war.state != War.State.ACTIVE) {
            return Result.fail("war.lotr_port.not_active", "The war may not be fought yet");
        }
        if (kind == Army.Kind.SIEGE && !(online(server, war.attacker) && online(server, war.defender))) {
            return Result.fail("war.lotr_port.siege_offline",
                    "A siege needs someone who leads %s online", defender.get().name());
        }
        if (kind == Army.Kind.RAID) {
            if (online(server, war.defender)) {
                return Result.fail("war.lotr_port.raid_online",
                        "%s's leaders are online: raids are only for when they are away", defender.get().name());
            }
            long last = raided.getOrDefault(defender.get().id(), Long.MIN_VALUE / 2);
            if (now(server) - last < s.raidCooldown()) {
                return Result.fail("war.lotr_port.raid_cooldown", "%s was raided too recently",
                        defender.get().name());
            }
            count = Math.min(count, s.raidCap());
        }
        List<Person> ready = available(server, attacker.get());
        count = Math.min(count, ready.size());
        if (count <= 0) {
            return Result.fail("war.lotr_port.no_soldiers", "%s has no soldiers at home", attacker.get().name());
        }
        long cost = (long) s.musterCost() * count;
        if (treasury(attacker.get()) < cost) {
            return Result.fail("war.lotr_port.no_coins", "Mustering %s soldiers costs %s coins", count, cost);
        }
        Colonies.get(server).set(attacker.get(), CoreComponents.TREASURY,
                new CoreComponents.Treasury(treasury(attacker.get()) - cost));
        List<UUID> ids = new ArrayList<>();
        for (Person p : ready.subList(0, count)) {
            ids.add(p.id());
            Units.body(server, p).ifPresent(LotrNpc::discard);
            homecoming.remove(p.id());
        }
        Army army = march(server, war, ids, 0, kind, attacker.get().hall(), defender.get().hall());
        return Result.done(war, "war.lotr_port.mustered", "%s soldiers march on %s; they arrive in %s", count,
                defender.get().name(), minutes((int) (army.arrives - now(server))));
    }

    /** Sends a counted army (no bodies) from {@code from}: AI rulers' raids. */
    public Optional<Army> musterCounted(MinecraftServer server, War war, int strength, Army.Kind kind, BlockPos from) {
        Optional<Colony> defender = colonyOf(server, war.defender);
        if (war.state != War.State.ACTIVE || defender.isEmpty() || strength <= 0) {
            return Optional.empty();
        }
        if (kind == Army.Kind.RAID && (online(server, war.defender) || now(server) - raided.getOrDefault(
                defender.get().id(), Long.MIN_VALUE / 2) < WarSettings.get().armies().raidCooldown())) {
            return Optional.empty();
        }
        return Optional.of(march(server, war, List.of(), strength, kind, from, defender.get().hall()));
    }

    private Army march(MinecraftServer server, War war, List<UUID> soldiers, int strength, Army.Kind kind,
            BlockPos from, BlockPos to) {
        int ticks = WarSettings.get().armies().march(Math.sqrt(from.distSqr(to)));
        Army army = new Army(UUID.randomUUID(), soldiers, strength, kind, from, now(server) + ticks,
                Army.Stage.MARCHING, 0, -1, -1, false);
        war.armies.add(army);
        setDirty();
        CompoundTag tag = tag(war);
        tag.putUUID("army", army.id);
        tag.putString("kind", kind.getSerializedName());
        tag.putInt("soldiers", soldiers.size() + strength);
        tag.putLong("arrives", army.arrives);
        log(server, MUSTER_HISTORY, war, tag);
        report(server, war, "An army of " + (soldiers.size() + strength) + " (" + kind.getSerializedName()
                + ") marches from " + name(server, war.attacker) + " on " + name(server, war.defender)
                + ", arriving in " + minutes(ticks).getString());
        return army;
    }

    // ---- the clock ----

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK == 0) {
            get(server).tick(server);
        }
    }

    /** Runs the war clock once (every second in game; tests call it directly). */
    public void tick(MinecraftServer server) {
        long now = now(server);
        long day = now / Math.max(1, ColonyData.settings().dayTicks());
        WarSettings settings = WarSettings.get();
        for (War war : new ArrayList<>(wars.values())) {
            if (war.state == War.State.WARNING && now >= war.activeFrom) {
                war.state = War.State.ACTIVE;
                war.tally.day = day;
                setDirty();
                log(server, ACTIVE_HISTORY, war, tag(war));
                report(server, war, "The war between " + name(server, war.attacker) + " and "
                        + name(server, war.defender) + " may now be fought");
            }
            // AI realms make their own peace (AiRulers); exhaustion is for wars a colony fights
            if (war.state == War.State.ACTIVE && (war.attacker instanceof Owner.OfColony
                    || war.defender instanceof Owner.OfColony)) {
                if (war.tally.day < 0) {
                    war.tally.day = day;
                }
                if (day > war.tally.day) {
                    double add = settings.peace().exhaustionPerDay() * (day - war.tally.day);
                    war.tally.exhaustionA += add;
                    war.tally.exhaustionB += add;
                    war.tally.day = day;
                    setDirty();
                }
                double max = settings.peace().exhaustionMax();
                if (war.tally.exhaustionA >= max || war.tally.exhaustionB >= max) {
                    forcePeace(server, war, "exhausted");
                }
            }
            for (Army army : new ArrayList<>(war.armies)) {
                switch (army.stage) {
                    case MARCHING -> {
                        if (now >= army.arrives) {
                            arrive(server, war, army);
                        }
                    }
                    case SIEGE -> siegeTick(server, war, army, now);
                    case RETURNING -> {
                        if (now >= army.arrives) {
                            war.armies.remove(army);
                            homecoming.addAll(army.soldiers);
                            setDirty();
                        }
                    }
                }
            }
        }
        if (!homecoming.isEmpty()) {
            bringHome(server);
        }
    }

    private void arrive(MinecraftServer server, War war, Army army) {
        if (war.state != War.State.ACTIVE) {
            goHome(server, war, army); // peace was made on the way
            return;
        }
        if (army.kind == Army.Kind.RAID) {
            raid(server, war, army);
        } else {
            army.stage = Army.Stage.SIEGE;
            setDirty();
            CompoundTag tag = tag(war);
            tag.putString("event", "begun");
            log(server, SIEGE_HISTORY, war, tag);
            report(server, war, name(server, war.attacker) + " besieges " + name(server, war.defender));
        }
    }

    private void goHome(MinecraftServer server, War war, Army army) {
        army.stage = Army.Stage.RETURNING;
        Optional<Colony> target = colonyOf(server, war.defender);
        int back = target.map(t -> WarSettings.get().armies().march(Math.sqrt(t.hall().distSqr(army.from))))
                .orElse(0);
        army.arrives = now(server) + back;
        if (army.landed) {
            for (UUID id : army.soldiers) {
                Persons.get(server).get(id).flatMap(p -> Units.body(server, p)).ifPresent(LotrNpc::discard);
            }
            army.landed = false;
        }
        army.soldiers.removeIf(id -> Persons.get(server).get(id).map(p -> !p.alive()).orElse(true));
        setDirty();
    }

    // ---- sieges ----

    private void siegeTick(MinecraftServer server, War war, Army army, long now) {
        WarSettings.Armies s = WarSettings.get().armies();
        Optional<Colony> target = colonyOf(server, war.defender);
        if (target.isEmpty() || war.state != War.State.ACTIVE) {
            goHome(server, war, army);
            return;
        }
        // the attacking side leaving ends the siege; the defending side leaving hands it to the attacker
        if (online(server, war.attacker)) {
            army.attackerAway = -1;
        } else if (army.attackerAway < 0) {
            army.attackerAway = now;
        } else if (now - army.attackerAway >= s.attackerGrace()) {
            endSiege(server, war, army, false, "attacker_left");
            return;
        }
        if (online(server, war.defender)) {
            army.defenderAway = -1;
        } else if (army.defenderAway < 0) {
            army.defenderAway = now;
        } else if (now - army.defenderAway >= s.defenderGrace()) {
            endSiege(server, war, army, true, "defender_left");
            return;
        }
        ServerLevel level = server.getLevel(target.get().dimension());
        BlockPos hall = target.get().hall();
        if (level == null || !level.isLoaded(hall)) {
            return; // nobody is there to fight: the siege waits
        }
        if (!army.landed) {
            land(server, level, war, army, target.get());
        }
        long alive = army.soldiers.stream().filter(id -> Persons.get(server).get(id).map(Person::alive)
                .orElse(false)).count();
        if (alive == 0 && army.strength <= 0) {
            endSiege(server, war, army, false, "routed");
            return;
        }
        AABB zone = new AABB(hall).inflate(s.siegeZone());
        boolean attackers = false;
        for (UUID id : army.soldiers) {
            if (level.getEntity(id) instanceof LotrNpc npc && npc.isAlive() && zone.contains(npc.position())) {
                attackers = true;
                break;
            }
        }
        Optional<Colony> home = colonyOf(server, war.attacker);
        boolean defenders = false;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || !zone.contains(p.position())) {
                continue;
            }
            if (Permissions.isMember(p.getUUID(), target.get())) {
                defenders = true;
            } else if (home.map(c -> Permissions.isMember(p.getUUID(), c)).orElse(false)) {
                attackers = true;
            }
        }
        if (!defenders) {
            for (Person p : Society.soldiers(server, target.get())) {
                if (level.getEntity(p.id()) instanceof LotrNpc npc && npc.isAlive()
                        && zone.contains(npc.position())) {
                    defenders = true;
                    break;
                }
            }
        }
        if (attackers && !defenders) {
            army.held += TICK;
            setDirty();
            if (army.held >= s.siegeHold()) {
                endSiege(server, war, army, true, "held");
            }
        }
    }

    /** The army's bodies appear at the edge of the Hall's land, on the side facing home, and make for the Hall. */
    private void land(MinecraftServer server, ServerLevel level, War war, Army army, Colony target) {
        WarSettings.Armies s = WarSettings.get().armies();
        BlockPos hall = target.hall();
        double dx = army.from.getX() - hall.getX();
        double dz = army.from.getZ() - hall.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        BlockPos spot = hall.offset((int) Math.round(dx / len * s.siegeLanding()), 0,
                (int) Math.round(dz / len * s.siegeLanding()));
        if (!level.isLoaded(spot)) {
            return;
        }
        UUID colony = war.attacker.colonyId().orElse(null);
        int n = 0;
        for (UUID id : army.soldiers) {
            Optional<Person> p = Persons.get(server).get(id);
            if (p.isEmpty() || !p.get().alive() || level.getEntity(id) != null) {
                continue;
            }
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spot.offset(n % 4, 0, n / 4));
            n++;
            LotrNpc.bodyFor(level, p.get()).ifPresent(npc -> {
                npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                if (level.addFreshEntity(npc)) {
                    npc.applyUnit(null, colony, Order.GUARD, hall);
                }
            });
        }
        army.landed = true;
        setDirty();
    }

    private void endSiege(MinecraftServer server, War war, Army army, boolean won, String why) {
        WarSettings.Peace p = WarSettings.get().peace();
        if (won) {
            war.tally.scoreA += p.siegeScore();
        } else {
            war.tally.scoreB += p.siegeScore();
        }
        CompoundTag tag = tag(war);
        tag.putString("event", won ? "won" : "lost");
        tag.putString("why", why);
        log(server, SIEGE_HISTORY, war, tag);
        report(server, war, "The siege of " + name(server, war.defender) + (won ? " was won by " : " was beaten off by ")
                + name(server, won ? war.attacker : war.defender) + " (" + why.replace('_', ' ') + ")");
        goHome(server, war, army);
        if (won) {
            win(server, war);
        } else {
            setDirty();
        }
    }

    /** The attacker took the Hall: the war's goal is met. */
    private void win(MinecraftServer server, War war) {
        Colonies colonies = Colonies.get(server);
        Optional<Colony> target = colonyOf(server, war.defender);
        switch (war.goal) {
            case TAKE_COLONY -> {
                Optional<Colony> home = colonyOf(server, war.attacker);
                if (target.isPresent() && home.isPresent()) {
                    colonies.transfer(server, target.get(), home.get().founder(), home.get().faction(),
                            home.get().ai(), CONQUEST);
                    colonies.set(target.get(), Members.TYPE, Members.NONE);
                    colonies.set(target.get(), LIEGE, null);
                }
                end(server, war, Optional.of(true), 0, "taken");
            }
            case VASSAL -> {
                war.attacker.colonyId().ifPresent(lord -> target.ifPresent(t -> colonies.set(t, LIEGE,
                        new Liege(lord))));
                end(server, war, Optional.of(true), 0, "vassal");
            }
            case FREE_VASSAL -> {
                war.subject.flatMap(colonies::get).ifPresent(v -> colonies.set(v, LIEGE, null));
                end(server, war, Optional.of(true), 0, "freed");
            }
            case TRIBUTE -> end(server, war, Optional.of(true), tributeCap(war, true), "tribute");
        }
    }

    // ---- raids ----

    private void raid(MinecraftServer server, War war, Army army) {
        WarSettings settings = WarSettings.get();
        Optional<Colony> target = colonyOf(server, war.defender);
        if (target.isEmpty()) {
            goHome(server, war, army);
            return;
        }
        Colony t = target.get();
        int home = 0;
        for (Person p : Society.soldiers(server, t)) {
            if (p.alive() && !away(p.id())) {
                home++;
            }
        }
        double defense = home + Society.population(server, t) * settings.peace().defensePerCitizen();
        double attack = army.soldiers.size() + army.strength;
        // the same raid always rolls the same
        double roll = 0.5 + new java.util.Random(army.id.getLeastSignificantBits()).nextDouble();
        boolean won = attack * roll > defense;
        String text;
        CompoundTag tag = tag(war);
        tag.putInt("attack", (int) attack);
        tag.putDouble("defense", defense);
        tag.putBoolean("won", won);
        if (won) {
            WarSettings.Armies s = settings.armies();
            long coins = Math.min(s.raidLootCap(), (long) Math.floor(treasury(t) * s.lootShare()));
            long taken = pay(server, war.defender, war.attacker, coins);
            int items = takeStock(server, t, colonyOf(server, war.attacker), s.lootShare(), s.raidItemCap());
            war.tally.scoreA += settings.peace().raidScore();
            tag.putLong("coins", taken);
            tag.putInt("items", items);
            text = name(server, war.attacker) + " raided " + t.name() + ": " + taken + " coins and " + items
                    + " goods taken (" + (int) attack + " raiders against " + home + " guards)";
        } else {
            war.tally.scoreB += settings.peace().raidScore();
            int losses = (int) Math.ceil(army.soldiers.size() * settings.armies().raidLosses());
            for (UUID id : new ArrayList<>(army.soldiers.subList(0, Math.min(losses, army.soldiers.size())))) {
                Persons.get(server).died(server, id, Optional.empty());
            }
            army.strength = (int) (army.strength * (1 - settings.armies().raidLosses()));
            tag.putInt("losses", losses);
            text = t.name() + "'s guards beat off a raid by " + name(server, war.attacker) + " (" + (int) attack
                    + " raiders against " + home + " guards, " + losses + " raiders fell)";
        }
        raided.put(t.id(), now(server));
        log(server, RAID_HISTORY, war, tag);
        report(server, war, text);
        goHome(server, war, army);
        if (war.goal == War.Goal.TRIBUTE && war.tally.scoreA >= settings.peace().tributeGoal()
                && war.state == War.State.ACTIVE) {
            end(server, war, Optional.of(true), tributeCap(war, true), "tribute");
        }
    }

    /** Moves a share of each of the target's stock (up to a cap in all) to the raider's stock. */
    private static int takeStock(MinecraftServer server, Colony target, Optional<Colony> raider, double share,
            int cap) {
        WorkComponents.Stock stock = target.get(WorkComponents.STOCK).orElse(WorkComponents.Stock.EMPTY);
        Map<ResourceLocation, Integer> left = new LinkedHashMap<>(stock.items());
        Map<ResourceLocation, Integer> loot = new LinkedHashMap<>();
        int taken = 0;
        for (Map.Entry<ResourceLocation, Integer> e : stock.items().entrySet()) {
            int n = Math.min(cap - taken, (int) Math.floor(e.getValue() * share));
            if (n > 0) {
                left.put(e.getKey(), e.getValue() - n);
                loot.merge(e.getKey(), n, Integer::sum);
                taken += n;
            }
        }
        if (taken == 0) {
            return 0;
        }
        Colonies colonies = Colonies.get(server);
        colonies.set(target, WorkComponents.STOCK, new WorkComponents.Stock(left));
        raider.ifPresent(r -> {
            Map<ResourceLocation, Integer> theirs = new LinkedHashMap<>(r.get(WorkComponents.STOCK)
                    .orElse(WorkComponents.Stock.EMPTY).items());
            loot.forEach((k, v) -> theirs.merge(k, v, Integer::sum));
            colonies.set(r, WorkComponents.STOCK, new WorkComponents.Stock(theirs));
        });
        return taken;
    }

    // ---- peace ----

    /** The most tribute a peace may ask of the other side: the score lead times coins per score. */
    public static int tributeCap(War war, boolean forAttacker) {
        int lead = forAttacker ? war.tally.scoreA - war.tally.scoreB : war.tally.scoreB - war.tally.scoreA;
        return Math.max(0, lead) * WarSettings.get().peace().coinsPerScore();
    }

    /** Offers peace: the proposer's side asks the other for {@code tribute} coins (capped by the score). */
    public Result propose(MinecraftServer server, UUID by, War war, int tribute) {
        Optional<Boolean> side = sideOf(server, by, war);
        if (side.isEmpty()) {
            return Result.fail("war.lotr_port.not_side", "You may not make peace in this war");
        }
        if (war.state == War.State.ENDED) {
            return Result.fail("war.lotr_port.ended", "That war is over");
        }
        int cap = tributeCap(war, side.get());
        if (tribute > cap) {
            return Result.fail("war.lotr_port.tribute_cap", "Your war score allows at most %s coins of tribute", cap);
        }
        war.proposal = Optional.of(new War.Proposal(side.get(), Math.max(0, tribute)));
        setDirty();
        report(server, war, name(server, side.get() ? war.attacker : war.defender) + " offers peace"
                + (tribute > 0 ? " for " + tribute + " coins" : "") + " (accept with /lotr_war accept "
                + war.shortId() + ")");
        return Result.done(war, "war.lotr_port.proposed", "Peace offered");
    }

    /** The other side accepts the offer on the table. */
    public Result accept(MinecraftServer server, UUID by, War war) {
        Optional<Boolean> side = sideOf(server, by, war);
        if (side.isEmpty() || war.proposal.isEmpty() || war.proposal.get().byAttacker() == side.get()) {
            return Result.fail("war.lotr_port.no_offer", "There is no offer of peace for you to accept");
        }
        War.Proposal p = war.proposal.get();
        end(server, war, p.tribute() > 0 ? Optional.of(p.byAttacker()) : Optional.empty(), p.tribute(), "peace");
        return Result.done(war, "war.lotr_port.peace", "Peace is made");
    }

    /** Exhaustion forces peace on the score: the side ahead gets the most tribute its lead allows. */
    private void forcePeace(MinecraftServer server, War war, String why) {
        int a = war.tally.scoreA;
        int b = war.tally.scoreB;
        Optional<Boolean> winner = a == b ? Optional.empty() : Optional.of(a > b);
        end(server, war, winner, winner.map(w -> tributeCap(war, w)).orElse(0), why);
    }

    /** Ends a war: the loser pays {@code tribute} to {@code attackerWon}'s side (empty = nobody pays). */
    public void end(MinecraftServer server, War war, Optional<Boolean> attackerWon, int tribute, String result) {
        if (war.state == War.State.ENDED) {
            return;
        }
        long paid = 0;
        if (attackerWon.isPresent() && tribute > 0) {
            paid = attackerWon.get() ? pay(server, war.defender, war.attacker, tribute)
                    : pay(server, war.attacker, war.defender, tribute);
        }
        war.state = War.State.ENDED;
        war.result = result;
        war.proposal = Optional.empty();
        for (Army army : war.armies) {
            if (army.stage != Army.Stage.RETURNING) {
                goHome(server, war, army);
            }
        }
        setDirty();
        CompoundTag tag = tag(war);
        tag.putString("result", result);
        attackerWon.ifPresent(w -> tag.putBoolean("attacker_won", w));
        tag.putLong("tribute", paid);
        tag.putInt("score_a", war.tally.scoreA);
        tag.putInt("score_b", war.tally.scoreB);
        log(server, ENDED_HISTORY, war, tag);
        report(server, war, "Peace between " + name(server, war.attacker) + " and " + name(server, war.defender)
                + " (" + result + ")" + (paid > 0 ? ": " + paid + " coins of tribute" : ""));
    }

    /** True for the attacking side, false for the defending side, empty if the player may not lead either. */
    private static Optional<Boolean> sideOf(MinecraftServer server, UUID player, War war) {
        if (colonyOf(server, war.attacker).map(c -> Permissions.can(player, c, Perm.WAR)).orElse(false)) {
            return Optional.of(true);
        }
        if (colonyOf(server, war.defender).map(c -> Permissions.can(player, c, Perm.WAR)).orElse(false)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    // ---- AI rulers ----

    /**
     * AI rulers' wars between their realms: declared without warning (both sides decide in the same daily step) and
     * fought in the abstract by {@code AiRulers}; ended with {@code war = false}.
     */
    public void aiWar(MinecraftServer server, ResourceLocation a, ResourceLocation b, boolean war) {
        Owner ra = new Owner.OfRealm(a);
        Owner rb = new Owner.OfRealm(b);
        if (war) {
            declare(server, ra, rb, War.Goal.TAKE_COLONY, "ai", Optional.empty(), 0);
        } else {
            between(ra, rb).ifPresent(w -> forcePeace(server, w, "peace"));
        }
    }

    /** Scores an AI siege (fought in the abstract) on the realms' war. */
    public void aiSiege(MinecraftServer server, ResourceLocation attacker, ResourceLocation defender, Colony colony,
            boolean won) {
        between(new Owner.OfRealm(attacker), new Owner.OfRealm(defender)).ifPresent(w -> {
            boolean attackerSide = w.attacker.equals(new Owner.OfRealm(attacker));
            int score = WarSettings.get().peace().siegeScore();
            if (won == attackerSide) {
                w.tally.scoreA += score;
            } else {
                w.tally.scoreB += score;
            }
            report(server, w, name(server, new Owner.OfRealm(attacker)) + (won ? " took " : " failed to take ")
                    + colony.name());
            setDirty();
        });
    }

    // ---- bookkeeping ----

    /** Soldiers who fall cost their side exhaustion and give the other side score. */
    private static void onDied(SocietyEvent.Died event) {
        Person p = event.person();
        Optional<UUID> colony = p.owner().colonyId();
        if (colony.isEmpty() || !p.has(CoreComponents.UNIT)) {
            return;
        }
        Wars wars = get(event.server());
        WarSettings.Peace s = WarSettings.get().peace();
        for (War w : wars.of(Owner.colony(colony.get()))) {
            if (w.state != War.State.ACTIVE) {
                continue;
            }
            if (w.attacker.equals(Owner.colony(colony.get()))) {
                w.tally.exhaustionA += s.exhaustionPerSoldier();
                w.tally.scoreB += s.soldierScore();
            } else {
                w.tally.exhaustionB += s.exhaustionPerSoldier();
                w.tally.scoreA += s.soldierScore();
            }
            wars.setDirty();
        }
    }

    /** A player who logs in hears what happened in their colonies' wars lately. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        Wars wars = get(server);
        long since = now(server) - WarSettings.get().peace().reportTicks();
        for (War w : wars.wars.values()) {
            boolean mine = colonyOf(server, w.attacker).map(c -> Permissions.isMember(player.getUUID(), c))
                    .orElse(false) || colonyOf(server, w.defender).map(c -> Permissions.isMember(player.getUUID(),
                            c)).orElse(false);
            if (!mine) {
                continue;
            }
            for (War.Report r : w.reports) {
                if (r.time() >= since) {
                    player.sendSystemMessage(Component.literal("[War] " + r.text()).withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    private void bringHome(MinecraftServer server) {
        Persons persons = Persons.get(server);
        for (UUID id : new ArrayList<>(homecoming)) {
            Optional<Person> p = persons.get(id);
            Optional<Colony> home = p.flatMap(x -> Society.colonyOf(server, x));
            if (p.isEmpty() || !p.get().alive() || home.isEmpty()) {
                homecoming.remove(id);
                setDirty();
                continue;
            }
            ServerLevel level = server.getLevel(home.get().dimension());
            BlockPos hall = home.get().hall();
            if (level == null || !level.isLoaded(hall)) {
                continue;
            }
            homecoming.remove(id);
            setDirty();
            if (level.getEntity(id) != null) {
                continue;
            }
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(2, 0, 2));
            LotrNpc.bodyFor(level, p.get()).ifPresent(npc -> {
                npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                level.addFreshEntity(npc);
            });
        }
    }

    /** Tells both sides' online members, and keeps the line for those who log in later. */
    private void report(MinecraftServer server, War war, String text) {
        war.reports.add(new War.Report(now(server), text));
        while (war.reports.size() > MAX_REPORTS) {
            war.reports.remove(0);
        }
        setDirty();
        Optional<Colony> a = colonyOf(server, war.attacker);
        Optional<Colony> b = colonyOf(server, war.defender);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (a.map(c -> Permissions.isMember(p.getUUID(), c)).orElse(false)
                    || b.map(c -> Permissions.isMember(p.getUUID(), c)).orElse(false)) {
                p.sendSystemMessage(Component.literal("[War] " + text).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    static long treasury(Colony colony) {
        return colony.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
    }

    /** Moves up to {@code coins} from one party's treasury to the other's; returns what moved. */
    static long pay(MinecraftServer server, Owner from, Owner to, long coins) {
        Optional<Colony> payer = colonyOf(server, from);
        if (payer.isEmpty() || coins <= 0) {
            return 0;
        }
        long moved = Math.min(coins, treasury(payer.get()));
        Colonies colonies = Colonies.get(server);
        colonies.set(payer.get(), CoreComponents.TREASURY, new CoreComponents.Treasury(treasury(payer.get()) - moved));
        colonyOf(server, to).ifPresent(c -> colonies.set(c, CoreComponents.TREASURY,
                new CoreComponents.Treasury(treasury(c) + moved)));
        return moved;
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    static Component minutes(int ticks) {
        int m = Math.max(0, ticks) / 1200;
        int sec = Math.max(0, ticks) % 1200 / 20;
        return Component.literal(m > 0 ? m + " min" + (sec > 0 ? " " + sec + " s" : "") : sec + " s");
    }

    private static CompoundTag tag(War war) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("war", war.id);
        tag.putString("attacker", war.attacker.encode());
        tag.putString("defender", war.defender.encode());
        return tag;
    }

    private static void log(MinecraftServer server, ResourceLocation type, War war, CompoundTag tag) {
        Optional<Colony> target = colonyOf(server, war.defender);
        HistoryLog.get(server).append(new HistoryEntry(now(server), type,
                target.map(Colony::dimension).orElse(Level.OVERWORLD), target.map(Colony::hall), tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("wars", War.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(wars.values())).getOrThrow());
        tag.put("raided", Codec.unboundedMap(Codec.STRING, Codec.LONG).encodeStart(NbtOps.INSTANCE,
                raided.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(),
                        Map.Entry::getValue))).getOrThrow());
        tag.put("homecoming", UUIDUtil.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(homecoming))
                .getOrThrow());
        return tag;
    }

    public static Wars load(CompoundTag tag, HolderLookup.Provider registries) {
        Wars out = new Wars();
        War.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("wars")).resultOrPartial(e -> LotrPort.LOG.error(
                "[war] {}", e)).ifPresent(list -> list.forEach(w -> out.wars.put(w.id, w)));
        if (tag.contains("raided")) {
            Codec.unboundedMap(Codec.STRING, Codec.LONG).parse(NbtOps.INSTANCE, tag.get("raided"))
                    .resultOrPartial(e -> LotrPort.LOG.error("[war] {}", e))
                    .ifPresent(m -> m.forEach((k, v) -> out.raided.put(UUID.fromString(k), v)));
        }
        if (tag.contains("homecoming")) {
            UUIDUtil.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("homecoming")).result()
                    .ifPresent(out.homecoming::addAll);
        }
        return out;
    }

    /** For tests: forgets a war and its raid cooldowns. */
    public void forget(War war) {
        wars.remove(war.id);
        war.defender.colonyId().ifPresent(raided::remove);
        setDirty();
    }
}
