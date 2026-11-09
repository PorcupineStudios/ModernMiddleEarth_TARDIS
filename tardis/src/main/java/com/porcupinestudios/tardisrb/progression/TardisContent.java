package com.porcupinestudios.tardisrb.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * All TARDIS tuning data, read from datapacks in one go so the validator can check ids across folders: subsystems,
 * stages, milestones, repairs, research, features and costs under {@code data/<namespace>/tardis/<folder>/}. It
 * reloads on {@code /reload}. Bad files and entries that point at missing ids or form research loops are dropped with
 * a log line naming the file; everything else still loads.
 */
public final class TardisContent extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonElement>>> {
    public static final String SUBSYSTEM = "subsystem";
    public static final String STAGE = "stage";
    public static final String MILESTONE = "milestone";
    public static final String REPAIR = "repair";
    public static final String RESEARCH = "research";
    public static final String FEATURE = "feature";
    public static final String COST = "cost";
    public static final String TUNING = "tuning";
    public static final String LOOK = "look";
    public static final String ROOM = "room";
    public static final String DESTINATION = "destination";
    public static final String CONTROL = "control";
    public static final String SKY = "sky";
    public static final String SYSTEM = "system";
    public static final String WORMHOLE = "wormhole";
    public static final List<String> FOLDERS =
            List.of(SUBSYSTEM, STAGE, MILESTONE, REPAIR, RESEARCH, FEATURE, COST, TUNING, LOOK, ROOM,
            DESTINATION, CONTROL, SKY, SYSTEM, WORMHOLE);
    /** The look file for the police box. */
    public static final ResourceLocation EXTERIOR_LOOK = ResourceLocation.fromNamespaceAndPath("tardis_rb", "exterior");
    /** The tuning file that counts; others in the folder are ignored. */
    public static final ResourceLocation MAIN_TUNING = ResourceLocation.fromNamespaceAndPath("tardis_rb", "main");

    private static final TardisContent LOADER = new TardisContent();
    private static volatile Snapshot current = Snapshot.EMPTY;
    private static int generation;

