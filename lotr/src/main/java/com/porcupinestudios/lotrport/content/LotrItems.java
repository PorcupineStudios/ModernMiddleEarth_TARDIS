package com.porcupinestudios.lotrport.content;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.quest.RedBookItem;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every converted LOTR item, plus an item for each converted block that had one, from the generated data. */
public final class LotrItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, LotrPort.MOD_ID);
    /** Registered items by id, block items included, in file order. */
    public static final Map<String, DeferredItem<? extends Item>> BY_ID = new LinkedHashMap<>();
    private static final Map<String, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIALS = new LinkedHashMap<>();

    private LotrItems() {}

    public static void register(IEventBus modBus) {
        for (Map.Entry<String, JsonObject> e : LotrBlocks.ENTRIES.entrySet()) {
            String item = ContentData.str(e.getValue(), "item", "block");
            if (item.equals("none")) {
                continue;
            }
            Supplier<? extends Block> block = LotrBlocks.BY_ID.get(e.getKey());
            BY_ID.put(e.getKey(), ITEMS.register(e.getKey(), () -> item.equals("double_high")
                    ? new DoubleHighBlockItem(block.get(), new Item.Properties())
                    : new BlockItem(block.get(), new Item.Properties())));
        }
        List<JsonObject> items = ContentData.read("items.json");
        for (JsonObject e : items) {
            if (e.has("armor")) {
                material(e.getAsJsonObject("armor"), items);
            }
        }
        for (JsonObject e : items) {
            String id = e.get("id").getAsString();
            BY_ID.put(id, ITEMS.register(id, () -> item(e)));
        }
        ITEMS.register(modBus);
        ARMOR_MATERIALS.register(modBus);
    }

    private static Item item(JsonObject e) {
        Item.Properties p = new Item.Properties();
        int durability = ContentData.integer(e, "durability", 0);
        if (durability > 0) {
            p.durability(durability);
        } else {
            p.stacksTo(Math.max(1, Math.min(99, ContentData.integer(e, "stack", 64))));
        }
        if (e.has("food")) {
            JsonObject f = e.getAsJsonObject("food");
            p.food(new FoodProperties.Builder().nutrition(ContentData.integer(f, "nutrition", 1))
                    .saturationModifier(ContentData.num(f, "saturation", 0.3f)).build());
        }
        String kind = ContentData.str(e, "kind", "item");
        ConvertedTier tier = new ConvertedTier(Math.max(1, durability), ContentData.num(e, "speed", 4f),
                ContentData.integer(e, "level", 0), ContentData.integer(e, "enchant", 10));
        float damage = ContentData.num(e, "damage", 1f);
        return switch (kind) {
            case "sword" -> e.has("weapon")
                    ? new LotrWeaponItem(tier, p, damage, e.getAsJsonObject("weapon"), poison(e))
                    : new SwordItem(tier, p.attributes(SwordItem.createAttributes(tier, damage, -2.4f)));
            case "bow" -> {
                JsonObject r = ranged(e);
                yield new LotrBowItem(p, ContentData.integer(r, "draw_time", 20), ContentData.num(r, "damage_factor", 1f));
            }
            case "crossbow" -> new LotrCrossbowItem(p, ContentData.num(ranged(e), "damage_factor", 1f));
            case "bolt", "arrow" -> new ArrowItem(poison(e) == null ? p
                    : p.component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(),
                            List.of(new MobEffectInstance(MobEffects.POISON,
                                    ContentData.integer(poison(e), "arrow_ticks", 140))))));
            case "pickaxe" -> new PickaxeItem(tier, p.attributes(PickaxeItem.createAttributes(tier, damage, -2.8f)));
            case "axe" -> new AxeItem(tier, p.attributes(AxeItem.createAttributes(tier, damage, -3.0f)));
            case "shovel" -> new ShovelItem(tier, p.attributes(ShovelItem.createAttributes(tier, damage, -3.0f)));
            case "hoe" -> new HoeItem(tier, p.attributes(HoeItem.createAttributes(tier, damage, -1.0f)));
            case "armor" -> {
                if (!e.has("armor")) {
                    yield new Item(p);
                }
                JsonObject a = e.getAsJsonObject("armor");
                yield new ArmorItem(MATERIALS.get(key(a)), slot(a), p);
            }
            case "red_book" -> new RedBookItem(p);
            case "drink" -> new LotrDrinkItem(p, e.getAsJsonObject("drink"));
            case "horn", "pipe", "vessel" -> new LotrUseItem(kind.equals("vessel") ? p : p.stacksTo(1), kind,
                    ContentData.str(e, "fills", null));
            default -> new Item(p);
        };
    }

    private static JsonObject ranged(JsonObject e) {
        return e.has("ranged") ? e.getAsJsonObject("ranged") : new JsonObject();
    }

    /** LOTR's poison settings for a poisoned dagger, arrow or bolt, or null. */
    private static JsonObject poison(JsonObject e) {
        return e.has("poison") && e.get("poison").isJsonObject() ? e.getAsJsonObject("poison") : null;
    }

    private static ArmorItem.Type slot(JsonObject armor) {
        return switch (ContentData.str(armor, "slot", "chest")) {
            case "head" -> ArmorItem.Type.HELMET;
            case "legs" -> ArmorItem.Type.LEGGINGS;
            case "feet" -> ArmorItem.Type.BOOTS;
            default -> ArmorItem.Type.CHESTPLATE;
        };
    }

    /**
     * The armor material for a piece: one per old material name, or per texture where pieces of one material look
     * different (LOTR's gambesons and hats have a texture per item).
     */
    private static String key(JsonObject armor) {
        return ContentData.str(armor, "texture", ContentData.str(armor, "material", ""));
    }

    /** One armor material per {@link #key}, with the defense of each piece of its old material. */
    private static void material(JsonObject armor, List<JsonObject> items) {
        String name = ContentData.str(armor, "material", "");
        String key = key(armor);
        if (MATERIALS.containsKey(key)) {
            return;
        }
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        int enchant = 10;
        for (JsonObject e : items) {
            if (e.has("armor") && name.equals(ContentData.str(e.getAsJsonObject("armor"), "material", ""))) {
                JsonObject a = e.getAsJsonObject("armor");
                defense.put(slot(a), ContentData.integer(a, "defense", 1));
                enchant = ContentData.integer(e, "enchant", enchant);
            }
        }
        for (ArmorItem.Type t : ArmorItem.Type.values()) {
            defense.putIfAbsent(t, 0);
        }
        int enchantValue = enchant;
        String texture = ContentData.str(armor, "texture", name);
        // 1.7.10 had no toughness: LOTR armor keeps 0 unless the data gives some (see lotr/docs/missing-features.md)
        float toughness = ContentData.num(armor, "toughness", 0f);
        float knockbackResistance = ContentData.num(armor, "knockback_resistance", 0f);
        MATERIALS.put(key, ARMOR_MATERIALS.register(key, () -> new ArmorMaterial(defense, enchantValue,
                SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(LotrPort.id(texture))), toughness, knockbackResistance)));
    }

    static Holder<ArmorMaterial> material(String name) {
        return MATERIALS.get(name);
    }
}
