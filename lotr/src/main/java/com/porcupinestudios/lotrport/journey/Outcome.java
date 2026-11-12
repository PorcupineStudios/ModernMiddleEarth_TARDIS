package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.colony.Colony;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * What a caravan rule does when it fires: send the caravan, add goods to it, message the owner, stop the rules after
 * it... Written {@code {"type": "<id>", ...}} like conditions; other systems add their own with {@link #register}
 * (and a screen block with {@code RuleEditors.registerOutcome}).
 */
public interface Outcome {
    Type<?> type();

    void run(Run run);

    /** A kind of outcome: how its fields are read. */
    record Type<T extends Outcome>(ResourceLocation id, MapCodec<T> codec) {}

    Map<ResourceLocation, Type<?>> TYPES = new LinkedHashMap<>();

    static <T extends Outcome> Type<T> register(ResourceLocation id, MapCodec<T> codec) {
        Type<T> t = new Type<>(id, codec);
        TYPES.put(id, t);
        return t;
    }

    Codec<Type<?>> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(id -> {
        Type<?> t = TYPES.get(id);
        return t == null ? DataResult.error(() -> "unknown outcome " + id) : DataResult.success(t);
    }, Type::id);

    Codec<Outcome> CODEC = Codec.lazyInitialized(() -> TYPE_CODEC.dispatch(Outcome::type, Type::codec));

    /**
     * One firing of rules for a route: what the outcomes so far asked for. The caravan goes once the rules are done,
     * if any outcome asked for it, with the route's goods plus the extra lines.
     */
    final class Run {
        public final MinecraftServer server;
        public final Colony home;
        public final CaravanBook.Route route;
        public final List<CaravanRules.Amount> take = new ArrayList<>();
        public final List<CaravanRules.Amount> bring = new ArrayList<>();
        public final List<Component> messages = new ArrayList<>();
        public boolean send;
        /** Only the extra lines, not the route's own goods. */
        public boolean extrasOnly;
        public boolean stop;
        public Optional<Integer> people = Optional.empty();

        public Run(MinecraftServer server, Colony home, CaravanBook.Route route) {
            this.server = server;
            this.home = home;
            this.route = route;
        }
    }
}
