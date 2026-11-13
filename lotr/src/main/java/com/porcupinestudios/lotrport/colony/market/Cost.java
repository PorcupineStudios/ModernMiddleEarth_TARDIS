package com.porcupinestudios.lotrport.colony.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * What a colony pays for something (an upgrade): points of any research tiers, goods from its stores, coins from its
 * treasury, all at once, and what it must meet first. Every part is optional; checked whole, then paid whole.
 *
 * @param research points per tier
 * @param items goods from the stores
 * @param coins from the treasury
 * @param requires a condition ("home" names the colony)
 * @param requiresText that, in words
 */
public record Cost(Map<ResourceLocation, Double> research, Map<ResourceLocation, Integer> items, long coins,
        Optional<Condition> requires, String requiresText) {
    public static final Cost FREE = new Cost(Map.of(), Map.of(), 0, Optional.empty(), "");
    public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("research", Map.of())
                    .forGetter(Cost::research),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("items", Map.of())
                    .forGetter(Cost::items),
            Codec.LONG.optionalFieldOf("coins", 0L).forGetter(Cost::coins),
            Conditions.CODEC.optionalFieldOf("requires").forGetter(Cost::requires),
            Codec.STRING.optionalFieldOf("requires_text", "").forGetter(Cost::requiresText))
            .apply(i, Cost::new));

    static Colony fresh(MinecraftServer server, Colony c) {
        return Colonies.get(server).get(c.id()).orElse(c);
    }

    /** What is missing (empty: it can be paid). */
    public Optional<Component> missing(MinecraftServer server, Colony c) {
        Colony now = fresh(server, c);
        if (requires.isPresent() && !requires.get().test(CaravanConditions.colonyContext(server, now))) {
            return Optional.of(Component.literal("Needs " + (requiresText.isEmpty() ? "the colony to be ready"
                    : requiresText)));
        }
        List<String> short_ = new ArrayList<>();
        research.forEach((t, n) -> {
            double have = Research.points(server, now, t);
            if (have + 1e-9 < n) {
                short_.add((int) Math.ceil(n - have) + " more " + Research.name(t) + " points");
            }
        });
        if (!items.isEmpty()) {
            Stores stores = new Stores(server, server.getLevel(now.dimension()), now);
            items.forEach((it, n) -> {
                int have = stores.count(it, null);
                if (have < n) {
                    short_.add((n - have) + " more " + Market.name(it));
                }
            });
        }
        long gold = now.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
        if (gold < coins) {
            short_.add((coins - gold) + " more coins");
        }
        return short_.isEmpty() ? Optional.empty() : Optional.of(Component.literal("Needs " + String.join(", ",
                short_)));
    }

    /** Pays it all, or nothing (false with the reason). */
    public Optional<Component> pay(MinecraftServer server, Colony c) {
        Optional<Component> why = missing(server, c);
        if (why.isPresent()) {
            return why;
        }
        research.forEach((t, n) -> Research.spend(server, c, t, n));
        if (!items.isEmpty()) {
            Colony now = fresh(server, c);
            Stores stores = new Stores(server, server.getLevel(now.dimension()), now);
            items.forEach((it, n) -> stores.take(it, n, null));
            stores.save();
        }
        if (coins > 0) {
            Colony now = fresh(server, c);
            long gold = now.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
            Colonies.get(server).set(now, CoreComponents.TREASURY, new CoreComponents.Treasury(gold - coins));
        }
        return Optional.empty();
    }

    /** "20 Basic research points, 10 Scholarly research points, 16 Iron Ingot, 200 coins". */
    public String text() {
        List<String> out = new ArrayList<>();
        research.forEach((t, n) -> out.add((int) Math.ceil(n) + " " + Research.name(t) + " points"));
        items.forEach((it, n) -> out.add(n + " " + Market.name(it)));
        if (coins > 0) {
            out.add(coins + " coins");
        }
        if (!requiresText.isEmpty()) {
            out.add("needs " + requiresText);
        }
        return out.isEmpty() ? "free" : String.join(", ", out);
    }
}
