package com.porcupinestudios.lotrport.modifier;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrComponents;
import com.porcupinestudios.lotrport.progress.Achievements;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * LOTR's modifiers (LOTREnchantmentHelper), hooked in. The modifiers themselves are data enchantments
 * ({@code enchantment/<id>.json}, converter/extra.py); this is how a player comes by them, as in LOTR:
 * <ul>
 * <li>Crafted gear rolls random modifiers ({@link #onCrafted}, from a mixin on {@code Item.onCraftedBy}: crafting
 * table, faction tables, shift-click too), good and bad ({@code rolls}).</li>
 * <li>What traders sell rolls skilful modifiers when the deals are made, and costs more by each one's {@code value}
 * ({@link #rollTrade}).</li>
 * <li>Smith's Scrolls (NPCs drop them; each holds one modifier, given when it first appears in the world) apply their
 * modifier on an anvil; two scrolls of one modifier combine into its {@code upgrade} (LOTR's "Expert Knowledge").</li>
 * <li>Reforging: undamaged gear plus one of its repair material on an anvil rolls its modifiers again; banes and other
 * {@code persists_reforge} ones stay, vanilla enchantments stay.</li>
 * <li>Looting and silk touch count as vanilla's ({@code acts_as}); headhunting drops the victim's head.</li>
 * </ul>
 * Tooltips show good modifiers green and bad ones ({@code #lotr_port:modifier/negative}) red, as LOTR did.
 */
public final class Modifiers {
    private static final CodecJsonLoader<ModifierDef> DEFS = new CodecJsonLoader<>("modifier", ModifierDef.CODEC);
    private static final CodecJsonLoader<ModifierSettings> SETTINGS =
            new CodecJsonLoader<>("modifier_settings", ModifierSettings.CODEC);
    private static final CodecJsonLoader<AncientItemDef> ANCIENT =
            new CodecJsonLoader<>("ancient_item", AncientItemDef.CODEC);
    public static final TagKey<Enchantment> NEGATIVE =
            TagKey.create(Registries.ENCHANTMENT, LotrPort.id("modifier/negative"));

    private Modifiers() {}

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(Modifiers::tabs);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(DEFS);
            e.addListener(SETTINGS);
            e.addListener(ANCIENT);
        });
        NeoForge.EVENT_BUS.addListener(Modifiers::onAnvil);
        NeoForge.EVENT_BUS.addListener(Modifiers::onAnvilTake);
        NeoForge.EVENT_BUS.addListener(Modifiers::onEnchantmentLevel);
        NeoForge.EVENT_BUS.addListener(Modifiers::onDrops);
        NeoForge.EVENT_BUS.addListener(Modifiers::onJoin);
        NeoForge.EVENT_BUS.addListener(Modifiers::onTooltip);
    }

    public static ModifierSettings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), ModifierSettings.DEFAULT);
    }

    public static Map<ResourceLocation, ModifierDef> defs() {
        return DEFS.entries();
    }

    public static Optional<AncientItemDef> ancient(Item item) {
        return Optional.ofNullable(ANCIENT.entries().get(BuiltInRegistries.ITEM.getKey(item)));
    }

    // ------------------------------------------------------------------ rolling

    private static Optional<ModifierDef> def(Holder<Enchantment> h) {
        return h.unwrapKey().map(k -> DEFS.entries().get(k.location()));
    }

    /** Modifiers that could be rolled onto {@code stack} now: weighted, applicable, compatible with what it has. */
    private static List<Holder.Reference<Enchantment>> candidates(ItemStack stack, HolderLookup.Provider registries,
            boolean skilful) {
        List<Holder.Reference<Enchantment>> out = new ArrayList<>();
        var lookup = registries.lookup(Registries.ENCHANTMENT);
        if (lookup.isEmpty()) {
            return out;
        }
        var have = stack.getEnchantments().keySet();
        for (Map.Entry<ResourceLocation, ModifierDef> e : DEFS.entries().entrySet()) {
            ModifierDef d = e.getValue();
            if (d.weight() <= 0 || d.skilful() && !skilful) {
                continue;
            }
            lookup.get().get(ResourceKey.create(Registries.ENCHANTMENT, e.getKey())).ifPresent(h -> {
                if (h.value().canEnchant(stack) && !have.contains(h)
                        && EnchantmentHelper.isEnchantmentCompatible(have, h)) {
                    out.add(h);
                }
            });
        }
        return out;
    }

    /** Whether any modifier can go on {@code stack} (LOTR's canApplyAnyEnchantment). */
    public static boolean canHaveModifiers(ItemStack stack, HolderLookup.Provider registries) {
        return !stack.isEmpty() && !candidates(stack, registries, true).isEmpty();
    }

    /** Rolls random modifiers onto {@code stack} ({@code rolls} or {@code skilful_rolls}); returns how many. */
    public static int applyRandom(ItemStack stack, RandomSource random, boolean skilful,
            HolderLookup.Provider registries) {
        int added = 0;
        for (float chance : skilful ? settings().skilfulRolls() : settings().rolls()) {
            if (random.nextFloat() >= chance) {
                break;
            }
            List<Holder.Reference<Enchantment>> pool = candidates(stack, registries, skilful);
            int total = 0;
            for (var h : pool) {
                total += def(h).map(ModifierDef::weight).orElse(0);
            }
            if (total <= 0) {
                break;
            }
            int pick = random.nextInt(total);
            for (var h : pool) {
                pick -= def(h).map(ModifierDef::weight).orElse(0);
                if (pick < 0) {
                    stack.enchant(h, 1);
                    added++;
                    break;
                }
            }
        }
        return added;
    }

    /** LOTR's price factor: the product of the stack's modifiers' {@code value}. */
    public static float value(ItemStack stack) {
        float v = 1f;
        for (Holder<Enchantment> h : stack.getEnchantments().keySet()) {
            v *= def(h).map(ModifierDef::value).orElse(1f);
        }
        return v;
    }

    /** Crafted (or forged, smithed) gear: random plain modifiers, once. Called on the server from the mixin. */
    public static void onCrafted(ItemStack stack, Level level, Player player) {
        if (level.isClientSide || stack.isEmpty()) {
            return;
        }
        if (player instanceof ServerPlayer sp && ancient(stack.getItem()).isPresent()) {
            Achievements.grant(sp, "craftAncientItem");
        }
        if (!settings().crafting() || !stack.getEnchantments().isEmpty() || stack.getMaxStackSize() > 1) {
            return;
        }
        applyRandom(stack, level.random, false, level.registryAccess());
    }

    /** A trader's goods: skilful modifiers, and the price scaled by their value. Returns the new price. */
    public static int rollTrade(ItemStack goods, int cost, RandomSource random, HolderLookup.Provider registries) {
        if (!settings().trades() || goods.getMaxStackSize() > 1 || !goods.getEnchantments().isEmpty()) {
            return cost;
        }
        if (applyRandom(goods, random, true, registries) == 0) {
            return cost;
        }
        return Math.max(1, Math.round(cost * value(goods)));
    }

    // ------------------------------------------------------------------ scrolls

    /** The modifier a Smith's Scroll holds, if any. */
    public static Optional<Holder.Reference<Enchantment>> scrollModifier(ItemStack scroll,
            HolderLookup.Provider registries) {
        ResourceLocation id = scroll.get(LotrComponents.MODIFIER.get());
        if (id == null) {
            return Optional.empty();
        }
        return registries.lookup(Registries.ENCHANTMENT)
                .flatMap(l -> l.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
    }

    /** Makes {@code scroll} a scroll of {@code modifier}: its id, and its name ("Keen Smith's Scroll"). */
    public static ItemStack setScroll(ItemStack scroll, Holder<Enchantment> modifier) {
        modifier.unwrapKey().ifPresent(k -> scroll.set(LotrComponents.MODIFIER.get(), k.location()));
        scroll.set(DataComponents.ITEM_NAME, Component.translatable(scroll.getItem().getDescriptionId(),
                modifier.value().description()));
        return scroll;
    }

    /** A scroll without a modifier gets a random one that may be rolled (LOTR's common templates). */
    public static void fillScroll(ItemStack scroll, RandomSource random, HolderLookup.Provider registries) {
        if (!(scroll.getItem() instanceof SmithScrollItem) || scroll.has(LotrComponents.MODIFIER.get())) {
            return;
        }
        var lookup = registries.lookup(Registries.ENCHANTMENT);
        if (lookup.isEmpty()) {
            return;
        }
        List<Holder.Reference<Enchantment>> pool = new ArrayList<>();
        int total = 0;
        for (Map.Entry<ResourceLocation, ModifierDef> e : DEFS.entries().entrySet()) {
            if (e.getValue().weight() > 0 && !e.getValue().skilful()) {
                var h = lookup.get().get(ResourceKey.create(Registries.ENCHANTMENT, e.getKey()));
                if (h.isPresent()) {
                    pool.add(h.get());
                    total += e.getValue().weight();
                }
            }
        }
        if (total <= 0) {
            return;
        }
        int pick = random.nextInt(total);
        for (var h : pool) {
            pick -= def(h).map(ModifierDef::weight).orElse(0);
            if (pick < 0) {
                setScroll(scroll, h);
                return;
            }
        }
    }

    /** One scroll of each modifier in the Materials tab, after the blank one. */
    private static void tabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, LotrPort.id("materials")))) {
            return;
        }
        Item scroll = BuiltInRegistries.ITEM.get(LotrPort.id("s_smiths_scroll"));
        if (!(scroll instanceof SmithScrollItem)) {
            return;
        }
        event.getParameters().holders().lookup(Registries.ENCHANTMENT).ifPresent(l -> l.listElements()
                .filter(h -> h.key().location().getNamespace().equals(LotrPort.MOD_ID))
                .forEach(h -> event.accept(setScroll(new ItemStack(scroll), h))));
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item) {
            fillScroll(item.getItem(), event.getLevel().random, event.getLevel().registryAccess());
        }
    }

    // ------------------------------------------------------------------ anvil

    private static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        Player player = event.getPlayer();
        if (left.isEmpty() || right.isEmpty() || player == null) {
            return;
        }
        RegistryAccess registries = player.level().registryAccess();
        ModifierSettings s = settings();
        ItemStack out = ItemStack.EMPTY;
        int cost = 0;
        if (right.getItem() instanceof SmithScrollItem) {
            Optional<Holder.Reference<Enchantment>> mod = scrollModifier(right, registries);
            if (mod.isEmpty()) {
                return;
            }
            if (left.getItem() instanceof SmithScrollItem) {
                // two scrolls of one modifier: the next one up
                Optional<ResourceLocation> up = scrollModifier(left, registries).filter(m -> m.equals(mod.get()))
                        .flatMap(Modifiers::def).flatMap(ModifierDef::upgrade);
                var upHolder = up.flatMap(id -> registries.lookup(Registries.ENCHANTMENT)
                        .flatMap(l -> l.get(ResourceKey.create(Registries.ENCHANTMENT, id))));
                if (upHolder.isEmpty() || left.getCount() != 1) {
                    return;
                }
                out = setScroll(new ItemStack(left.getItem()), upHolder.get());
                cost = s.combineCost();
            } else {
                Holder<Enchantment> h = mod.get();
                var have = left.getEnchantments().keySet();
                if (!h.value().canEnchant(left) || have.contains(h) || !EnchantmentHelper.isEnchantmentCompatible(have, h)) {
                    return;
                }
                out = left.copy();
                out.enchant(h, 1);
                cost = s.scrollCost() + s.scrollCostPerAnvilCost() * h.value().getAnvilCost();
            }
        } else if (!left.isDamaged() && left.getItem().isValidRepairItem(left, right)
                && canHaveModifiers(left, registries)) {
            out = reforge(left, RandomSource.create(seed(player, left, right)), registries);
            cost = s.reforgeCost();
        }
        if (out.isEmpty()) {
            return;
        }
        String name = event.getName();
        if (name != null && !name.isBlank() && !name.equals(left.getHoverName().getString())) {
            out.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            cost++;
        }
        event.setOutput(out);
        event.setCost(cost);
        event.setMaterialCost(1);
    }

    /** The same each time this player puts the same item and material in, so taking it out and back doesn't reroll. */
    private static long seed(Player player, ItemStack left, ItemStack right) {
        return player.getUUID().getLeastSignificantBits() * 31 + ItemStack.hashItemAndComponents(left) * 17L
                + BuiltInRegistries.ITEM.getKey(right.getItem()).hashCode();
    }

    /** Gear with its modifiers rolled again: those that persist a reforge and vanilla enchantments stay. */
    public static ItemStack reforge(ItemStack gear, RandomSource random, HolderLookup.Provider registries) {
        ItemStack out = gear.copy();
        EnchantmentHelper.updateEnchantments(out, m -> m.removeIf(h -> def(h).map(d -> !d.persistsReforge())
                .orElse(false)));
        applyRandom(out, random, false, registries);
        return out;
    }

    private static void onAnvilTake(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (event.getLeft().getItem() instanceof SmithScrollItem && event.getRight().getItem() instanceof SmithScrollItem) {
            Achievements.grant(sp, "combineSmithScrolls");
        } else if (!(event.getRight().getItem() instanceof SmithScrollItem) && !event.getLeft().isDamaged()
                && event.getLeft().getItem().isValidRepairItem(event.getLeft(), event.getRight())) {
            Achievements.grant(sp, "reforge");
        }
    }

    // ------------------------------------------------------------------ effects

    /**
     * LOTR's looting and silk touch count as vanilla's, whose ids vanilla's loot tables check. The stack's own
     * enchantments are read: asked for one enchantment, the event's list holds only that one.
     */
    private static void onEnchantmentLevel(GetEnchantmentLevelEvent event) {
        ItemEnchantments.Mutable enchants = event.getEnchantments();
        Registry<Enchantment> registry = null;
        for (Holder<Enchantment> h : event.getStack().getEnchantments().keySet()) {
            Optional<ModifierDef> d = def(h);
            if (d.isEmpty() || d.get().actsAs().isEmpty()) {
                continue;
            }
            if (registry == null) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return;
                }
                registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            }
            for (Map.Entry<ResourceLocation, Integer> e : d.get().actsAs().entrySet()) {
                var as = registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, e.getKey()));
                if (as.isPresent()) {
                    enchants.upgrade(as.get(), e.getValue());
                }
            }
        }
    }

    /** Headhunting: the victim's head (players their own; vanilla mobs their skulls, from {@code heads}). */
    private static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity killer)) {
            return;
        }
        ItemStack weapon = killer.getMainHandItem();
        boolean headhunting = weapon.getEnchantments().keySet().stream()
                .anyMatch(h -> h.unwrapKey().map(k -> k.location().equals(LotrPort.id("headhunting"))).orElse(false));
        if (!headhunting || victim.getRandom().nextFloat() >= settings().headChance()) {
            return;
        }
        ItemStack head;
        if (victim instanceof Player p) {
            head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(p.getGameProfile()));
        } else {
            ResourceLocation item = settings().heads().get(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
            if (item == null) {
                return;
            }
            head = new ItemStack(BuiltInRegistries.ITEM.get(item));
        }
        if (!head.isEmpty()) {
            event.getDrops().add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), head));
        }
    }

    /** Good modifiers green, bad ones red (LOTR's tooltip colours). Fires on the client only. */
    private static void onTooltip(ItemTooltipEvent event) {
        ItemEnchantments enchants = event.getItemStack().getEnchantments();
        if (enchants.isEmpty()) {
            return;
        }
        List<Component> lines = event.getToolTip();
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> h = entry.getKey();
            if (!h.unwrapKey().map(k -> k.location().getNamespace().equals(LotrPort.MOD_ID)).orElse(false)) {
                continue;
            }
            Component name = Enchantment.getFullname(h, entry.getIntValue());
            int i = lines.indexOf(name);
            if (i >= 0) {
                lines.set(i, name.copy().withStyle(h.is(NEGATIVE) ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }
    }
}
