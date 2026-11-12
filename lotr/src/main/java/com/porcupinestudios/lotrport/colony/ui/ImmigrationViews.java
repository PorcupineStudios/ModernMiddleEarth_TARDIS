package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.Immigration;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.TableBuilder;
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
 * The colony view's Immigration tab: the groups that would like to settle, each a row (who, how they are bound,
 * their best skills, ages), to welcome or turn away; when the board is next drawn and what the colony's level gives.
 */
final class ImmigrationViews {
    static final String TAB = "immigration";

    private ImmigrationViews() {}

    static Page tab(ServerPlayer player, Colony c) {
        Immigration.Board b = Immigration.board(player.server, c);
        Immigration.Perks perks = Immigration.perks(player.server, c);
        long now = player.server.overworld().getGameTime();
        int minutes = (int) Math.max(0, Math.ceil((b.next() - now) / 1200.0));
        boolean may = Permissions.can(player.getUUID(), c, Perm.MIGRANTS);
        Component why = tr("immigration.no_perm", "Only those who may answer migrants can");
        String level = perks.levels().isEmpty() ? "Hamlet" : perks.levels().get(perks.levels().size() - 1);
        return TableBuilder.of(b.offers(), o -> Integer.toString(o.id()))
                .column(Column.text("who", tr("immigration.col.who", "Who"), 5), o -> Cell.text(String.join(", ",
                        o.members().stream().map(m -> m.name().isEmpty() ? "?" : m.name()).toList())))
                .column(Column.text("group", tr("immigration.col.group", "Group"), 2), o -> Cell.text(switch (o.bond()) {
                    case "family" -> "Family of " + o.members().size();
                    case "friends" -> o.members().size() + " friends";
                    default -> "Alone";
                }, o.members().size() > 1 ? Tone.GOOD : Tone.NORMAL))
                .column(Column.text("race", tr("immigration.col.race", "Race"), 2), o -> Cell.text(String.join(", ",
                        o.members().stream().map(m -> ColonyViews.title(Races.of(m.type()))).distinct().toList())))
                .column(Column.text("skills", tr("immigration.col.skills", "Best skills"), 4), o -> Cell.text(
                        skills(o)))
                .column(Column.text("ages", tr("immigration.col.ages", "Ages"), 2), o -> Cell.text(String.join(", ",
                        o.members().stream().map(m -> Integer.toString(m.age())).toList())))
                .rowAction(may ? Action.of("welcome", tr("immigration.welcome", "Welcome"))
                        : Action.disabled("welcome", tr("immigration.welcome", "Welcome"), why))
                .rowAction(may ? Action.of("turn_away", tr("immigration.turn_away", "Turn away"))
                        : Action.disabled("turn_away", tr("immigration.turn_away", "Turn away"), why))
                // one short line (the row actions share the footer): hover for the level and what it gives
                .button(Action.disabled("info", Component.literal("New in " + minutes + " min"),
                        Component.literal(level + ": " + perks.slots() + " slot" + (perks.slots() == 1 ? "" : "s")
                                + ", groups up to " + perks.maxGroup() + ", quality " + perks.quality()
                                + ". Grows with population and upgrades.")))
                .empty(tr("immigration.none", "Nobody is waiting. New faces come every 30 minutes."))
                .build();
    }

    static String skills(Immigration.Offer o) {
        Map<ResourceLocation, Integer> best = new LinkedHashMap<>();
        for (Immigration.Prospect m : o.members()) {
            m.skills().forEach((k, v) -> best.merge(k, v.level(), Math::max));
        }
        List<Map.Entry<ResourceLocation, Integer>> top = new ArrayList<>(best.entrySet());
        top.sort(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            if (top.get(i).getValue() > 0) {
                out.add(Skills.name(top.get(i).getKey()) + " " + top.get(i).getValue());
            }
        }
        return out.isEmpty() ? "-" : String.join(", ", out);
    }

    static Optional<ViewProvider.Result> act(ServerPlayer player, Colony c, String action, String row) {
        if (!action.equals("welcome") && !action.equals("turn_away")) {
            return Optional.empty();
        }
        if (!Permissions.can(player.getUUID(), c, Perm.MIGRANTS)) {
            return Optional.of(ViewProvider.Result.say(tr("immigration.no_perm",
                    "Only those who may answer migrants can")));
        }
        int id = CaravanViews.parse(row);
        Immigration.Answer a = action.equals("welcome") ? Immigration.accept(player, c, id)
                : Immigration.decline(player.server, c, id);
        return Optional.of(ViewProvider.Result.say(a.message()));
    }
}
