package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A party on the road (saved in {@link Journeys}): who and what goes, the way, and how far along it they are. The
 * same for a caravan and a marching army; its {@code kind} ({@link JourneyKind}) says what happens when it gets
 * there. Distance is in route cost (blocks at speed 1), so a party's place is {@code route.at(done)}.
 */
public final class Journey {
    /** Where a journey is in its life. */
    public enum Stage {
        /** Its route is being worked out (off the server thread). */
        PLANNING,
        /** On its way out. */
        OUTBOUND,
        /** On its way home (caravans). */
        RETURNING,
        /** Over: kept a moment for the map and the ledger, then forgotten. */
        DONE
    }

    /** A steed in a party: its kind ({@code journey_steed/}) and the animal itself, as saved. */
    public record Steed(ResourceLocation def, CompoundTag entity) {
        public static final Codec<Steed> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("def").forGetter(Steed::def),
                CompoundTag.CODEC.fieldOf("entity").forGetter(Steed::entity))
                .apply(i, Steed::new));
    }

    private static final Codec<Route> ROUTE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("xs").forGetter(r -> java.util.Arrays.stream(r.xs()).boxed().toList()),
            Codec.INT.listOf().fieldOf("zs").forGetter(r -> java.util.Arrays.stream(r.zs()).boxed().toList()),
            Codec.DOUBLE.listOf().fieldOf("costs").forGetter(r -> java.util.Arrays.stream(r.costs()).boxed().toList()),
            Codec.DOUBLE.optionalFieldOf("road", 0.0).forGetter(Route::roadShare))
            .apply(i, (xs, zs, cs, road) -> new Route(xs.stream().mapToInt(Integer::intValue).toArray(),
                    zs.stream().mapToInt(Integer::intValue).toArray(),
                    cs.stream().mapToDouble(Double::doubleValue).toArray(), road)));

    public static final Codec<Journey> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(j -> j.id),
            ResourceLocation.CODEC.fieldOf("kind").forGetter(j -> j.kind),
            UUIDUtil.CODEC.optionalFieldOf("from").forGetter(j -> Optional.ofNullable(j.from)),
            UUIDUtil.CODEC.optionalFieldOf("to").forGetter(j -> Optional.ofNullable(j.to)),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(j -> j.dimension),
            ROUTE_CODEC.optionalFieldOf("route", Route.NONE).forGetter(j -> j.route),
            Codec.DOUBLE.optionalFieldOf("done", 0.0).forGetter(j -> j.done),
            UUIDUtil.CODEC.listOf().optionalFieldOf("people", List.of()).forGetter(j -> j.people),
            Steed.CODEC.listOf().optionalFieldOf("steeds", List.of()).forGetter(j -> j.steeds),
            Codec.INT.optionalFieldOf("wagons", 0).forGetter(j -> j.wagons),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("cargo", Map.of())
                    .forGetter(j -> j.cargo),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(j -> j.speed),
            Codec.STRING.xmap(Stage::valueOf, Stage::name).optionalFieldOf("stage", Stage.PLANNING)
                    .forGetter(j -> j.stage),
            Codec.LONG.optionalFieldOf("started", 0L).forGetter(j -> j.started),
            CompoundTag.CODEC.optionalFieldOf("extra", new CompoundTag()).forGetter(j -> j.extra),
            Codec.INT.listOf().optionalFieldOf("ends", List.of()).forGetter(j -> List.of(j.fromX, j.fromZ, j.toX,
                    j.toZ)))
            .apply(i, Journey::new));

    public final UUID id;
    public final ResourceLocation kind;
    /** The colony it set out from, if any. */
    public final UUID from;
    /** The colony it is going to, if any. */
    public final UUID to;
    public final ResourceKey<Level> dimension;
    public Route route;
    /** Route cost covered. */
    public double done;
    public final List<UUID> people;
    public final List<Steed> steeds;
    public int wagons;
    public final Map<ResourceLocation, Integer> cargo;
    /** The party's speed against walking pace ({@link PartyRules}). */
    public double speed;
    public Stage stage;
    public long started;
    /** What the kind keeps (an army's war, a supply route's id...). */
    public final CompoundTag extra;
    /** The two ends in blocks: start x, z, end x, z (for planning, and for the map while planning). */
    public int fromX;
    public int fromZ;
    public int toX;
    public int toZ;

    // not saved: bodies in the world
    /** Whether its people, steeds and wagons are in the world now. */
    public transient boolean embodied;
    /** Bodies stay away until this game time (after giving up on stuck bodies). */
    public transient long noBodiesUntil;
    /** The best {@code done} the bodies reached, and when: for the stuck rules. */
    public transient double bestDone;
    public transient long bestAt;
    /** The route leg the bodies are on. */
    public transient int leg;

    private Journey(UUID id, ResourceLocation kind, Optional<UUID> from, Optional<UUID> to,
            ResourceKey<Level> dimension, Route route, double done, List<UUID> people, List<Steed> steeds,
            int wagons, Map<ResourceLocation, Integer> cargo, double speed, Stage stage, long started,
            CompoundTag extra, List<Integer> ends) {
        this.id = id;
        this.kind = kind;
        this.from = from.orElse(null);
        this.to = to.orElse(null);
        this.dimension = dimension;
        this.route = route;
        this.done = done;
        this.people = new ArrayList<>(people);
        this.steeds = new ArrayList<>(steeds);
        this.wagons = wagons;
        this.cargo = new LinkedHashMap<>(cargo);
        this.speed = speed;
        this.stage = stage;
        this.started = started;
        this.extra = extra.copy();
        if (ends.size() == 4) {
            fromX = ends.get(0);
            fromZ = ends.get(1);
            toX = ends.get(2);
            toZ = ends.get(3);
        }
    }

    /** A new party, to be planned: from (x1, z1) to (x2, z2). */
    public static Journey create(ResourceLocation kind, UUID from, UUID to, ResourceKey<Level> dimension, int x1,
            int z1, int x2, int z2, List<UUID> people, List<Steed> steeds, int wagons,
            Map<ResourceLocation, Integer> cargo, double speed, long now) {
        return new Journey(UUID.randomUUID(), kind, Optional.ofNullable(from), Optional.ofNullable(to), dimension,
                Route.NONE, 0, people, steeds, wagons, cargo, speed, Stage.PLANNING, now, new CompoundTag(),
                List.of(x1, z1, x2, z2));
    }

    /** Where the party is now, in blocks (x, z); the start while it is planning. */
    public double[] position() {
        if (!route.found()) {
            return new double[] {fromX, fromZ};
        }
        double[] p = route.at(done);
        return new double[] {p[0], p[1]};
    }

    /** Route cost still to go. */
    public double left() {
        return Math.max(0, route.total() - done);
    }

    /** Seconds still to go at its speed. */
    public double secondsLeft(JourneySettings s) {
        return left() / Math.max(0.01, speed * s.walkSpeed());
    }

    public boolean moving() {
        return stage == Stage.OUTBOUND || stage == Stage.RETURNING;
    }
}
