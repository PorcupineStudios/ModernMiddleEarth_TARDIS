package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.tech.TechDef;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A building's page (its plaque, RingRealms): type and quality, what the room measured, who lives or works there,
 * what its trade makes (locked recipes say which research opens them) and the type's requirements. Opened from the
 * colony view's Buildings tab, or by sneak + right-clicking the building's door with an empty hand. Context:
 * {@code <colony id>/<building id>}.
 */
public final class BuildingViews {
    public static final ResourceLocation ID = LotrPort.id("building");

    private BuildingViews() {}

    public static void register() {
        Views.register(ID, new Provider());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BuildingViews::onRightClick);
    }

    static View.Target target(Colony c, Building b) {
        return new View.Target(ID, c.id() + "/" + b.id(), "");
    }

    /** Sneak + right-click a building's door with an empty hand: its page. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND || !e.getEntity().isShiftKeyDown()
                || !e.getItemStack().isEmpty() || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = e.getPos();
        for (Colony c : Colonies.get(player.server).all()) {
            if (!c.dimension().equals(player.level().dimension())) {
                continue;
            }
            Optional<Building> b = Buildings.of(c).atDoor(pos).or(() -> Buildings.of(c).atDoor(pos.below()));
            if (b.isPresent()) {
                if (Permissions.isMember(player.getUUID(), c)) {
                    Views.open(player, target(c, b.get()), true);
                    e.setCanceled(true);
                }
                return;
            }
        }
    }

    static final class Provider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            String[] parts = context.split("/");
            if (parts.length != 2) {
                return Optional.empty();
            }
            Optional<Colony> found = ColonyViews.colony(player.server, parts[0]);
            if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
                return Optional.empty();
            }
            Colony c = found.get();
            Optional<Building> fb;
            try {
                fb = Buildings.of(c).get(UUID.fromString(parts[1]));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
            if (fb.isEmpty()) {
                return Optional.empty();
            }
            Building b = fb.get();
            Optional<BuildingType> type = BuildingType.get(b.type());
            List<Page.Section> sections = new ArrayList<>();

            List<Stat> about = new ArrayList<>();
            about.add(Stat.of(tr("building.type", "Type"), BuildingType.titleOf(b.type()).getString()));
            about.add(Stat.of(tr("building.quality", "Quality"), Integer.toString(b.quality())));
            about.add(Stat.of(tr("building.door", "Door"), b.door().toShortString(), Tone.MUTED));
            about.add(Stat.of(tr("building.built", "Built"), "day " + b.built() / 24000, Tone.MUTED));
            StringBuilder measures = new StringBuilder();
            for (Map.Entry<String, Integer> m : new java.util.TreeMap<>(b.counts()).entrySet()) {
                if (m.getValue() > 0) {
                    measures.append(measures.length() == 0 ? "" : ", ").append(m.getValue()).append(' ')
                            .append(m.getKey().replace('_', ' '));
                }
            }
            about.add(Stat.of(tr("building.measures", "Measured"), measures.toString(), Tone.MUTED));
            sections.add(new Page.Section(tr("building.about", "The building"), about));

            List<Stat> people = new ArrayList<>();
            for (Person p : Society.citizens(player.server, c)) {
                boolean lives = p.get(WorkComponents.HOME).map(h -> h.building().equals(b.id())).orElse(false);
                Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB).filter(j -> j.building().equals(b.id()));
                if (!lives && job.isEmpty()) {
                    continue;
                }
                String what = ColonyViews.title(Races.of(p.type())) + (job.map(j -> ", " + JobDef.titleOf(j.job())
                        .getString() + " (skill " + Math.round(j.skill()) + ")").orElse("")) + (lives ? ", lives here" : "");
                people.add(Stat.of(Component.literal(ColonyViews.personName(p)), what));
            }
            if (people.isEmpty()) {
                people.add(Stat.of(tr("building.empty", "Nobody lives or works here"), "", Tone.MUTED));
            }
            sections.add(new Page.Section(tr("building.people", "People"), people));

            Optional<ResourceLocation> jobId = type.flatMap(BuildingType::job);
            Optional<JobDef> job = jobId.flatMap(JobDef::get);
            if (job.isPresent() && !job.get().recipes().isEmpty()) {
                List<Stat> makes = new ArrayList<>();
                for (JobDef.Recipe r : job.get().recipes()) {
                    String outs = items(r.produces().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, x -> (int) Math.round(x.getValue()))));
                    String ins = r.consumes().isEmpty() ? "from the land" : "from " + items(r.consumes());
                    boolean locked = r.requires().isPresent() && !Production.unlocked.test(c, r.requires().get());
                    makes.add(Stat.of(Component.literal(outs), locked ? "needs research: "
                            + TechDef.titleOf(r.requires().get()) : ins, locked ? Tone.MUTED : Tone.NORMAL));
                }
                job.get().tool().ifPresent(t -> makes.add(Stat.of(tr("building.tool", "Wears out"),
                        ProductionViews.name(t), Tone.MUTED)));
                sections.add(new Page.Section(tr("building.makes", "What it makes"), makes));
            }

            if (type.isPresent()) {
                List<Stat> needs = new ArrayList<>();
                for (BuildingType.Requirement r : type.get().requires()) {
                    boolean ok = r.holds(b.counts());
                    needs.add(Stat.of(r.why(), ok ? "met" : "missing", ok ? Tone.GOOD : Tone.BAD));
                }
                if (!needs.isEmpty()) {
                    sections.add(new Page.Section(tr("building.needs", "What the type needs"), needs));
                }
            }
            return Optional.of(new View(ID, context, b.label(),
                    List.of(new View.Tab("page", tr("building.tab", "Plaque"))), "page", new Page.Stats(sections,
                    List.<Action>of()), Optional.of(new View.Target(ColonyViews.COLONY, c.id().toString(),
                            ColonyViews.BUILDINGS))));
        }

        @Override
        public Component denied() {
            return tr("denied", "Only members of a colony may manage it");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            return Result.NONE;
        }
    }

    static String items(Map<ResourceLocation, Integer> m) {
        List<String> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(v + " " + ProductionViews.name(k)));
        return String.join(", ", out);
    }
}
