package com.porcupinestudios.lotrport.land;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Land claims, {@code data/<ns>/colony_land/settings.json} (reloads). Radii are in chunks: radius 1 = 3 x 3 chunks.
 *
 * @param hallRadius land a colony gets around its Hall when founded
 * @param bannerRadius land each colony banner adds
 * @param bannerReach farthest a colony banner may stand from the Hall, in chunks
 * @param bannersBase banners every colony may place
 * @param citizensPerBanner one more banner per this many citizens
 * @param personalBanners banners a player may place outside any colony
 * @param personalRadius land a personal banner claims
 * @param protectExplosions explosions don't break blocks on claimed land
 */
public record LandSettings(int hallRadius, int bannerRadius, int bannerReach, int bannersBase, int citizensPerBanner,
        int personalBanners, int personalRadius, boolean protectExplosions) {
    public static final LandSettings DEFAULT = new LandSettings(2, 1, 8, 2, 10, 1, 0, true);
    public static final Codec<LandSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("hall_radius", DEFAULT.hallRadius()).forGetter(LandSettings::hallRadius),
            Codec.INT.optionalFieldOf("banner_radius", DEFAULT.bannerRadius()).forGetter(LandSettings::bannerRadius),
            Codec.INT.optionalFieldOf("banner_reach", DEFAULT.bannerReach()).forGetter(LandSettings::bannerReach),
            Codec.INT.optionalFieldOf("banners_base", DEFAULT.bannersBase()).forGetter(LandSettings::bannersBase),
            Codec.INT.optionalFieldOf("citizens_per_banner", DEFAULT.citizensPerBanner())
                    .forGetter(LandSettings::citizensPerBanner),
            Codec.INT.optionalFieldOf("personal_banners", DEFAULT.personalBanners())
                    .forGetter(LandSettings::personalBanners),
            Codec.INT.optionalFieldOf("personal_radius", DEFAULT.personalRadius())
                    .forGetter(LandSettings::personalRadius),
            Codec.BOOL.optionalFieldOf("protect_explosions", DEFAULT.protectExplosions())
                    .forGetter(LandSettings::protectExplosions))
            .apply(i, LandSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<LandSettings> LOADER = new CodecJsonLoader<>("colony_land", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static LandSettings get() {
        return LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
