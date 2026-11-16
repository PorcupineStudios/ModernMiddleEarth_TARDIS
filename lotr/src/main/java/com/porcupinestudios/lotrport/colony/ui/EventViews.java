package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.events.ColonyEvents;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.TableBuilder;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colony view's Events tab: the one-time policies the Hall may proclaim (weddings, festivals, feasts), each with
 * what it costs, what it needs, and when it can be held again. Holding one needs {@link Perm#EVENTS}.
 */
final class EventViews {
    static final String TAB = "events";

    private EventViews() {}

    static Page tab(ServerPlayer player, Colony c) {
        var server = player.server;
        boolean may = Permissions.can(player.getUUID(), c, Perm.EVENTS);
        Component noPerm = tr("events.no_perm", "Only those who may proclaim events can");
        var rows = new ArrayList<>(ColonyEvents.policies().entrySet());
        return TableBuilder.of(rows, (Map.Entry<ResourceLocation, ColonyEvents.Def> e) -> e.getKey().toString())
                .column(Column.text("event", tr("events.col.event", "Event"), 3), e -> Cell.text(title(e)))
                .column(Column.text("what", tr("events.col.what", "What it does"), 6), e -> Cell.text(
                        e.getValue().policy().get().description()))
                .column(Column.text("cost", tr("events.col.cost", "Costs"), 3), e -> Cell.text(
                        ColonyEvents.costText(e.getValue().policy().get())))
                .column(Column.text("status", tr("events.col.status", "Status"), 3), e -> {
                    Optional<Component> why = ColonyEvents.whyNot(server, c, e.getKey());
                    return why.map(w -> Cell.text(w.getString(), Tone.BAD)).orElse(Cell.text("Ready", Tone.GOOD));
                })
                .rowAction(may ? Action.of("hold", tr("events.hold", "Proclaim"))
                        : Action.disabled("hold", tr("events.hold", "Proclaim"), noPerm))
                .empty(tr("events.none", "The Hall has no events to proclaim."))
                .build();
    }

    static String title(Map.Entry<ResourceLocation, ColonyEvents.Def> e) {
        String t = e.getValue().policy().get().title();
        return t.isEmpty() ? ColonyViews.title(e.getKey().getPath()) : t;
    }

    static Optional<ViewProvider.Result> act(ServerPlayer player, Colony c, String action, String row) {
        if (!action.equals("hold")) {
            return Optional.empty();
        }
        if (!Permissions.can(player.getUUID(), c, Perm.EVENTS)) {
            return Optional.of(ViewProvider.Result.say(tr("events.no_perm", "Only those who may proclaim events can")));
        }
        ResourceLocation id = ResourceLocation.tryParse(row);
        if (id == null) {
            return Optional.of(ViewProvider.Result.NONE);
        }
        ColonyEvents.Answer a = ColonyEvents.proclaim(player.server, c, id, new Random());
        return Optional.of(ViewProvider.Result.say(a.message()));
    }
}
