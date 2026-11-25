package com.porcupinestudios.lotrport.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Effects a land has on the players in it (Aaron 2026-11-25: the enchanted forest of The Hobbit films "with the
 * drowsy effect and cursed water"), all from data: {@code data/<ns>/region_effect/<id>.json}, reloaded by
 * {@code /reload}. A file names a biome tag and its rules; each rule is checked every {@code interval} ticks for every
 * player in survival or adventure standing in a biome of the tag:
 * <ul>
 * <li>{@code when}: {@code in_biome} (anywhere in it) or {@code in_water} (in water there: the Enchanted River);</li>
 * <li>{@code chance} per check, {@code cooldown} ticks before the rule can fire again for that player, and
 * {@code unless}: an effect that stops it (no drowsiness while asleep);</li>
 * <li>{@code effects}: vanilla's effect format ({@code id, duration, amplifier, ambient, show_particles,
 * show_icon}), {@code message}: a translation key shown above the hotbar;</li>
 * <li>{@code wake}: when the effect {@code after} wears off, give {@code effects}, turn the player round at random
 * ({@code turn}) and show {@code message}: waking from the enchanted sleep not knowing which way the path lay.</li>
 * </ul>
 * {@code bottle}: a glass bottle filled from water in the tag's biomes is a potion of {@code effects} named
 * {@code name} (a translation key) and coloured {@code color}: the river's black water.
 */
