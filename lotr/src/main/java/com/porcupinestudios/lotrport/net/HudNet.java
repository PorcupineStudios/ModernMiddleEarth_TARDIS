package com.porcupinestudios.lotrport.net;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * What the HUD needs from the server: the alignment bar for the faction the player watches. Sent by
 * {@code faction.AlignmentHud} when it changes; the client handler is set by client setup, so common code never
 * names the HUD.
 */
public final class HudNet {
    /** Shows the alignment bar; set by the client. Does nothing on a dedicated server. */
    public static Consumer<AlignmentBar> barHandler = bar -> { };

    private HudNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(HudNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(AlignmentBar.TYPE, AlignmentBar.CODEC, (p, ctx) -> ctx.enqueueWork(() -> barHandler.accept(p)));
    }

    /** A rank on the bar: the alignment it starts at and its name. */
    public record Rank(float alignment, Component name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Rank> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Rank::alignment,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Rank::name,
                Rank::new);
    }

    /**
     * The watched faction and the player's standing with it.
     *
     * @param faction the faction id, "" when there's nothing to show
     * @param ranks the faction's ranks, lowest first (empty for realms and rankless factions)
     */
    public record AlignmentBar(String faction, Component name, int color, float alignment, int flags,
            List<Rank> ranks) implements CustomPacketPayload {
        public static final AlignmentBar NONE = new AlignmentBar("", Component.empty(), 0, 0f, 0, List.of());
        public static final int PLEDGED = 1;
        /** The faction's reach (FactionInfluence.Reach ordinal) sits in bits 1-2. */
        public static final int REACH_SHIFT = 1;
        /** Near a faction NPC: the bar shows even outside Middle-earth. */
        public static final int NEAR_NPC = 8;

        public static int flags(boolean pledged, int reach, boolean nearNpc) {
            return (pledged ? PLEDGED : 0) | (reach & 3) << REACH_SHIFT | (nearNpc ? NEAR_NPC : 0);
        }

        public boolean pledged() {
            return (flags & PLEDGED) != 0;
        }

        /** 0 none (kills don't count), 1 near its people, 2 in its control zone. */
        public int reach() {
            return flags >> REACH_SHIFT & 3;
        }

        public boolean nearNpc() {
            return (flags & NEAR_NPC) != 0;
        }
        public static final Type<AlignmentBar> TYPE = new Type<>(LotrPort.id("alignment_bar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AlignmentBar> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AlignmentBar::faction,
                ComponentSerialization.TRUSTED_STREAM_CODEC, AlignmentBar::name,
                ByteBufCodecs.INT, AlignmentBar::color,
                ByteBufCodecs.FLOAT, AlignmentBar::alignment,
                ByteBufCodecs.VAR_INT, AlignmentBar::flags,
                Rank.CODEC.apply(ByteBufCodecs.list()), AlignmentBar::ranks,
                AlignmentBar::new);

        public boolean present() {
            return !faction.isEmpty();
        }

        @Override
        public Type<AlignmentBar> type() {
            return TYPE;
        }
    }
}
