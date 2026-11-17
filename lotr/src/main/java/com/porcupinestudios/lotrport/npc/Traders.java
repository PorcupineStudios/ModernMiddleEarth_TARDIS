package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * LOTR's trader screens: right-clicking a trader shows its name with Talk, Trade and Exchange Coins
 * (LOTRGuiTradeInteract); Trade opens {@link TraderMenu}, Exchange Coins {@link CoinExchangeMenu}.
 */
public final class Traders {
    public static final int TALK = 0;
    public static final int TRADE = 1;
    public static final int EXCHANGE = 2;

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LotrPort.MOD_ID);
    public static final Supplier<MenuType<TraderMenu>> TRADE_MENU = MENUS.register("trade",
            () -> IMenuTypeExtension.create(TraderMenu::new));
    public static final Supplier<MenuType<CoinExchangeMenu>> EXCHANGE_MENU = MENUS.register("coin_exchange",
            () -> IMenuTypeExtension.create(CoinExchangeMenu::new));

    /** Shows the Talk / Trade / Exchange Coins screen; set by the client. */
    public static Consumer<Interact> interactHandler = p -> { };

    private Traders() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        modBus.addListener(Traders::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(Interact.TYPE, Interact.CODEC, (p, ctx) -> ctx.enqueueWork(() -> interactHandler.accept(p)));
        r.playToServer(Choose.TYPE, Choose.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.level().getEntity(p.npc()) instanceof LotrNpc npc
                    && inReach(sp, npc) && npc.willDealWith(sp)) {
                choose(sp, npc, p.action());
            }
        }));
    }

    /** Opens the first screen for a trader. */
    public static void interact(ServerPlayer player, LotrNpc npc) {
        PacketDistributor.sendToPlayer(player, new Interact(npc.getId(), npc.tradeTitle()));
    }

    static void choose(ServerPlayer player, LotrNpc npc, int action) {
        switch (action) {
            case TALK -> npc.speak(player);
            case TRADE -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TraderMenu(id, inv, npc),
                    npc.getDisplayName()), buf -> TraderMenu.write(buf, npc));
            case EXCHANGE -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CoinExchangeMenu(id, inv, npc),
                    Component.translatableWithFallback("container.lotr_port.coin_exchange", "Coin Exchange")),
                    buf -> buf.writeVarInt(npc.getId()));
            default -> { }
        }
    }

    public static boolean inReach(Player player, LotrNpc npc) {
        return npc.isAlive() && player.distanceTo(npc) <= LotrNpc.DEAL_DISTANCE;
    }

    /** The trader's name and title, for the interact screen. */
    public record Interact(int npc, Component title) implements CustomPacketPayload {
        public static final Type<Interact> TYPE = new Type<>(LotrPort.id("trader_interact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Interact> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Interact::npc,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Interact::title,
                Interact::new);

        @Override
        public Type<Interact> type() {
            return TYPE;
        }
    }

    /** A button on the interact screen. */
    public record Choose(int npc, int action) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(LotrPort.id("trader_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Choose::npc,
                ByteBufCodecs.VAR_INT, Choose::action,
                Choose::new);

        @Override
        public Type<Choose> type() {
            return TYPE;
        }
    }
}
