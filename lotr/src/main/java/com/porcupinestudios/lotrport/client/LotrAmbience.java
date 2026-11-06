package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonArray;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.lotrport.LotrPort;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * LOTR's ambient sounds (LOTRAmbience), with its numbers from {@code assets/lotr_port/ambience.json}
 * ({@code converter/sounds.py}):
 * <ul>
 * <li>wight moans now and then in the haunted biomes (Barrow-downs, Dol Guldur, Dead Marshes, corrupted Mirkwood, the
 * Old Forest), 1 in {@code chance} per tick, at least 15 s apart;</li>
 * <li>wind high up in Middle-earth (100-180, lower in rain, lower still in a sandstorm), up to 4 at once;</li>
 * <li>sea waves from open water near the shore in ocean, beach and coast biomes, up to 3 at once;</li>
 * <li>Middle-earth's own rain and thunder in place of vanilla's.</li>
 * </ul>
 * "In Middle-earth" means the player stands in a lotr_port biome, which covers both the Middle-earth world type and
 * the dimension.
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID, value = Dist.CLIENT)
public final class LotrAmbience {
    private static final int WIGHT_GAP = 300;
    private static JsonObject config;
    private static final List<SoundInstance> WIND = new ArrayList<>();
    private static final List<SoundInstance> SEA = new ArrayList<>();
    private static int ticksSinceWight = WIGHT_GAP;

    private LotrAmbience() {}

    private static JsonObject config() {
        if (config == null) {
            config = new JsonObject();
            Minecraft.getInstance().getResourceManager().getResource(LotrPort.id("ambience.json")).ifPresent(r -> {
                try (Reader in = r.openAsReader()) {
                    config = JsonParser.parseReader(in).getAsJsonObject();
                } catch (Exception e) {
                    LogUtils.getLogger().warn("Could not read ambience.json", e);
                }
            });
        }
        return config;
    }

    private static ResourceLocation biome(ClientLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(k -> k.location()).orElse(null);
    }

    static boolean inMiddleEarth(ClientLevel level, BlockPos pos) {
        ResourceLocation b = biome(level, pos);
        return b != null && b.getNamespace().equals(LotrPort.MOD_ID);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (!inMiddleEarth(level, pos)) {
            return;
        }
        JsonObject c = config();
        RandomSource rand = level.getRandom();
        ResourceLocation here = biome(level, pos);
        wight(c, level, player, rand, here);
        WIND.removeIf(s -> !mc.getSoundManager().isActive(s));
        SEA.removeIf(s -> !mc.getSoundManager().isActive(s));
        wind(c, mc, level, player, rand, here);
        sea(c, mc, level, player, rand, here);
    }

