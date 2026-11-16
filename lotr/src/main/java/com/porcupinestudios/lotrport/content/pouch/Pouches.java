package com.porcupinestudios.lotrport.content.pouch;

import com.porcupinestudios.lotrport.LotrPort;
import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the pouch screen and rename payload, and fills pouches with pickups that don't fit (LOTR's pouches). */
public final class Pouches {
    /** {@link PouchMenu#pouchSlot} for a pouch in the off hand. */
    public static final int OFFHAND = 40;
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LotrPort.MOD_ID);
    public static final Supplier<MenuType<PouchMenu>> MENU = MENUS.register("pouch",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new PouchMenu(id, inv, buf.readVarInt())));

    private Pouches() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        modBus.addListener(Pouches::payloads);
        NeoForge.EVENT_BUS.addListener(Pouches::pickup);
    }

    /** The pouch screen's rename field (LOTRPacketRenamePouch). */
    public record Rename(String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(LotrPort.id("pouch_rename"));
        public static final StreamCodec<ByteBuf, Rename> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Rename::name, Rename::new);

        @Override
        public Type<Rename> type() {
            return TYPE;
        }
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Rename.TYPE, Rename.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof PouchMenu menu) {
                menu.rename(p.name());
            }
        }));
    }

    /** LOTR: an item the inventory can't take goes into the first pouch with room. */
    private static void pickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (player.level().isClientSide || stack.isEmpty() || stack.getItem() instanceof PouchItem
                || player.getInventory().getSlotWithRemainingSpace(stack) >= 0
                || player.getInventory().getFreeSlot() >= 0) {
            return;
        }
        for (ItemStack pouch : player.getInventory().items) {
            if (pouch.getItem() instanceof PouchItem) {
                var items = PouchItem.contents(pouch);
                int before = stack.getCount();
                for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
                    ItemStack in = items.get(i);
                    if (in.isEmpty()) {
                        items.set(i, stack.split(stack.getCount()));
                    } else if (ItemStack.isSameItemSameComponents(in, stack)) {
                        int move = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                        in.grow(move);
                        stack.shrink(move);
                    }
                }
                if (stack.getCount() != before) {
                    PouchItem.store(pouch, items);
                    player.take(entity, before - stack.getCount());
                }
                if (stack.isEmpty()) {
                    entity.discard();
                    return;
                }
            }
        }
        entity.setItem(stack);
    }
}
