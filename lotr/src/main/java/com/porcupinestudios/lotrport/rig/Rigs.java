package com.porcupinestudios.lotrport.rig;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The rig folder ({@code data/<ns>/rig/*.json}, {@link RigDef}): loaded on the server, reloaded with /reload, and sent
 * to every client on join and reload (a dedicated client has no data packs). Each side keeps its own copy, so a
 * single-player client and its server never share a skeleton.
 */
public final class Rigs {
    private static final CodecJsonLoader<RigDef> LOADER = new CodecJsonLoader<>("rig", RigDef.CODEC) {
        @Override
        protected void apply(Map<ResourceLocation, com.google.gson.JsonElement> files,
                net.minecraft.server.packs.resources.ResourceManager manager,
                net.minecraft.util.profiling.ProfilerFiller profiler) {
            super.apply(files, manager, profiler);
            server = index(entries());
        }
    };
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static volatile Side server = Side.EMPTY;
    private static volatile Side client = Side.EMPTY;

    /** One side's rigs: by rig id, and by the NPC types that use them. */
    private record Side(Map<ResourceLocation, RigSkeleton> byId, Map<ResourceLocation, ResourceLocation> byType) {
        static final Side EMPTY = new Side(Map.of(), Map.of());
    }

    private Rigs() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener(Rigs::sync);
        modBus.addListener(Rigs::payloads);
    }

    private static Side index(Map<ResourceLocation, RigDef> defs) {
        Map<ResourceLocation, RigSkeleton> byId = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> byType = new HashMap<>();
        defs.forEach((id, def) -> {
            try {
                byId.put(id, new RigSkeleton(def));
                for (String t : def.npcTypes()) {
                    ResourceLocation type = ResourceLocation.tryParse(t);
                    if (type != null) {
                        byType.put(type, id);
                    }
                }
            } catch (RuntimeException ex) {
                LOG.error("[rig] {} can't be built: {}", id, ex.toString());
            }
        });
        return new Side(Map.copyOf(byId), Map.copyOf(byType));
    }

    private static Side side(boolean clientSide) {
        return clientSide ? client : server;
    }

    /** The rig an NPC type uses on this side, or null. */
    @Nullable
    public static RigSkeleton forType(Level level, ResourceLocation npcType) {
        Side s = side(level.isClientSide);
        ResourceLocation id = s.byType.get(npcType);
        return id == null ? null : s.byId.get(id);
    }

    /** Whether an NPC type is drawn on a rig (server: decides which entity a spawn becomes). */
    public static boolean rigged(ResourceLocation npcType) {
        return server.byType.containsKey(npcType);
    }

    public static Map<ResourceLocation, RigSkeleton> all(boolean clientSide) {
        return side(clientSide).byId;
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }

    // ---------------------------------------------------------------- sync

    private static void sync(OnDatapackSyncEvent event) {
        Map<String, String> json = new LinkedHashMap<>();
        LOADER.entries().forEach((id, def) -> RigDef.CODEC.encodeStart(JsonOps.INSTANCE, def)
                .ifSuccess(j -> json.put(id.toString(), j.toString())));
        Sync payload = new Sync(json);
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), payload);
        } else {
            for (ServerPlayer p : event.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> receive(p)));
    }

    private static void receive(Sync sync) {
        Map<ResourceLocation, RigDef> defs = new LinkedHashMap<>();
        sync.json().forEach((id, text) -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                RigDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text))
                        .ifSuccess(d -> defs.put(rl, d))
                        .ifError(err -> LOG.error("[rig] {} from the server: {}", id, err.message()));
            }
        });
        client = index(defs);
    }

    /** Every rig as JSON text, by id. */
    public record Sync(Map<String, String> json) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(LotrPort.id("rigs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = ByteBufCodecs
                .<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(LinkedHashMap::new,
                        ByteBufCodecs.STRING_UTF8, ByteBufCodecs.stringUtf8(1 << 20))
                .map(Sync::new, Sync::json);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }
}
