package com.porcupinestudios.lotrport.faction;

import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Every faction: the great powers from data ({@code data/<namespace>/faction/*.json}) and the realms founded in play.
 * Relations come from {@link Diplomacy} when changed in play, otherwise from the data; a realm without its own
 * relations takes its culture's.
 */
public final class Factions {
    public static final String FOLDER = "faction";

    private static final CodecJsonLoader<FactionDef> LOADER = new CodecJsonLoader<>(FOLDER, FactionDef.CODEC) {
        @Override
        protected List<String> validate(Map<ResourceLocation, FactionDef> entries) {
            List<String> problems = new ArrayList<>();
            entries.forEach((id, def) -> def.relations().keySet().forEach(other -> {
                if (!entries.containsKey(other)) {
                    problems.add(id + ": relation with unknown faction " + other);
                }
            }));
            entries.forEach((id, def) -> {
                if (def.pledgeRank().isPresent()
                        && def.ranks().stream().noneMatch(r -> r.id().equals(def.pledgeRank().get()))) {
                    problems.add(id + ": pledge rank " + def.pledgeRank().get() + " is not one of its ranks");
                }
            });
            return problems;
        }
    };

    private Factions() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(LOADER));
    }

    public static Map<ResourceLocation, FactionDef> definitions() {
        return LOADER.entries();
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }

    /** Every faction: great powers first, then realms. */
    public static List<Faction> all(MinecraftServer server) {
        Map<ResourceLocation, Faction> all = new LinkedHashMap<>();
        LOADER.entries().forEach((id, def) -> all.put(id, Faction.of(id, def)));
        for (Realms.Realm realm : Realms.get(server).all()) {
            all.put(realm.id(), realmFaction(realm));
        }
        return List.copyOf(all.values());
    }

    public static Optional<Faction> get(MinecraftServer server, ResourceLocation id) {
        FactionDef def = LOADER.entries().get(id);
        if (def != null) {
            return Optional.of(Faction.of(id, def));
        }
        return Realms.get(server).get(id).map(Factions::realmFaction);
    }

    private static Faction realmFaction(Realms.Realm realm) {
        return new Faction(realm.id(), Component.literal(realm.name()), realm.color(), true, Optional.empty(),
                List.of(), Optional.empty(), 1f, true);
    }

    /** The relation between two factions now: a faction is its own ally. */
    public static Relation relation(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        if (a.equals(b)) {
            return Relation.ALLY;
        }
        Optional<Relation> live = Diplomacy.get(server).override(a, b);
        if (live.isPresent()) {
            return live.get();
        }
        return defaultRelation(server, a, b);
    }

    /** The relation from data, before anything changed in play. */
    public static Relation defaultRelation(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        ResourceLocation ca = culture(server, a);
        ResourceLocation cb = culture(server, b);
        if (ca.equals(cb)) {
            return a.equals(ca) || b.equals(cb) ? Relation.FRIEND : Relation.NEUTRAL;
        }
        FactionDef da = LOADER.entries().get(ca);
        FactionDef db = LOADER.entries().get(cb);
        if (da != null && da.relations().containsKey(cb)) {
            return da.relations().get(cb);
        }
        if (db != null && db.relations().containsKey(ca)) {
            return db.relations().get(ca);
        }
        return Relation.NEUTRAL;
    }

    /** The great power a faction follows: itself, or a realm's culture. */
    private static ResourceLocation culture(MinecraftServer server, ResourceLocation id) {
        if (LOADER.entries().containsKey(id)) {
            return id;
        }
        return Realms.get(server).get(id).flatMap(Realms.Realm::culture).orElse(id);
    }
}
