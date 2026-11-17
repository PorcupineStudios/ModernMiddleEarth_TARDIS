package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.people.Council;
import com.porcupinestudios.lotrport.colony.people.EdictDef;
import com.porcupinestudios.lotrport.colony.work.Modifiers;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colony view's Council tab: unity and what it does, the colony-wide modifiers, the edicts (proclaim with the
 * picker, repeal by stepping to 0; {@link Perm#EVENTS}) and the latest quarrels and brawls.
 */
final class CouncilViews {
    static final String TAB = "council";

    private CouncilViews() {}

    static Page tab(ServerPlayer player, Colony c) {
        var server = player.server;
        Council.State st = Council.state(server, c);
        List<Stat> summary = new ArrayList<>();
        summary.add(Stat.of(tr("council.unity", "Unity"), Math.round(st.unity()) + " / 100",
                st.unity() < 30 ? Tone.BAD : st.unity() < 50 ? Tone.WARN : Tone.GOOD));
        for (Modifiers.Part p : Modifiers.parts(server, c)) {
            String v = String.format(java.util.Locale.ROOT, "work x%.2f, happiness %+.0f", p.work(), p.happiness());
            summary.add(Stat.of(Component.literal("  " + ColonyViews.title(p.name())), v, Tone.MUTED));
        }
        List<Stat> edicts = new ArrayList<>();
        edicts.add(Stat.of(Component.literal("In force (" + st.edicts().size() + " of "
                + Council.slots(server, c) + " slots); set to 0 to repeal"), "", Tone.MUTED));
        for (ResourceLocation id : st.edicts()) {
            EdictDef d = EdictDef.get(id).orElse(null);
            if (d != null) {
                edicts.add(CaravanViews.stepper(d.title(id) + " (" + d.upkeep() + " coins a day)", "repeal:" + id,
                        1, 0, 1));
            }
        }
        List<Stat> offer = new ArrayList<>();
        for (Map.Entry<ResourceLocation, EdictDef> e : EdictDef.all().entrySet()) {
            if (st.edicts().contains(e.getKey())) {
                continue;
            }
            EdictDef d = e.getValue();
            Optional<Component> why = Council.whyNot(server, c, e.getKey());
            offer.add(Stat.of(Component.literal(d.title(e.getKey()) + ": " + d.description()),
                    why.map(Component::getString).orElse("set to 1 to proclaim (" + d.upkeep() + " coins a day)"),
                    why.isPresent() ? Tone.MUTED : Tone.GOOD)
                    .withStepper(new Stat.Stepper("proclaim:" + e.getKey(), 0, 0, why.isPresent() ? 0 : 1, 1)));
        }
        List<Stat> reports = new ArrayList<>();
        for (int i = st.reports().size() - 1; i >= 0; i--) {
            Council.Report r = st.reports().get(i);
            reports.add(Stat.of(Component.literal(r.text()), "day " + r.time() / 24000, r.brawl() ? Tone.BAD
                    : Tone.NORMAL));
        }
        if (reports.isEmpty()) {
            reports.add(Stat.of(tr("council.calm", "All quiet"), "", Tone.MUTED));
        }
        return new Page.Stats(List.of(new Page.Section(tr("council.summary", "The colony"), summary),
                new Page.Section(tr("council.edicts", "Edicts in force"), edicts),
                new Page.Section(tr("council.offer", "Edicts the Council may proclaim"), offer),
                new Page.Section(tr("council.reports", "Quarrels and brawls"), reports)), List.<Action>of());
    }

    static Optional<ViewProvider.Result> act(ServerPlayer player, Colony c, String action, int value) {
        boolean pro = action.startsWith("proclaim:");
        if (!pro && !action.startsWith("repeal:")) {
            return Optional.empty();
        }
        if (!Permissions.can(player.getUUID(), c, Perm.EVENTS)) {
            return Optional.of(ViewProvider.Result.say(tr("council.no_perm", "Only those who may proclaim can")));
        }
        ResourceLocation id = ResourceLocation.tryParse(action.substring(action.indexOf(':') + 1));
        if (id == null) {
            return Optional.of(ViewProvider.Result.NONE);
        }
        if (pro && value > 0) {
            return Optional.of(ViewProvider.Result.say(Council.proclaim(player.server, c, id)));
        }
        if (!pro && value <= 0) {
            return Optional.of(ViewProvider.Result.say(Council.repeal(player.server, c, id)));
        }
        return Optional.of(ViewProvider.Result.NONE);
    }
}
