package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.tech.MilestoneDef;
import com.porcupinestudios.lotrport.colony.tech.TechDef;
import com.porcupinestudios.lotrport.colony.tech.Techs;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colony view's Tech tab: the research points on hand, the tech tree as one section per lane (nodes in stage
 * order; set a node to 1 to research it, {@link Perm#RESEARCH}), and the milestones reached and still ahead.
 */
final class TechViews {
    static final String TAB = "tech";

    private TechViews() {}

    static Page tab(ServerPlayer player, Colony c) {
        var server = player.server;
        Techs.State st = Techs.state(server, c);
        List<Page.Section> sections = new ArrayList<>();
        List<Stat> points = new ArrayList<>();
        for (ResourceLocation tier : Research.tiers().keySet()) {
            points.add(Stat.of(Component.literal(Research.name(tier)), Integer.toString((int) Math.floor(
                    Research.points(server, c, tier)))));
        }
        points.add(Stat.of(tr("tech.help", "Set a node to 1 to research it. Nodes marked 'fork' close the others of "
                + "their fork for good."), "", Tone.MUTED));
        sections.add(new Page.Section(tr("tech.points", "Research points"), points));
        Map<String, List<Stat>> lanes = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, TechDef> e : TechDef.sorted()) {
            ResourceLocation id = e.getKey();
            TechDef d = e.getValue();
            boolean have = st.techs().containsKey(id);
            Optional<Component> why = have ? Optional.empty() : Techs.whyNot(server, c, id);
            String label = "[" + d.stage() + "] " + d.title(id) + (d.fork().isPresent() ? " (fork: " + d.fork().get()
                    + ")" : "") + (d.description().isEmpty() ? "" : ": " + d.description());
            String value = have ? "researched" : why.map(w -> w.getString() + " - costs " + d.cost().text())
                    .orElse("ready - costs " + d.cost().text());
            Tone tone = have ? Tone.GOOD : why.isPresent() ? Tone.MUTED : Tone.NORMAL;
            Stat s = Stat.of(Component.literal(label), value, tone);
            if (!have) {
                s = s.withStepper(new Stat.Stepper("tech:" + id, 0, 0, why.isPresent() ? 0 : 1, 1));
            }
            lanes.computeIfAbsent(d.lane(), k -> new ArrayList<>()).add(s);
        }
        lanes.forEach((lane, rows) -> sections.add(new Page.Section(Component.literal(ColonyViews.title(lane)), rows)));
        List<Stat> miles = new ArrayList<>();
        List<Map.Entry<ResourceLocation, MilestoneDef>> all = new ArrayList<>(MilestoneDef.all().entrySet());
        all.sort(java.util.Comparator.comparingInt(e -> e.getValue().order()));
        for (Map.Entry<ResourceLocation, MilestoneDef> e : all) {
            boolean done = st.milestones().containsKey(e.getKey());
            String reward = e.getValue().coins() > 0 ? e.getValue().coins() + " coins" : "";
            miles.add(Stat.of(Component.literal(e.getValue().title(e.getKey()) + ": " + e.getValue().description()),
                    done ? "reached" : reward.isEmpty() ? "ahead" : "ahead - " + reward, done ? Tone.GOOD : Tone.MUTED));
        }
        if (!miles.isEmpty()) {
            sections.add(new Page.Section(tr("tech.milestones", "Milestones"), miles));
        }
        return new Page.Stats(sections, List.<Action>of());
    }

    static Optional<ViewProvider.Result> act(ServerPlayer player, Colony c, String action, int value) {
        if (!action.startsWith("tech:")) {
            return Optional.empty();
        }
        if (value <= 0) {
            return Optional.of(ViewProvider.Result.NONE);
        }
        if (!Permissions.can(player.getUUID(), c, Perm.RESEARCH)) {
            return Optional.of(ViewProvider.Result.say(tr("market.no_research", "Only those who may direct research can")));
        }
        ResourceLocation id = ResourceLocation.tryParse(action.substring(5));
        return Optional.of(id == null ? ViewProvider.Result.NONE
                : ViewProvider.Result.say(Techs.research(player.server, c, id)));
    }
}
