package com.porcupinestudios.lotrport.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The creative tabs, in LOTR's order and with LOTR's contents, from the generated {@code tabs.json}. */
public final class LotrTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LotrPort.MOD_ID);

    private LotrTabs() {}

    public static void register(IEventBus modBus) {
        for (JsonObject tab : ContentData.read("tabs.json")) {
            String id = tab.get("id").getAsString();
            List<String> items = new ArrayList<>();
            for (JsonElement e : tab.getAsJsonArray("items")) {
                items.add(e.getAsString());
            }
            String icon = ContentData.str(tab, "icon", items.isEmpty() ? "" : items.get(0));
            TABS.register(id, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + LotrPort.MOD_ID + "." + id))
                    .icon(() -> stack(icon))
                    .displayItems((params, output) -> {
                        for (String item : items) {
                            DeferredItem<? extends Item> holder = LotrItems.BY_ID.get(item);
                            if (holder != null) {
                                output.accept(holder.get());
                            }
                        }
                    })
                    .build());
        }
        TABS.register(modBus);
    }

    private static ItemStack stack(String id) {
        DeferredItem<? extends Item> holder = LotrItems.BY_ID.get(id);
        return holder == null ? new ItemStack(Items.STONE) : new ItemStack(holder.get());
    }
}
