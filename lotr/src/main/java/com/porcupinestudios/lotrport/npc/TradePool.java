package com.porcupinestudios.lotrport.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * What a trader deals in: {@code data/<namespace>/npc_trades/<name>.json}. A {@code buy} pool is what the trader sells
 * to players, a {@code sell} pool what it buys from them. Costs are in silver coins.
 *
 * @param offers how many entries one trader offers, picked at random per trader (0 = all)
 * @param uses how many times a trader makes one offer before it runs out
 */
public record TradePool(Kind kind, int offers, int uses, List<Entry> entries) {
    public enum Kind {
        BUY, SELL;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(s -> valueOf(s.toUpperCase()), k -> k.name()
                .toLowerCase());
    }

    public record Entry(ResourceLocation item, int count, int cost) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count),
                Codec.INT.fieldOf("cost").forGetter(Entry::cost))
                .apply(i, Entry::new));
    }

    public static final Codec<TradePool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(TradePool::kind),
            Codec.INT.optionalFieldOf("offers", 0).forGetter(TradePool::offers),
            Codec.INT.optionalFieldOf("uses", 1000).forGetter(TradePool::uses),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(TradePool::entries))
            .apply(i, TradePool::new));
}
