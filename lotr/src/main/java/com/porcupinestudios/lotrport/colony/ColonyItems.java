package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Pledges;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The colony items and their creative tab: the Charter founds a colony where it is used. */
public final class ColonyItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LotrPort.MOD_ID);

    /** Use on the block that will be the Hall. Rename it on an anvil to name the colony. */
    public static final DeferredItem<Item> CHARTER = ITEMS.register("colony_charter",
            () -> new Charter(new Item.Properties().stacksTo(1)));

    static {
        TABS.register("colonies", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + LotrPort.MOD_ID + ".colonies"))
                .icon(() -> new ItemStack(CHARTER.get()))
                .displayItems((params, output) -> output.accept(CHARTER.get()))
                .build());
    }

    private ColonyItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** The colony's faction is the founder's pledge, as in RingRealms. */
    static final class Charter extends Item {
        Charter(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            if (ctx.getLevel().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) {
                return InteractionResult.PASS;
            }
            Optional<ResourceLocation> pledge = Pledges.pledge(player);
            if (pledge.isEmpty()) {
                player.sendSystemMessage(Component.translatableWithFallback("colony.lotr_port.pledge_first",
                        "Pledge yourself to a faction before founding a colony"));
                return InteractionResult.FAIL;
            }
            ItemStack stack = ctx.getItemInHand();
            String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString()
                    : player.getName().getString() + "'s colony";
            BlockPos hall = ctx.getClickedPos().relative(ctx.getClickedFace());
            Colonies.Founding f = Colonies.get(player.server).found(player, name, hall, pledge.get());
            if (f.colony().isEmpty()) {
                player.sendSystemMessage(f.problem());
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }
    }
}
