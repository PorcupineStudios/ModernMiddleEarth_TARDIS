package com.porcupinestudios.lotrport.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.society.Owner;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * One war between two parties (colonies, or AI realms), from declaration to peace. Mutable; changed only by
 * {@link Wars}, which saves it and writes history. The attacker is side A, the defender side B.
 */
public final class War {
    /** What the attacker wants; reaching it ends the war. */
    public enum Goal implements StringRepresentable {
        /** Coins: ends when the attacker's score reaches the tribute goal, or a siege is won. */
        TRIBUTE,
        /** The defender's colony becomes the attacker owner's. */
        TAKE_COLONY,
        /** The defender's colony becomes the attacker's vassal (pays it every day). */
        VASSAL,
        /** A vassal ({@code subject}) of the defender goes free. */
        FREE_VASSAL;

        public static final Codec<Goal> CODEC = StringRepresentable.fromEnum(Goal::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum State implements StringRepresentable {
        /** Declared; nobody may fight until {@code activeFrom}. */
        WARNING,
        ACTIVE,
        ENDED;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Score and exhaustion for both sides. */
    public static final class Tally {
        static final Codec<Tally> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("score_a", 0).forGetter(t -> t.scoreA),
                Codec.INT.optionalFieldOf("score_b", 0).forGetter(t -> t.scoreB),
                Codec.DOUBLE.optionalFieldOf("exhaustion_a", 0.0).forGetter(t -> t.exhaustionA),
                Codec.DOUBLE.optionalFieldOf("exhaustion_b", 0.0).forGetter(t -> t.exhaustionB),
                Codec.LONG.optionalFieldOf("day", -1L).forGetter(t -> t.day))
                .apply(i, Tally::new));

        int scoreA;
        int scoreB;
        double exhaustionA;
        double exhaustionB;
        /** The last colony day exhaustion was added for. */
        long day;

        Tally(int scoreA, int scoreB, double exhaustionA, double exhaustionB, long day) {
            this.scoreA = scoreA;
            this.scoreB = scoreB;
            this.exhaustionA = exhaustionA;
            this.exhaustionB = exhaustionB;
            this.day = day;
        }

        public int scoreA() {
            return scoreA;
        }

        public int scoreB() {
            return scoreB;
        }

        public double exhaustionA() {
            return exhaustionA;
        }

        public double exhaustionB() {
            return exhaustionB;
        }
    }

