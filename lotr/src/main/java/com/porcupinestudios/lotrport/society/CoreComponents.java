package com.porcupinestudios.lotrport.society;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.StringRepresentable;

/**
 * The component types the core itself uses. Features add their own the same way (a static {@code ComponentType}
 * in their package), registered before any world loads: {@link #init()} runs from the mod constructor.
 */
public final class CoreComponents {
    private CoreComponents() {}

    /** A member of the colony that owns the Person. {@code rare} = joined by a recruitment quest. */
    public record Citizen(long joined, boolean rare) {
        public static final Codec<Citizen> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("joined", 0L).forGetter(Citizen::joined),
                Codec.BOOL.optionalFieldOf("rare", false).forGetter(Citizen::rare))
                .apply(i, Citizen::new));
    }

    /** Waiting at the Hall of the colony that owns the Person; {@code days} waited so far. */
    public record Applicant(int days) {
        public static final Codec<Applicant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("days", 0).forGetter(Applicant::days))
                .apply(i, Applicant::new));

        public Applicant older() {
            return new Applicant(days + 1);
        }
    }

    /** What a unit was told to do. */
    public enum Order implements StringRepresentable {
        /** Stands where it is and defends itself. */
        NONE,
        /** Follows its commander (a player owner) and fights for them. */
        FOLLOW,
        /** Holds its post and fights anything hostile near it. */
        GUARD;

        public static final Codec<Order> CODEC = StringRepresentable.fromEnum(Order::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A fighter: a hired unit (owned by a player) or a colony soldier (owned by a colony). The same component on both,
     * so orders, squads and the unit screen work for either.
     *
     * @param post where it guards (orders GUARD)
     * @param hired game time it was hired or enlisted
     * @param wage coins per colony day paid by a colony owner (0 for units a player paid once)
     * @param unpaid colony days its owner could not pay
     * @param squad the squad it belongs to, a name unique per owner
     */
    public record Unit(Order order, Optional<GlobalPos> post, long hired, int wage, int unpaid,
            Optional<String> squad) {
        public static final Codec<Unit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Order.CODEC.optionalFieldOf("order", Order.FOLLOW).forGetter(Unit::order),
                GlobalPos.CODEC.optionalFieldOf("post").forGetter(Unit::post),
                Codec.LONG.optionalFieldOf("hired", 0L).forGetter(Unit::hired),
                Codec.INT.optionalFieldOf("wage", 0).forGetter(Unit::wage),
                Codec.INT.optionalFieldOf("unpaid", 0).forGetter(Unit::unpaid),
                Codec.STRING.optionalFieldOf("squad").forGetter(Unit::squad))
                .apply(i, Unit::new));

        public static Unit hired(long time) {
            return new Unit(Order.FOLLOW, Optional.empty(), time, 0, 0, Optional.empty());
        }

        public Unit withOrder(Order next, Optional<GlobalPos> at) {
            return new Unit(next, next == Order.GUARD ? at : Optional.empty(), hired, wage, unpaid, squad);
        }

        public Unit withWage(int coins) {
            return new Unit(order, post, hired, coins, unpaid, squad);
        }

        /** {@code days} colony days without pay (0 = paid up). */
        public Unit withUnpaid(int days) {
            return new Unit(order, post, hired, wage, days, squad);
        }

        /** A named group of units that take orders together (empty = none). */
        public Unit withSquad(Optional<String> name) {
            return new Unit(order, post, hired, wage, unpaid, name.filter(n -> !n.isEmpty()));
        }
    }

    /** A colony's coins (silver coins), paid in and spent by systems: soldiers' wages, building, trade. */
    public record Treasury(long coins) {
        public static final Codec<Treasury> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("coins", 0L).forGetter(Treasury::coins))
                .apply(i, Treasury::new));
        public static final Treasury EMPTY = new Treasury(0);

        public Treasury plus(long n) {
            return new Treasury(Math.max(0, coins + n));
        }
    }

    public static final ComponentType<Citizen> CITIZEN = ComponentType.register(LotrPort.id("citizen"),
            Citizen.CODEC);
    public static final ComponentType<Applicant> APPLICANT = ComponentType.register(LotrPort.id("applicant"),
            Applicant.CODEC);
    public static final ComponentType<Unit> UNIT = ComponentType.register(LotrPort.id("unit"), Unit.CODEC);
    /** On a colony. */
    public static final ComponentType<Treasury> TREASURY = ComponentType.register(LotrPort.id("treasury"),
            Treasury.CODEC);

    /** Loads this class (and so registers the types) from the mod constructor. */
    public static void init() {}
}
