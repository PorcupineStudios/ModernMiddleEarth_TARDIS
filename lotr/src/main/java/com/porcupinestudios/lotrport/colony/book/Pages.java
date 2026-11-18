package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Shared bits for the book providers: finding the colony, faces, names, person cards. */
public final class Pages {
    private Pages() {}

    public static Optional<Colony> colony(MinecraftServer server, String id) {
        try {
            return Colonies.get(server).get(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A face for an NPC type: one of its skins, picked by the seed ("tex|64" for 64x64 skins). */
    public static String face(ResourceLocation type, long seed) {
        Optional<NpcType> t = NpcData.type(type);
        if (t.isEmpty() || t.get().skins().isEmpty()) {
            return "";
        }
        List<ResourceLocation> skins = t.get().skins();
        return skins.get((int) Math.floorMod(seed, (long) skins.size())) + (t.get().skinHeight() == 64 ? "|64" : "");
    }

    public static String face(Person p) {
        return face(p.type(), p.id().getLeastSignificantBits());
    }

    public static String name(Person p) {
        return p.name().isEmpty() ? title(p.type().getPath()) : p.name();
    }

    public static String title(String word) {
        if (word.isEmpty()) {
            return word;
        }
        String w = word.replace('_', ' ');
        return w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1);
    }

    public static String race(Person p) {
        return title(Races.of(p.type()));
    }

    /** An item's name, translated on the client ({@code {item:<id>}}). */
    public static String item(ResourceLocation id) {
        return "{item:" + id + "}";
    }

    /** A lang key translated on the client, with a fallback. */
    public static String lang(String key, String fallback) {
        return "{lang:" + key + "|" + fallback + "}";
    }

    public static String job(ResourceLocation id) {
        String key = JobDef.get(id).map(JobDef::name).filter(n -> !n.isEmpty())
                .orElse("colony_job." + id.getNamespace() + "." + id.getPath());
        return lang(key, title(id.getPath()));
    }

    public static String building(ResourceLocation id) {
        String key = com.porcupinestudios.lotrport.colony.building.BuildingType.get(id)
                .map(com.porcupinestudios.lotrport.colony.building.BuildingType::name).filter(n -> !n.isEmpty())
                .orElse("colony_building." + id.getNamespace() + "." + id.getPath());
        return lang(key, title(id.getPath()));
    }

    public static String faction(ResourceLocation id) {
        return lang("faction." + id.getNamespace() + "." + id.getPath(), title(id.getPath()));
    }

    public static String items(Map<ResourceLocation, ? extends Number> m) {
        List<String> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(v.intValue() + " " + item(k)));
        return out.isEmpty() ? "nothing" : String.join(", ", out);
    }

    public static String playerName(MinecraftServer server, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(id))
                .map(com.mojang.authlib.GameProfile::getName).orElse(id.toString().substring(0, 8));
    }

    public static String coins(long n) {
        return n + (n == 1 ? " coin" : " coins");
    }

    /** A citizen's card: face, name, race and job, happiness and food meters; clicking opens their page. */
    public static El person(Person p, String extra) {
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        String job = p.get(WorkComponents.JOB).map(j -> job(j.job())).orElse(
                p.has(CoreComponents.UNIT) ? "Soldier" : "No work");
        String sub = race(p) + " - " + job + (extra.isEmpty() ? "" : " - " + extra);
        boolean homeless = !p.has(WorkComponents.HOME) && !p.has(CoreComponents.UNIT);
        int tone = n.unpaid() > 0 || n.happiness() < 25 ? El.BAD : homeless || n.happiness() < 50 ? El.WARN
                : El.NORMAL;
        List<El> meters = List.of(El.meter("Mood", Math.round(n.happiness()) + "", n.happiness() / 100.0),
                El.meter("Food", Math.round(n.food()) + "", n.food() / 20.0));
        return El.card(face(p), name(p), sub, tone, "citizen", p.id().toString(), meters);
    }
}
