package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
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
     * @param every send one every this many days (0: only by hand, or by {@code when})
     * @param people how many colonists go
     * @param sell at another's colony: bring home what didn't trade (true) or leave it there as supplies (false)
     * @param lastDay the day the last one set out
     * @param when sets out by itself while this holds (and none of this route is on the road)
     * @param name what the player calls it ("" for "Route n")
     * @param logic one condition (an older form; see {@link #program})
     * @param rules its rules: if/then/else with conditions and {@link Outcome}s, run in order every check
     */
    public record Route(int id, UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring, int every,
            int people, boolean sell, long lastDay, Optional<CaravanRules.Trigger> when, String name,
            Optional<Condition> logic, List<Rule> rules) {
        public Route(int id, UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring, int every,
                int people, boolean sell, long lastDay, Optional<CaravanRules.Trigger> when, String name,
                Optional<Condition> logic) {
            this(id, to, send, bring, every, people, sell, lastDay, when, name, logic, List.of());
        }

        /** Longest name a route may have. */
        public static final int MAX_NAME = 32;

        public Route(int id, UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring, int every,
                int people, boolean sell, long lastDay) {
            this(id, to, send, bring, every, people, sell, lastDay, Optional.empty(), "", Optional.empty());
        }

        public Route(int id, UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring, int every,
                int people, boolean sell, long lastDay, Optional<CaravanRules.Trigger> when) {
            this(id, to, send, bring, every, people, sell, lastDay, when, "", Optional.empty());
        }

        public Route(int id, UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring, int every,
                int people, boolean sell, long lastDay, Optional<CaravanRules.Trigger> when, String name) {
            this(id, to, send, bring, every, people, sell, lastDay, when, name, Optional.empty());
        }

        /**
         * When it sets out by itself: its rules, or (routes saved before rules) every N days / its one trigger as
         * rules; empty: only by hand.
         */
        public Optional<Condition> rule() {
            if (logic.isPresent()) {
                return logic;
            }
            if (when.isPresent()) {
                CaravanRules.Trigger t = when.get();
                return Optional.of(new CaravanConditions.Stock(t.colony().toString(), t.item(), t.above() ? ">" : "<",
                        t.threshold()));
            }
            return every > 0 ? Optional.of(new CaravanConditions.DaysSinceSent(every)) : Optional.empty();
        }

        /** The same route with one rule: if {@code rule} then send (empty: by hand). */
        public Route withRule(Optional<Condition> rule) {
            return withRules(rule.map(c -> List.of(new Rule(Optional.of(c), List.of(new Outcomes.Send(false)),
                    List.of(), false))).orElse(List.of()));
        }

        /** The same route with these rules (the old forms dropped). */
        public Route withRules(List<Rule> r) {
            return new Route(id, to, send, bring, 0, people, sell, lastDay, Optional.empty(), name, Optional.empty(),
                    List.copyOf(r));
        }

        /**
         * Its rules; routes saved before rules lists (every N days, one trigger, one condition) as one rule: if that,
         * send.
         */
        public List<Rule> program() {
            if (!rules.isEmpty()) {
                return rules;
            }
            return rule().map(c -> List.of(new Rule(Optional.of(c), List.of(new Outcomes.Send(false)), List.of(),
                    false))).orElse(List.of());
        }

        /** Its name, or "Route n" when it has none. */
        public String title() {
            return name.isBlank() ? "Route " + id : name;
        }

        public Route named(String n) {
            String t = n == null ? "" : n.strip();
            if (t.length() > MAX_NAME) {
                t = t.substring(0, MAX_NAME);
            }
            return new Route(id, to, send, bring, every, people, sell, lastDay, when, t, logic, rules);
        }

        public static final Codec<CaravanRules.Amount> AMOUNT = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(CaravanRules.Amount::item),
                Codec.INT.optionalFieldOf("count", 0).forGetter(CaravanRules.Amount::count),
                Codec.INT.optionalFieldOf("over", -1).forGetter(CaravanRules.Amount::over),
                Codec.INT.optionalFieldOf("up_to", -1).forGetter(CaravanRules.Amount::upTo))
                .apply(i, CaravanRules.Amount::new));

        public static final Codec<CaravanRules.Trigger> TRIGGER = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("colony").forGetter(CaravanRules.Trigger::colony),
                Codec.STRING.fieldOf("item").forGetter(CaravanRules.Trigger::item),
                Codec.BOOL.optionalFieldOf("above", false).forGetter(CaravanRules.Trigger::above),
                Codec.INT.optionalFieldOf("threshold", 0).forGetter(CaravanRules.Trigger::threshold))
                .apply(i, CaravanRules.Trigger::new));

        public static final Codec<Route> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Route::id),
                UUIDUtil.CODEC.fieldOf("to").forGetter(Route::to),
                AMOUNT.listOf().optionalFieldOf("send", List.of()).forGetter(Route::send),
                AMOUNT.listOf().optionalFieldOf("bring", List.of()).forGetter(Route::bring),
                Codec.INT.optionalFieldOf("every", 0).forGetter(Route::every),
                Codec.INT.optionalFieldOf("people", 1).forGetter(Route::people),
                Codec.BOOL.optionalFieldOf("sell", false).forGetter(Route::sell),
                Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Route::lastDay),
                TRIGGER.optionalFieldOf("when").forGetter(Route::when),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Route::name),
                Conditions.CODEC.optionalFieldOf("rules").forGetter(Route::logic),
                Rule.CODEC.listOf().optionalFieldOf("program", List.of()).forGetter(Route::rules))
                .apply(i, Route::new));

        public Route sentOn(long day) {
            return new Route(id, to, send, bring, every, people, sell, day, when, name, logic, rules);
        }

        /** "rules" when it has rules, else "manual". */
        public String mode() {
            return program().isEmpty() ? "manual" : "rules";
        }
    }

    public static final Codec<CaravanRules.Amount> AMOUNT = Route.AMOUNT;

    /**
     * One rule of a route: if {@code when} holds (none: always), run {@code then}, else {@code otherwise}. With
     * {@code edge} it fires only when {@code when} turns true (or false, for {@code otherwise}), not at every check.
     */
    public record Rule(Optional<Condition> when, List<Outcome> then, List<Outcome> otherwise, boolean edge) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Conditions.CODEC.optionalFieldOf("if").forGetter(Rule::when),
                Outcome.CODEC.listOf().optionalFieldOf("then", List.of()).forGetter(Rule::then),
                Outcome.CODEC.listOf().optionalFieldOf("else", List.of()).forGetter(Rule::otherwise),
                Codec.BOOL.optionalFieldOf("edge", false).forGetter(Rule::edge))
                .apply(i, Rule::new));
    }

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
