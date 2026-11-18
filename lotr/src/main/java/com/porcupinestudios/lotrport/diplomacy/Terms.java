package com.porcupinestudios.lotrport.diplomacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * What an offer between two sides holds; "give" is from the proposer to the other side, "ask" the other way. Every
 * term is carried out together on acceptance, or none is ({@link Treaties#accept}).
 *
 * @param giveCoins coins from the proposer's capital treasury to the other's
 * @param askCoins coins the other way
 * @param giveLand map squares the proposer hands over (its held land nearest the other's capital)
 * @param askLand map squares asked of the other side
 * @param giveColony one of the proposer's colonies handed over
 * @param askColony one of the other side's colonies asked for
 * @param trade a trade agreement: caravans trade and supply both ways, as between friends
 * @param truceDays a truce: neither side may declare war for this many days
 * @param alliance an alliance: allies while it lasts
 * @param vassal who swears to whom
 * @param endWar ends every war between the two sides
 */
public record Terms(long giveCoins, long askCoins, int giveLand, int askLand, Optional<UUID> giveColony,
        Optional<UUID> askColony, boolean trade, int truceDays, boolean alliance, Vassal vassal, boolean endWar) {
    public static final Terms NONE = new Terms(0, 0, 0, 0, Optional.empty(), Optional.empty(), false, 0, false,
            Vassal.NONE, false);

    public enum Vassal implements StringRepresentable {
        NONE,
        /** The other side becomes the proposer's vassal. */
        THEY_SERVE,
        /** The proposer becomes the other side's vassal. */
        WE_SERVE;

        public static final Codec<Vassal> CODEC = StringRepresentable.fromEnum(Vassal::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Terms> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("give_coins", 0L).forGetter(Terms::giveCoins),
            Codec.LONG.optionalFieldOf("ask_coins", 0L).forGetter(Terms::askCoins),
            Codec.INT.optionalFieldOf("give_land", 0).forGetter(Terms::giveLand),
            Codec.INT.optionalFieldOf("ask_land", 0).forGetter(Terms::askLand),
            UUIDUtil.CODEC.optionalFieldOf("give_colony").forGetter(Terms::giveColony),
            UUIDUtil.CODEC.optionalFieldOf("ask_colony").forGetter(Terms::askColony),
            Codec.BOOL.optionalFieldOf("trade", false).forGetter(Terms::trade),
            Codec.INT.optionalFieldOf("truce_days", 0).forGetter(Terms::truceDays),
            Codec.BOOL.optionalFieldOf("alliance", false).forGetter(Terms::alliance),
            Vassal.CODEC.optionalFieldOf("vassal", Vassal.NONE).forGetter(Terms::vassal),
            Codec.BOOL.optionalFieldOf("end_war", false).forGetter(Terms::endWar))
            .apply(i, Terms::new));

    public boolean empty() {
        return equals(NONE);
    }

    public Terms withGiveCoins(long v) {
        return new Terms(v, askCoins, giveLand, askLand, giveColony, askColony, trade, truceDays, alliance, vassal, endWar);
    }

    public Terms withAskCoins(long v) {
        return new Terms(giveCoins, v, giveLand, askLand, giveColony, askColony, trade, truceDays, alliance, vassal, endWar);
    }

    public Terms withGiveLand(int v) {
        return new Terms(giveCoins, askCoins, v, askLand, giveColony, askColony, trade, truceDays, alliance, vassal, endWar);
    }

    public Terms withAskLand(int v) {
        return new Terms(giveCoins, askCoins, giveLand, v, giveColony, askColony, trade, truceDays, alliance, vassal, endWar);
    }

    public Terms withGiveColony(Optional<UUID> v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, v, askColony, trade, truceDays, alliance, vassal, endWar);
    }

    public Terms withAskColony(Optional<UUID> v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, giveColony, v, trade, truceDays, alliance, vassal, endWar);
    }

    public Terms withTrade(boolean v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, giveColony, askColony, v, truceDays, alliance, vassal, endWar);
    }

    public Terms withTruceDays(int v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, giveColony, askColony, trade, v, alliance, vassal, endWar);
    }

    public Terms withAlliance(boolean v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, giveColony, askColony, trade, truceDays, v, vassal, endWar);
    }

    public Terms withVassal(Vassal v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, giveColony, askColony, trade, truceDays, alliance, v, endWar);
    }

    public Terms withEndWar(boolean v) {
        return new Terms(giveCoins, askCoins, giveLand, askLand, giveColony, askColony, trade, truceDays, alliance, vassal, v);
    }

    /** The terms seen from the other side (what they give is what we ask). */
    public Terms flipped() {
        Vassal v = vassal == Vassal.THEY_SERVE ? Vassal.WE_SERVE : vassal == Vassal.WE_SERVE ? Vassal.THEY_SERVE
                : Vassal.NONE;
        return new Terms(askCoins, giveCoins, askLand, giveLand, askColony, giveColony, trade, truceDays, alliance, v,
                endWar);
    }

    /** One line per term, from the proposer's view ("gives", "asks"); colony names come from {@code colonyName}. */
    public List<String> describe(String proposer, String other, java.util.function.Function<UUID, String> colonyName) {
        List<String> out = new ArrayList<>();
        if (endWar) {
            out.add("Peace: every war between them ends");
        }
        if (giveCoins > 0) {
            out.add(proposer + " pays " + giveCoins + " coins");
        }
        if (askCoins > 0) {
            out.add(other + " pays " + askCoins + " coins");
        }
        if (giveLand > 0) {
            out.add(proposer + " cedes " + giveLand + " squares of land");
        }
        if (askLand > 0) {
            out.add(other + " cedes " + askLand + " squares of land");
        }
        giveColony.ifPresent(c -> out.add(proposer + " hands over " + colonyName.apply(c)));
        askColony.ifPresent(c -> out.add(other + " hands over " + colonyName.apply(c)));
        if (trade) {
            out.add("Trade agreement");
        }
        if (truceDays > 0) {
            out.add("Truce for " + truceDays + " days");
        }
        if (alliance) {
            out.add("Alliance");
        }
        if (vassal == Vassal.THEY_SERVE) {
            out.add(other + " swears to " + proposer);
        } else if (vassal == Vassal.WE_SERVE) {
            out.add(proposer + " swears to " + other);
        }
        return out;
    }
}
