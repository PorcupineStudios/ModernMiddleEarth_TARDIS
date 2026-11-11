package com.porcupinestudios.lotrport.ui;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** The view providers by id, and opening a view for a player. */
public final class Views {
    private static final Map<ResourceLocation, ViewProvider> PROVIDERS = new ConcurrentHashMap<>();

    private Views() {}

    /** Registers a provider once, at mod setup. */
    public static void register(ResourceLocation id, ViewProvider provider) {
        if (PROVIDERS.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("UI view registered twice: " + id);
        }
    }

    public static Optional<ViewProvider> provider(ResourceLocation id) {
        return Optional.ofNullable(PROVIDERS.get(id));
    }

    /**
     * Builds the view and sends it. {@code open} opens the screen; otherwise only a screen already showing this view
     * (same id and context) takes it, so a refresh never pops a screen up.
     */
    public static void open(ServerPlayer player, View.Target target, boolean open) {
        ViewProvider provider = PROVIDERS.get(target.view());
        if (provider == null) {
            LotrPort.LOG.warn("[ui] {} asked for unknown view {}", player.getGameProfile().getName(), target.view());
            return;
        }
        Optional<View> view;
        try {
            view = provider.build(player, target.context(), target.tab());
        } catch (RuntimeException e) {
            // a bad context from a client, or a provider bug: never let it kick the player
            LotrPort.LOG.error("[ui] building {} for {}", target, player.getGameProfile().getName(), e);
            view = Optional.empty();
        }
        if (view.isEmpty()) {
            if (open) {
                player.displayClientMessage(provider.denied(), true);
            }
            PacketDistributor.sendToPlayer(player, new UiNet.Close(target.view(), target.context()));
            return;
        }
        PacketDistributor.sendToPlayer(player, new UiNet.Show(view.get(), open));
    }

    static void act(ServerPlayer player, UiNet.Act act) {
        ViewProvider provider = PROVIDERS.get(act.target().view());
        if (provider == null) {
            return;
        }
        View.Target target = act.target();
        ViewProvider.Result result;
        try {
            // build first: someone who may no longer see the view may not act in it either
            if (provider.build(player, target.context(), target.tab()).isEmpty()) {
                open(player, target, false);
                return;
            }
            result = provider.act(player, target.context(), target.tab(), act.action(), act.row(), act.value());
        } catch (RuntimeException e) {
            LotrPort.LOG.error("[ui] action {} in {} for {}", act.action(), target, player.getGameProfile().getName(),
                    e);
            return;
        }
        result.message().ifPresent(m -> player.displayClientMessage(m, false));
        open(player, result.next().orElse(target), result.next().isPresent());
    }
}