public final class RegionEffects {
    public record Wake(String after, List<MobEffectInstance> effects, boolean turn, Optional<String> message) {
        public static final Codec<Wake> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("after").forGetter(Wake::after),
                MobEffectInstance.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Wake::effects),
                Codec.BOOL.optionalFieldOf("turn", false).forGetter(Wake::turn),
                Codec.STRING.optionalFieldOf("message").forGetter(Wake::message)).apply(i, Wake::new));
    }

    public record Rule(String when, int interval, float chance, int cooldown, Optional<String> unless,
            List<MobEffectInstance> effects, Optional<String> message, Optional<Wake> wake) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("when").forGetter(Rule::when),
                Codec.intRange(1, 72000).optionalFieldOf("interval", 20).forGetter(Rule::interval),
                Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(Rule::chance),
                Codec.intRange(0, 720000).optionalFieldOf("cooldown", 0).forGetter(Rule::cooldown),
                Codec.STRING.optionalFieldOf("unless").forGetter(Rule::unless),
                MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(Rule::effects),
                Codec.STRING.optionalFieldOf("message").forGetter(Rule::message),
                Wake.CODEC.optionalFieldOf("wake").forGetter(Rule::wake)).apply(i, Rule::new));
    }

    public record Bottle(String name, int color, List<MobEffectInstance> effects) {
        public static final Codec<Bottle> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Bottle::name),
                Codec.INT.fieldOf("color").forGetter(Bottle::color),
                MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(Bottle::effects)).apply(i, Bottle::new));
    }

    public record Def(String biomeTag, List<Rule> rules, Optional<Bottle> bottle) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("biome_tag").forGetter(Def::biomeTag),
                Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(Def::rules),
                Bottle.CODEC.optionalFieldOf("bottle").forGetter(Def::bottle)).apply(i, Def::new));

        TagKey<Biome> tag() {
            return TagKey.create(Registries.BIOME, ResourceLocation.parse(biomeTag));
        }
    }

    private static final CodecJsonLoader<Def> LOADER = new CodecJsonLoader<>("region_effect", Def.CODEC);
    /** When each rule last fired for each player (game time), by "file#rule". */
    private static final Map<UUID, Map<String, Long>> LAST = new ConcurrentHashMap<>();
    /** What happens when a player's effect wears off, by player and effect id. */
    private static final Map<UUID, Map<String, Wake>> WAKES = new ConcurrentHashMap<>();

    private RegionEffects() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener(RegionEffects::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RegionEffects::onExpired);
        NeoForge.EVENT_BUS.addListener(RegionEffects::onRightClickItem);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            LAST.remove(e.getEntity().getUUID());
            WAKES.remove(e.getEntity().getUUID());
        });
    }

    /** The loaded files, by id (for tests and commands). */
    public static Map<ResourceLocation, Def> defs() {
        return LOADER.entries();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()
                || LOADER.entries().isEmpty()) {
            return;
        }
        Holder<Biome> biome = null;
        for (Map.Entry<ResourceLocation, Def> file : LOADER.entries().entrySet()) {
            List<Rule> rules = file.getValue().rules();
            for (int n = 0; n < rules.size(); n++) {
                Rule rule = rules.get(n);
                if (player.tickCount % rule.interval() != 0) {
                    continue;
                }
                if (biome == null) {
                    biome = player.level().getBiome(player.blockPosition());
                }
                if (!biome.is(file.getValue().tag())) {
                    break;
                }
                apply(player, file.getKey() + "#" + n, rule);
            }
        }
    }

    /** Fires a rule for a player if its condition, chance, cooldown and {@code unless} allow. */
    public static boolean apply(ServerPlayer player, String key, Rule rule) {
        switch (rule.when()) {
            case "in_biome" -> { }
            case "in_water" -> {
                if (!player.isInWater()) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        if (rule.unless().isPresent() && has(player, rule.unless().get())) {
            return false;
        }
        long now = player.level().getGameTime();
        Map<String, Long> last = LAST.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        Long before = last.get(key);
        if (before != null && now - before < rule.cooldown()) {
            return false;
        }
        if (player.getRandom().nextFloat() >= rule.chance()) {
            return false;
        }
        last.put(key, now);
        for (MobEffectInstance e : rule.effects()) {
            player.addEffect(new MobEffectInstance(e));
        }
        rule.message().ifPresent(m -> player.displayClientMessage(Component.translatable(m), true));
        rule.wake().ifPresent(w -> WAKES.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(w.after(), w));
        return true;
    }

    private static boolean has(ServerPlayer player, String effect) {
        Optional<Holder.Reference<MobEffect>> holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceLocation.parse(effect));
        return holder.isPresent() && player.hasEffect(holder.get());
    }

    private static void onExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getEffectInstance() == null) {
            return;
        }
        Map<String, Wake> wakes = WAKES.get(player.getUUID());
        if (wakes == null) {
            return;
        }
        String id = event.getEffectInstance().getEffect().unwrapKey().map(k -> k.location().toString()).orElse("");
        Wake wake = wakes.remove(id);
        if (wake == null) {
            return;
        }
        for (MobEffectInstance e : wake.effects()) {
            player.addEffect(new MobEffectInstance(e));
        }
        if (wake.turn()) {
            player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                    player.getRandom().nextFloat() * 360f - 180f, player.getXRot());
        }
        wake.message().ifPresent(m -> player.displayClientMessage(Component.translatable(m), true));
    }

    /** A glass bottle dipped in water of a biome with a {@code bottle}: that water instead of plain water. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.GLASS_BOTTLE) || !(event.getLevel() instanceof ServerLevel level)
                || LOADER.entries().isEmpty()) {
            return;
        }
        var player = event.getEntity();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.mayInteract(player, pos)) {
            return;
        }
        Bottle bottle = bottleAt(level, pos);
        if (bottle == null) {
            return;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL,
                SoundSource.NEUTRAL, 1f, 1f);
        player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(stack, player, bottled(bottle)));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** The bottle rule for water at a position, or null. */
    static Bottle bottleAt(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        for (Def def : LOADER.entries().values()) {
            if (def.bottle().isPresent() && biome.is(def.tag())) {
                return def.bottle().get();
            }
        }
        return null;
    }

    /** A potion of a bottle rule's water. */
    public static ItemStack bottled(Bottle bottle) {
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(bottle.color()),
                bottle.effects().stream().map(MobEffectInstance::new).toList()));
        potion.set(DataComponents.CUSTOM_NAME, Component.translatable(bottle.name())
                .withStyle(s -> s.withItalic(false)));
        return potion;
    }
}
