package com.porcupinestudios.lotrport.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

/**
 * GameTest mock players ({@code makeMockServerPlayerInLevel}) sit on an in-memory connection that never negotiated
 * mod channels, and Curios sends its slot sync to every player without asking, which NeoForge refuses with an
 * exception that fails whatever test made the player. Before Curios' listeners run, such connections get Curios'
 * channels as ad-hoc channels; the packets then go into the in-memory channel like vanilla's. Real connections are
 * never touched (only an {@link EmbeddedChannel}, which only GameTest players have).
 */
public final class MockPlayerChannels {
    private static final List<String> CURIOS = List.of("break", "client_page", "destroy", "grabbed_item",
            "open_curios", "open_vanilla", "quick_move", "server_page", "set_icons", "sync_active", "sync_curios",
            "sync_data", "sync_modifiers", "sync_render", "sync_stack", "toggle_cosmetics", "toggle_render");

    private MockPlayerChannels() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (PlayerEvent.PlayerLoggedInEvent e) -> open(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (EntityJoinLevelEvent e) -> open(e.getEntity()));
        // placeNewPlayer syncs datapacks (Curios' slot data) before the login event
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                (net.neoforged.neoforge.event.OnDatapackSyncEvent e) -> e.getRelevantPlayers().forEach(MockPlayerChannels::open));
    }

    private static void open(Object entity) {
        if (entity instanceof ServerPlayer p && p.connection != null
                && p.connection.getConnection().channel() instanceof EmbeddedChannel) {
            var adHoc = ChannelAttributes.getOrCreateAdHocChannels(p.connection.getConnection());
            CURIOS.forEach(id -> adHoc.add(ResourceLocation.fromNamespaceAndPath("curios", id)));
        }
    }
}
