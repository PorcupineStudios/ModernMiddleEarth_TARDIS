package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.mount.Mounts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Spawn eggs: one NPC egg whose stacks carry an {@code NpcType} (one per type file shipped in the jar, so the tab is
 * full on servers too), plus one egg per mount breed. The eggs' entity data has {@code Fresh}, so skin, gear and name
 * roll like a natural spawn. Tab "LOTR Spawn Eggs".
 */
public final class SpawnEggs {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LotrPort.MOD_ID);

    public static final DeferredItem<Item> NPC_EGG = ITEMS.register("npc_spawn_egg",
            () -> new NpcEgg(new Item.Properties()));

    private SpawnEggs() {}

    /** Call after {@link Mounts#register}, which fills the breeds. */
    public static void register(IEventBus modBus) {
        List<DeferredItem<Item>> mountEggs = Mounts.TYPE_BY_BREED.entrySet().stream()
                .map(e -> ITEMS.<Item>register(e.getKey() + "_spawn_egg",
                        () -> new DeferredSpawnEggItem(e.getValue(), 0x8a5a30, 0xe0c890, new Item.Properties()) {
                            @Override
                            public Component getName(ItemStack stack) {
                                return spawn(e.getValue().get().getDescription());
                            }
                        }))
                .toList();
        TABS.register("spawn_eggs", () -> CreativeModeTab.builder()
                .title(Component.translatableWithFallback("itemGroup.lotr_port.spawn_eggs", "LOTR Spawn Eggs"))
                .icon(() -> new ItemStack(NPC_EGG.get()))
                .displayItems((params, output) -> {
                    mountEggs.forEach(egg -> output.accept(egg.get()));
                    npcTypes().forEach(type -> output.accept(egg(type)));
                })
                .build());
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** An NPC egg for one type. */
    public static ItemStack egg(ResourceLocation type) {
        ItemStack stack = new ItemStack(NPC_EGG.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("id", LotrEntities.NPC.getId().toString());
        tag.putString("NpcType", type.toString());
        tag.putBoolean("Fresh", true);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        return stack;
    }

    /** The NPC types shipped in this mod's jar ({@code data/lotr_port/npc/*.json}), sorted. */
    static List<ResourceLocation> npcTypes() {
        Path dir = ModList.get().getModFileById(LotrPort.MOD_ID).getFile().findResource("data", LotrPort.MOD_ID, "npc");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted()
                    .map(n -> LotrPort.id(n.substring(0, n.length() - 5))).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** "Spawn <what>" without a lang entry of its own (lang files are generated). */
    static Component spawn(Component what) {
        return Component.translatableWithFallback("item.lotr_port.spawn_egg.of", "Spawn %s", what);
    }

    static String typeOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        return data == null ? "" : data.copyTag().getString("NpcType");
    }

    /** The NPC egg: named after its stack's type ("Spawn Hobbit"). */
    static final class NpcEgg extends DeferredSpawnEggItem {
        NpcEgg(Item.Properties props) {
            super(LotrEntities.NPC, 0x3a5a2a, 0xd8b060, props);
        }

        @Override
        public Component getName(ItemStack stack) {
            ResourceLocation type = ResourceLocation.tryParse(typeOf(stack));
            if (type == null || type.getPath().isEmpty()) {
                return spawn(Component.literal("NPC"));
            }
            return spawn(Component.translatable("npc." + type.getNamespace() + "." + type.getPath()));
        }
    }
}