    private static void wight(JsonObject c, ClientLevel level, LocalPlayer player, RandomSource rand,
            ResourceLocation here) {
        ticksSinceWight++;
        if (!c.has("wight") || ticksSinceWight < WIGHT_GAP) {
            return;
        }
        JsonObject w = c.getAsJsonObject("wight");
        JsonObject b = w.getAsJsonObject("biomes");
        if (b == null || !b.has(here.toString())) {
            return;
        }
        JsonObject spec = b.getAsJsonObject(here.toString());
        if (rand.nextInt(Math.max(1, spec.get("chance").getAsInt())) == 0) {
            float pitch = spec.get("pitch").getAsFloat() * (0.8f + rand.nextFloat() * 0.4f);
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.parse(w.get("sound").getAsString())),
                    SoundSource.AMBIENT, 1f, pitch, false);
            ticksSinceWight = 0;
        }
    }

    private static void wind(JsonObject c, Minecraft mc, ClientLevel level, LocalPlayer player, RandomSource rand,
            ResourceLocation here) {
        JsonObject w = c.getAsJsonObject("wind");
        if (w == null || WIND.size() >= w.get("max").getAsInt() || rand.nextInt(w.get("chance").getAsInt()) != 0) {
            return;
        }
        JsonArray band = w.getAsJsonArray("clear");
        if (level.isRaining()) {
            band = set(w.getAsJsonArray("sandstorm_biomes")).contains(here.toString())
                    ? w.getAsJsonArray("sandstorm") : w.getAsJsonArray("rain");
        }
        int min = band.get(0).getAsInt();
        int max = band.get(1).getAsInt();
        int range = w.get("range").getAsInt();
        for (int i = 0; i < 2; i++) {
            BlockPos at = player.blockPosition().offset(Mth.nextInt(rand, -range, range),
                    Mth.nextInt(rand, -range, range), Mth.nextInt(rand, -range, range));
            if (at.getY() < min || !level.canSeeSky(at)) {
                continue;
            }
            float strength = Mth.clamp((at.getY() - min) / (float) (max - min), 0f, 1f);
            if (strength < rand.nextFloat()) {
                continue;
            }
            SoundInstance s = sound(w.get("sound").getAsString(), at, Math.max(0.25f, strength),
                    0.8f + rand.nextFloat() * 0.4f, rand);
            mc.getSoundManager().play(s);
            WIND.add(s);
            return;
        }
    }

    private static void sea(JsonObject c, Minecraft mc, ClientLevel level, LocalPlayer player, RandomSource rand,
            ResourceLocation here) {
        JsonObject s = c.getAsJsonObject("sea");
        if (s == null || SEA.size() >= s.get("max").getAsInt() || !set(s.getAsJsonArray("biomes"))
                .contains(here.toString())) {
            return;
        }
        int range = s.get("range").getAsInt();
        int tries = s.get("tries").getAsInt();
        for (float ring : new float[] { 0.25f, 0.5f, 0.75f, 1f }) {
            int r = Math.max(1, (int) (range * ring));
            for (int i = 0; i < tries; i++) {
                BlockPos at = player.blockPosition().offset(Mth.nextInt(rand, -r, r), Mth.nextInt(rand, -16, 8),
                        Mth.nextInt(rand, -r, r));
                if (!level.getFluidState(at).is(Fluids.WATER)
                        || at.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) - 1) {
                    continue;
                }
                SoundInstance snd = sound(s.get("sound").getAsString(), at, 1f, 0.8f + rand.nextFloat() * 0.4f,
                        rand);
                mc.getSoundManager().play(snd);
                SEA.add(snd);
                return;
            }
        }
    }

    /** Vanilla rain and thunder become LOTR's while the player is in Middle-earth (LOTRAmbience.onPlaySound). */
    @SubscribeEvent
    public static void replaceWeather(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (original == null || mc.level == null || mc.player == null
                || !inMiddleEarth(mc.level, mc.player.blockPosition())) {
            return;
        }
        JsonObject weather = config().getAsJsonObject("weather");
        String key = original.getLocation().toString();
        if (weather == null || !weather.has(key)) {
            return;
        }
        original.resolve(mc.getSoundManager());
        event.setSound(new SimpleSoundInstance(ResourceLocation.parse(weather.get(key).getAsString()),
                original.getSource(), original.getVolume(), original.getPitch(), mc.level.getRandom(),
                original.isLooping(), original.getDelay(), original.getAttenuation(), original.getX(),
                original.getY(), original.getZ(), original.isRelative()));
    }

    private static SoundInstance sound(String id, BlockPos at, float volume, float pitch, RandomSource rand) {
        return new SimpleSoundInstance(ResourceLocation.parse(id), SoundSource.AMBIENT, volume, pitch, rand, false, 0,
                SoundInstance.Attenuation.LINEAR, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, false);
    }

    private static final Map<JsonArray, Set<String>> SETS = new HashMap<>();

    private static Set<String> set(JsonArray a) {
        if (a == null) {
            return Set.of();
        }
        return SETS.computeIfAbsent(a, k -> {
            Set<String> out = new HashSet<>();
            for (JsonElement e : k) {
                out.add(e.getAsString());
            }
            return out;
        });
    }
}
