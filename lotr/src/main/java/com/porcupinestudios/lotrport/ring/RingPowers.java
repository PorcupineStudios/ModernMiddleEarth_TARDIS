package com.porcupinestudios.lotrport.ring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Rings of power: a ring worn in a Curios {@code ring} slot whose item a {@code data/<ns>/ring_power/<id>.json} names
 * hides its wearer. The wearer is invisible without the potion's swirl (no effect is applied, only the entity's
 * invisible flag), mobs can't see them ({@code mob_visibility}, 0 = not at all) and lose them as a target, except
 * entities listed in {@code sees_through} (entity type ids, {@code #entity tags}, or LOTR NPC type ids: wights and
 * wraiths see into the wraith-world). Clients also stop drawing the wearer's armour, held items and name
 * ({@code client/RingSight}, which also runs the wearer's own Ring look). Reloads with {@code /reload}.
 */
public final class RingPowers {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LotrPort.MOD_ID);

    public static final DeferredItem<Item> ONE_RING = ITEMS.register("one_ring",
            () -> new RingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Set while a ring power, not a potion, keeps the player invisible (not saved: re-applied on the next tick). */
    private static final Supplier<AttachmentType<Boolean>> HIDDEN_BY_RING = ATTACHMENTS.register("hidden_by_ring",
            () -> AttachmentType.builder(() -> false).build());

    /** One ring power. */
    public record Power(List<ResourceLocation> items, boolean invisible, double mobVisibility, List<String> seesThrough,
            boolean loseTargets, int loseTargetsRange) {
        public static final Codec<Power> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(Power::items),
                Codec.BOOL.optionalFieldOf("invisible", true).forGetter(Power::invisible),
                Codec.doubleRange(0, 1).optionalFieldOf("mob_visibility", 0.0).forGetter(Power::mobVisibility),
                Codec.STRING.listOf().optionalFieldOf("sees_through", List.of()).forGetter(Power::seesThrough),
                Codec.BOOL.optionalFieldOf("lose_targets", true).forGetter(Power::loseTargets),
                Codec.intRange(0, 256).optionalFieldOf("lose_targets_range", 32).forGetter(Power::loseTargetsRange))
                .apply(i, Power::new));

        /** Whether {@code looker} sees the wearer anyway. */
        public boolean seenBy(Entity looker) {
            if (looker == null) {
                return false;
            }
            for (String m : seesThrough) {
                if (m.startsWith("#")) {
                    if (looker.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(m.substring(1))))) {
                        return true;
                    }
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse(m);
                if (BuiltInRegistries.ENTITY_TYPE.getKey(looker.getType()).equals(id)
                        || looker instanceof LotrNpc npc && id.equals(npc.typeId())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final CodecJsonLoader<Power> LOADER = new CodecJsonLoader<>("ring_power", Power.CODEC);
    private static Map<ResourceLocation, Power> indexedFrom;
    private static Map<Item, Power> byItem = Map.of();

    private RingPowers() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RingPowers::tab);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener(RingPowers::tick);
        NeoForge.EVENT_BUS.addListener(RingPowers::visibility);
    }

    private static void tab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, LotrPort.id("misc")))) {
            event.accept(ONE_RING.get());
        }
    }

    public static Map<ResourceLocation, Power> powers() {
        return LOADER.entries();
    }

    /** The power of a ring item, or null when it has none. */
    public static Power powerOf(Item item) {
        Map<ResourceLocation, Power> all = LOADER.entries();
        if (all != indexedFrom) {
            Map<Item, Power> m = new HashMap<>();
            all.values().forEach(p -> p.items().forEach(id -> BuiltInRegistries.ITEM.getOptional(id)
                    .ifPresent(item1 -> m.put(item1, p))));
            byItem = m;
            indexedFrom = all;
        }
        return byItem.get(item);
    }

    /** The ring power the entity wears in a Curios slot, if any. */
    public static Optional<Power> worn(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(inv -> inv.findFirstCurio(s -> powerOf(s.getItem()) != null))
                .map(r -> powerOf(r.stack().getItem()));
    }

    /** The ring stack worn, if any (any item in a ring-tagged slot); used by the client to pick a look. */
    public static ItemStack wornStack(LivingEntity entity, java.util.function.Predicate<ItemStack> which) {
        return CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.findFirstCurio(which))
                .map(r -> r.stack()).orElse(ItemStack.EMPTY);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            update(event.getEntity());
        }
    }

    /** One server tick of a player's ring: hide them while they wear one, show them again once it is off. */
    public static void update(Player player) {
        Power power = worn(player).orElse(null);
        boolean hide = power != null && power.invisible() && !player.isSpectator();
        if (hide) {
            player.setInvisible(true);
            player.setData(HIDDEN_BY_RING, true);
            if (power.loseTargets() && player.level().getGameTime() % 10 == 0) {
                double r = power.loseTargetsRange();
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r),
                        m -> m.getTarget() == player && !power.seenBy(m))) {
                    mob.setTarget(null);
                }
            }
        } else if (player.getData(HIDDEN_BY_RING)) {
            player.setData(HIDDEN_BY_RING, false);
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        }
    }

    private static void visibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        worn(player).filter(p -> p.invisible() && !p.seenBy(event.getLookingEntity()))
                .ifPresent(p -> event.modifyVisibility(p.mobVisibility()));
    }
}