    /** An offer of peace: {@code byAttacker}'s side asks the other for {@code tribute} coins (0 = white peace). */
    public record Proposal(boolean byAttacker, int tribute) {
        static final Codec<Proposal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("by_attacker").forGetter(Proposal::byAttacker),
                Codec.INT.fieldOf("tribute").forGetter(Proposal::tribute))
                .apply(i, Proposal::new));
    }

    /** A line of what happened, for the players told on login. */
    public record Report(long time, String text) {
        static final Codec<Report> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Report::time),
                Codec.STRING.fieldOf("text").forGetter(Report::text))
                .apply(i, Report::new));
    }

    public static final Codec<War> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(w -> w.id),
            Owner.CODEC.fieldOf("attacker").forGetter(w -> w.attacker),
            Owner.CODEC.fieldOf("defender").forGetter(w -> w.defender),
            Goal.CODEC.fieldOf("goal").forGetter(w -> w.goal),
            UUIDUtil.CODEC.optionalFieldOf("subject").forGetter(w -> w.subject),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(w -> w.reason),
            Codec.LONG.fieldOf("declared").forGetter(w -> w.declared),
            Codec.LONG.fieldOf("active_from").forGetter(w -> w.activeFrom),
            State.CODEC.fieldOf("state").forGetter(w -> w.state),
            Tally.CODEC.optionalFieldOf("tally", new Tally(0, 0, 0, 0, -1)).forGetter(w -> w.tally),
            Army.CODEC.listOf().optionalFieldOf("armies", List.of()).forGetter(w -> w.armies),
            Proposal.CODEC.optionalFieldOf("proposal").forGetter(w -> w.proposal),
            Report.CODEC.listOf().optionalFieldOf("reports", List.of()).forGetter(w -> w.reports),
            Codec.STRING.optionalFieldOf("result", "").forGetter(w -> w.result))
            .apply(i, War::new));

    final UUID id;
    final Owner attacker;
    final Owner defender;
    final Goal goal;
    final Optional<UUID> subject;
    final String reason;
    final long declared;
    final long activeFrom;
    State state;
    final Tally tally;
    final List<Army> armies;
    Optional<Proposal> proposal;
    final List<Report> reports;
    /** How it ended: {@code taken}, {@code vassal}, {@code freed}, {@code tribute}, {@code peace}, {@code exhausted}. */
    String result;

    War(UUID id, Owner attacker, Owner defender, Goal goal, Optional<UUID> subject, String reason, long declared,
            long activeFrom, State state, Tally tally, List<Army> armies, Optional<Proposal> proposal,
            List<Report> reports, String result) {
        this.id = id;
        this.attacker = attacker;
        this.defender = defender;
        this.goal = goal;
        this.subject = subject;
        this.reason = reason;
        this.declared = declared;
        this.activeFrom = activeFrom;
        this.state = state;
        this.tally = tally;
        this.armies = new ArrayList<>(armies);
        this.proposal = proposal;
        this.reports = new ArrayList<>(reports);
        this.result = result;
    }

    public UUID id() {
        return id;
    }

    public Owner attacker() {
        return attacker;
    }

    public Owner defender() {
        return defender;
    }

    public Goal goal() {
        return goal;
    }

    public Optional<UUID> subject() {
        return subject;
    }

    public String reason() {
        return reason;
    }

    public long declared() {
        return declared;
    }

    public long activeFrom() {
        return activeFrom;
    }

    public State state() {
        return state;
    }

    public Tally tally() {
        return tally;
    }

    public List<Army> armies() {
        return List.copyOf(armies);
    }

    public Optional<Proposal> proposal() {
        return proposal;
    }

    public List<Report> reports() {
        return List.copyOf(reports);
    }

    public String result() {
        return result;
    }

    /** Whether this party is one of the two sides. */
    public boolean involves(Owner party) {
        return attacker.equals(party) || defender.equals(party);
    }

    /** The first characters of the id, for chat and commands. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    /** An army of the attacker on its way, fighting, or coming home. */
    public static final class Army {
        public enum Kind implements StringRepresentable {
            /** Real soldiers that appear by the Hall and must hold its zone; both sides online. */
            SIEGE,
            /** A small party against an offline colony, fought in the abstract; never breaks a block. */
            RAID;

            public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        public enum Stage implements StringRepresentable {
            MARCHING,
            SIEGE,
            RETURNING;

            public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        static final Codec<Army> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(a -> a.id),
                UUIDUtil.CODEC.listOf().optionalFieldOf("soldiers", List.of()).forGetter(a -> a.soldiers),
                Codec.INT.optionalFieldOf("strength", 0).forGetter(a -> a.strength),
                Kind.CODEC.fieldOf("kind").forGetter(a -> a.kind),
                BlockPos.CODEC.fieldOf("from").forGetter(a -> a.from),
                Codec.LONG.fieldOf("arrives").forGetter(a -> a.arrives),
                Stage.CODEC.fieldOf("stage").forGetter(a -> a.stage),
                Codec.LONG.optionalFieldOf("held", 0L).forGetter(a -> a.held),
                Codec.LONG.optionalFieldOf("attacker_away", -1L).forGetter(a -> a.attackerAway),
                Codec.LONG.optionalFieldOf("defender_away", -1L).forGetter(a -> a.defenderAway),
                Codec.BOOL.optionalFieldOf("landed", false).forGetter(a -> a.landed))
                .apply(i, Army::new));

        final UUID id;
        /** The Persons marching (soldiers of the attacking colony). */
        final List<UUID> soldiers;
        /** Counted soldiers without bodies (AI armies). */
        int strength;
        final Kind kind;
        /** Where it set out from (for the march home). */
        final BlockPos from;
        /** When it reaches the target (marching) or home (returning). */
        long arrives;
        Stage stage;
        /** Ticks the Hall's zone has been held. */
        long held;
        long attackerAway;
        long defenderAway;
        /** Its soldiers' bodies have appeared by the Hall. */
        boolean landed;

        Army(UUID id, List<UUID> soldiers, int strength, Kind kind, BlockPos from, long arrives, Stage stage,
                long held, long attackerAway, long defenderAway, boolean landed) {
            this.id = id;
            this.soldiers = new ArrayList<>(soldiers);
            this.strength = strength;
            this.kind = kind;
            this.from = from;
            this.arrives = arrives;
            this.stage = stage;
            this.held = held;
            this.attackerAway = attackerAway;
            this.defenderAway = defenderAway;
            this.landed = landed;
        }

        public UUID id() {
            return id;
        }

        public List<UUID> soldiers() {
            return List.copyOf(soldiers);
        }

        public int strength() {
            return strength;
        }

        public Kind kind() {
            return kind;
        }

        public long arrives() {
            return arrives;
        }

        public Stage stage() {
            return stage;
        }

        public long held() {
            return held;
        }

        public boolean landed() {
            return landed;
        }
    }
}
