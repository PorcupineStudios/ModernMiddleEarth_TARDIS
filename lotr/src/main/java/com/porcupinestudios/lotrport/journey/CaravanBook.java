package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * A colony's caravan arrangements (colony component {@code caravans}): standing routes it sends caravans on, the
 * trades it offers caravans from friendly colonies, and the floors it never lets caravans take its goods below.
 */
public record CaravanBook(List<Route> routes, List<CaravanRules.Offer> offers, Map<String, Integer> keep, int next) {
    public static final CaravanBook EMPTY = new CaravanBook(List.of(), List.of(), Map.of(), 1);

    /**
     * A standing route.
     *
     * @param id its number in the colony (for commands and screens)
     * @param to the colony it goes to
     * @param send what it loads at home and takes there
     * @param bring what it loads there and brings home: from your own colony as it is, from another's only through
     *     that colony's trade offers (see {@code sell})
     * @param every send one every this many days (0: only when sent by hand)
     * @param people how many colonists go
     * @param sell at another's colony: bring home what didn't trade (true) or leave it there as supplies (false)
     * @param lastDay the day the last one set out
     */
    public record Route(int id, UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring, int every,
            int people, boolean sell, long lastDay) {
        public static final Codec<CaravanRules.Amount> AMOUNT = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(CaravanRules.Amount::item),
                Codec.INT.fieldOf("count").forGetter(CaravanRules.Amount::count))
                .apply(i, CaravanRules.Amount::new));

        public static final Codec<Route> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Route::id),
                UUIDUtil.CODEC.fieldOf("to").forGetter(Route::to),
                AMOUNT.listOf().optionalFieldOf("send", List.of()).forGetter(Route::send),
                AMOUNT.listOf().optionalFieldOf("bring", List.of()).forGetter(Route::bring),
                Codec.INT.optionalFieldOf("every", 0).forGetter(Route::every),
                Codec.INT.optionalFieldOf("people", 1).forGetter(Route::people),
                Codec.BOOL.optionalFieldOf("sell", false).forGetter(Route::sell),
                Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Route::lastDay))
                .apply(i, Route::new));

        public Route sentOn(long day) {
            return new Route(id, to, send, bring, every, people, sell, day);
        }
    }

    public static final Codec<CaravanRules.Amount> AMOUNT = Route.AMOUNT;

    public static final Codec<CaravanRules.Offer> OFFER = RecordCodecBuilder.create(i -> i.group(
            AMOUNT.fieldOf("give").forGetter(CaravanRules.Offer::give),
            AMOUNT.fieldOf("want").forGetter(CaravanRules.Offer::want),
            Codec.INT.optionalFieldOf("keep", 0).forGetter(CaravanRules.Offer::keep),
            Codec.INT.optionalFieldOf("limit", 0).forGetter(CaravanRules.Offer::limit))
            .apply(i, CaravanRules.Offer::new));

    public static final Codec<CaravanBook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Route.CODEC.listOf().optionalFieldOf("routes", List.of()).forGetter(CaravanBook::routes),
            OFFER.listOf().optionalFieldOf("offers", List.of()).forGetter(CaravanBook::offers),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("keep", Map.of()).forGetter(CaravanBook::keep),
            Codec.INT.optionalFieldOf("next", 1).forGetter(CaravanBook::next))
            .apply(i, CaravanBook::new));

    public static final ComponentType<CaravanBook> TYPE = ComponentType.register(LotrPort.id("caravans"), CODEC);

    public Optional<Route> route(int id) {
        return routes.stream().filter(r -> r.id() == id).findFirst();
    }

    public int keepOf(String item) {
        return keep.getOrDefault(item, 0);
    }

    public CaravanBook withRoute(Route r) {
        List<Route> out = new java.util.ArrayList<>(routes.stream().filter(x -> x.id() != r.id()).toList());
        out.add(r);
        out.sort(java.util.Comparator.comparingInt(Route::id));
        return new CaravanBook(out, offers, keep, Math.max(next, r.id() + 1));
    }

    public CaravanBook withoutRoute(int id) {
        return new CaravanBook(routes.stream().filter(x -> x.id() != id).toList(), offers, keep, next);
    }

    public CaravanBook withOffers(List<CaravanRules.Offer> o) {
        return new CaravanBook(routes, List.copyOf(o), keep, next);
    }

    public CaravanBook withKeep(Map<String, Integer> k) {
        return new CaravanBook(routes, offers, Map.copyOf(k), next);
    }
}