    /** One consistent set of data. {@code generation} goes up on every reload, so caches know when to clear. */
    public record Snapshot(Map<ResourceLocation, Subsystem> subsystems, Map<ResourceLocation, Stage> stages,
            Map<ResourceLocation, Milestone> milestones, Map<ResourceLocation, Repair> repairs,
            Map<ResourceLocation, Research> research, Map<ResourceLocation, Feature> features,
            Map<ResourceLocation, Cost> costs, Tuning tuning, Map<ResourceLocation, Look> looks,
            Map<ResourceLocation, Room> rooms, Map<ResourceLocation, Destination> destinations,
            Map<ResourceLocation, Control> controls, Map<ResourceLocation, SkyLink> skies,
            Map<ResourceLocation, SystemFile> systems, Map<ResourceLocation, WormholeExit> wormholes, List<String> problems,
            int generation) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Tuning.DEFAULT, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), 0);

        public Look exteriorLook() {
            return looks.getOrDefault(EXTERIOR_LOOK, Look.NONE);
        }

        /** Stages from first to last. */
        public List<Map.Entry<ResourceLocation, Stage>> stageOrder() {
            List<Map.Entry<ResourceLocation, Stage>> out = new ArrayList<>(stages.entrySet());
            out.sort(Comparator.comparingInt((Map.Entry<ResourceLocation, Stage> e) -> e.getValue().order())
                    .thenComparing(e -> e.getKey().toString()));
            return out;
        }

        /** A cost by reference or inline; a missing reference (dropped by the validator) reads as free. */
        public Cost cost(Either<ResourceLocation, Cost> ref) {
            return ref.map(id -> costs.getOrDefault(id, Cost.FREE), c -> c);
        }
    }

    private TardisContent() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(LOADER));
    }

    public static Snapshot get() {
        return current;
    }

    public static Tuning tuning() {
        return current.tuning;
    }

    public static Optional<Subsystem> subsystem(ResourceLocation id) {
        return Optional.ofNullable(current.subsystems.get(id));
    }

    public static Optional<Repair> repair(ResourceLocation id) {
        return Optional.ofNullable(current.repairs.get(id));
    }

    public static Optional<Feature> feature(ResourceLocation id) {
        return Optional.ofNullable(current.features.get(id));
    }

    public static Optional<Destination> destination(ResourceLocation id) {
        return Optional.ofNullable(current.destinations.get(id));
    }

    public static Optional<Control> control(ResourceLocation id) {
        return Optional.ofNullable(current.controls.get(id));
    }

    public static Optional<Room> room(ResourceLocation id) {
        return Optional.ofNullable(current.rooms.get(id));
    }

    public static Optional<Research> research(ResourceLocation id) {
        return Optional.ofNullable(current.research.get(id));
    }

    @Override
    protected Map<String, Map<ResourceLocation, JsonElement>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Map<ResourceLocation, JsonElement>> out = new HashMap<>();
        List<String> unreadable = new ArrayList<>();
        for (String folder : FOLDERS) {
            FileToIdConverter files = FileToIdConverter.json("tardis/" + folder);
            Map<ResourceLocation, JsonElement> jsons = new TreeMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : files.listMatchingResources(manager).entrySet()) {
                ResourceLocation id = files.fileToId(e.getKey());
                try (Reader reader = e.getValue().openAsReader()) {
                    jsons.put(id, JsonParser.parseReader(reader));
                } catch (Exception ex) {
                    unreadable.add(folder + " " + id + ": " + ex.getMessage());
                }
            }
            out.put(folder, jsons);
        }
        unreadable.forEach(u -> TardisRB.LOG.error("[tardis data] {}", u));
        return out;
    }

    @Override
    protected void apply(Map<String, Map<ResourceLocation, JsonElement>> files, ResourceManager manager,
            ProfilerFiller profiler) {
        Snapshot next = build(files, ++generation);
        next.problems.forEach(p -> TardisRB.LOG.error("[tardis data] {}", p));
        TardisRB.LOG.info("[tardis data] {} subsystems, {} stages, {} milestones, {} repairs, {} research, "
                + "{} features, {} costs, {} rooms, {} destinations, {} controls; {} problems", next.subsystems.size(),
                next.stages.size(),
                next.milestones.size(), next.repairs.size(), next.research.size(), next.features.size(),
                next.costs.size(), next.rooms.size(), next.destinations.size(), next.controls.size(), next.problems.size());
        current = next;
    }

    /** Parses and checks a set of files; pure, so tests can feed it broken data. */
    public static Snapshot build(Map<String, Map<ResourceLocation, JsonElement>> files, int generation) {
        List<String> problems = new ArrayList<>();
        Map<ResourceLocation, Subsystem> subsystems = parse(files, SUBSYSTEM, Subsystem.CODEC, problems);
        Map<ResourceLocation, Stage> stages = parse(files, STAGE, Stage.CODEC, problems);
        Map<ResourceLocation, Milestone> milestones = parse(files, MILESTONE, Milestone.CODEC, problems);
        Map<ResourceLocation, Repair> repairs = parse(files, REPAIR, Repair.CODEC, problems);
        Map<ResourceLocation, Research> research = parse(files, RESEARCH, Research.CODEC, problems);
        Map<ResourceLocation, Feature> features = parse(files, FEATURE, Feature.CODEC, problems);
        Map<ResourceLocation, Cost> costs = parse(files, COST, Cost.CODEC, problems);
        Tuning tuning = parse(files, TUNING, Tuning.CODEC, problems).getOrDefault(MAIN_TUNING, Tuning.DEFAULT);
        Map<ResourceLocation, Look> looks = parse(files, LOOK, Look.CODEC, problems);
        Map<ResourceLocation, Room> rooms = parse(files, ROOM, Room.CODEC, problems);
        Map<ResourceLocation, Destination> destinations = parse(files, DESTINATION, Destination.CODEC, problems);
        Map<ResourceLocation, Control> controls = parse(files, CONTROL, Control.CODEC, problems);
        Map<ResourceLocation, SkyLink> skies = parse(files, SKY, SkyLink.CODEC, problems);
        skies.entrySet().removeIf(e -> {
            SkyLink k = e.getValue();
            String bad = k.problem();
            if (bad != null) {
                problems.add("sky " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });
        Map<ResourceLocation, SystemFile> systems = parse(files, SYSTEM, SystemFile.CODEC, problems);
        Map<ResourceLocation, WormholeExit> wormholes = parse(files, WORMHOLE, WormholeExit.CODEC, problems);
        systems.entrySet().removeIf(e -> {
            String bad = e.getValue().problem();
            if (bad != null) {
                problems.add("system " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });
        controls.entrySet().removeIf(e -> {
            Control c = e.getValue();
            String bad = c.actions().values().stream().filter(a -> !ConsoleControls.isAction(a))
                    .findFirst().map(a -> "unknown action " + a)
                    .orElse(c.shows().stream().filter(s -> !ConsoleControls.VALUES.contains(s)).findFirst()
                            .map(s -> "unknown value " + s).orElse(null));
            if (bad != null) {
                problems.add("control " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });

        subsystems.entrySet().removeIf(e -> {
            if (e.getValue().levels().isEmpty()) {
                problems.add("subsystem " + e.getKey() + ": needs at least one level (level 0)");
                return true;
            }
            return false;
        });
        repairs.entrySet().removeIf(e -> {
            Repair r = e.getValue();
            Subsystem s = subsystems.get(r.subsystem());
            String bad = s == null ? "unknown subsystem " + r.subsystem()
                    : r.level() > s.maxLevel() ? "level " + r.level() + " is above " + r.subsystem() + "'s max "
                            + s.maxLevel()
                    : missingCost(r.cost(), costs);
            if (bad != null) {
                problems.add("repair " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });
        milestones.forEach((id, m) -> m.caps().keySet().stream().filter(s -> !subsystems.containsKey(s))
                .forEach(s -> problems.add("milestone " + id + ": cap for unknown subsystem " + s)));
        stages.entrySet().removeIf(e -> {
            Stage st = e.getValue();
            String bad = st.milestone().filter(m -> !milestones.containsKey(m)).map(m -> "unknown milestone " + m)
                    .orElse(st.repairs().stream().filter(r -> !repairs.containsKey(r)).findFirst()
                            .map(r -> "unknown repair " + r).orElse(null));
            if (bad != null) {
                problems.add("stage " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });
        research.entrySet().removeIf(e -> {
            String bad = missingCost(e.getValue().cost(), costs);
            if (bad != null) {
                problems.add("research " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });
        removeBrokenResearch(research, problems);
        rooms.entrySet().removeIf(e -> {
            String bad = missingCost(e.getValue().cost(), costs);
            if (bad != null) {
                problems.add("room " + e.getKey() + ": " + bad);
            }
            return bad != null;
        });

        return new Snapshot(Map.copyOf(subsystems), Map.copyOf(stages), Map.copyOf(milestones), Map.copyOf(repairs),
                Map.copyOf(research), Map.copyOf(features), Map.copyOf(costs), tuning, Map.copyOf(looks), Map.copyOf(rooms),
                Map.copyOf(destinations), Map.copyOf(controls), Map.copyOf(skies), Map.copyOf(systems), Map.copyOf(wormholes),
                List.copyOf(problems),
                generation);
    }

    private static String missingCost(Either<ResourceLocation, Cost> ref, Map<ResourceLocation, Cost> costs) {
        return ref.left().filter(id -> !costs.containsKey(id)).map(id -> "unknown cost " + id).orElse(null);
    }

    /** Drops nodes with unknown parents, nodes in a loop, and nodes that need a dropped node, until none are left. */
    private static void removeBrokenResearch(Map<ResourceLocation, Research> research, List<String> problems) {
        Set<ResourceLocation> inLoop = new HashSet<>();
        Map<ResourceLocation, Integer> state = new HashMap<>(); // 1 = visiting, 2 = done
        for (ResourceLocation start : research.keySet()) {
            findLoops(start, research, state, new ArrayList<>(), inLoop);
        }
        for (ResourceLocation id : inLoop) {
            problems.add("research " + id + ": part of a loop of parents");
        }
        research.keySet().removeAll(inLoop);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var it = research.entrySet().iterator(); it.hasNext();) {
                var e = it.next();
                Optional<ResourceLocation> missing = e.getValue().parents().stream()
                        .filter(p -> !research.containsKey(p)).findFirst();
                if (missing.isPresent()) {
                    problems.add("research " + e.getKey() + ": unknown or dropped parent " + missing.get());
                    it.remove();
                    changed = true;
                }
            }
        }
    }

    private static void findLoops(ResourceLocation id, Map<ResourceLocation, Research> research,
            Map<ResourceLocation, Integer> state, List<ResourceLocation> path, Set<ResourceLocation> inLoop) {
        Integer s = state.get(id);
        if (s != null && s == 2) {
            return;
        }
        if (s != null && s == 1) {
            inLoop.addAll(path.subList(path.indexOf(id), path.size()));
            return;
        }
        Research node = research.get(id);
        if (node == null) {
            return;
        }
        state.put(id, 1);
        path.add(id);
        for (ResourceLocation parent : node.parents()) {
            findLoops(parent, research, state, path, inLoop);
        }
        path.remove(path.size() - 1);
        state.put(id, 2);
    }

    private static <T> Map<ResourceLocation, T> parse(Map<String, Map<ResourceLocation, JsonElement>> files,
            String folder, Codec<T> codec, List<String> problems) {
        Map<ResourceLocation, T> out = new LinkedHashMap<>();
        files.getOrDefault(folder, Map.of()).forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                .ifSuccess(v -> out.put(id, v))
                .ifError(err -> problems.add(folder + " " + id + ": " + err.message())));
        return out;
    }
}
