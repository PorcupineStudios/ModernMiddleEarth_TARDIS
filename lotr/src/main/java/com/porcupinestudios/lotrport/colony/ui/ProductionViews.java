package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colony view's Production tab: work orders and stock targets for everything the colony's trades can make
 * ({@link Production}). Changing them needs {@link Perm#PRODUCTION}.
 */
final class ProductionViews {
    static final String TAB = "production";
    static final int MAX = 4096;

    private ProductionViews() {}

    static Page tab(ServerPlayer player, Colony c) {
        Production.Plan plan = Production.plan(player.server, c);
        Map<ResourceLocation, Integer> stock = c.get(WorkComponents.STOCK).orElse(WorkComponents.Stock.EMPTY).items();
        List<Stat> orders = new ArrayList<>();
        List<Stat> targets = new ArrayList<>();
        orders.add(Stat.of(tr("production.orders_help", "Made first, oldest first; done when the count reaches 0"), "",
                Tone.MUTED));
        targets.add(Stat.of(tr("production.targets_help",
                "Trades keep these in stock and rest once every target is met (0 = no target)"), "", Tone.MUTED));
        for (Map.Entry<ResourceLocation, ResourceLocation> e : Production.makeable().entrySet()) {
            ResourceLocation item = e.getKey();
            String label = name(item) + " (" + JobDef.titleOf(e.getValue()).getString() + ")";
            orders.add(CaravanViews.stepper(label, "order:" + item, plan.order(item), 0, MAX));
            int have = stock.getOrDefault(item, 0);
            targets.add(CaravanViews.stepper(label + " - " + have + " in the stock", "target:" + item,
                    plan.targets().getOrDefault(item, 0), 0, MAX));
        }
        List<Stat> wear = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JobDef> e : JobDef.all().entrySet()) {
            e.getValue().tool().ifPresent(t -> {
                double w = plan.wear().getOrDefault(e.getKey(), 0.0);
                wear.add(Stat.of(Component.literal(JobDef.titleOf(e.getKey()).getString() + ": " + name(t)),
                        w >= 1 ? "needs a new one" : Math.round((1 - w) * 100) + "% left", w >= 1 ? Tone.WARN
                                : Tone.NORMAL));
            });
        }
        return new Page.Stats(List.of(new Page.Section(tr("production.orders", "Work orders"), orders),
                new Page.Section(tr("production.targets", "Stock targets"), targets),
                new Page.Section(tr("production.tools", "Tools"), wear)), List.of());
    }

    static String name(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id).getDescription().getString();
    }

    static Optional<ViewProvider.Result> act(ServerPlayer player, Colony c, String action, int value) {
        boolean order = action.startsWith("order:");
        if (!order && !action.startsWith("target:")) {
            return Optional.empty();
        }
        if (!Permissions.can(player.getUUID(), c, Perm.PRODUCTION)) {
            return Optional.of(ViewProvider.Result.say(tr("production.no_perm",
                    "Only those who may direct production can")));
        }
        ResourceLocation item = ResourceLocation.tryParse(action.substring(action.indexOf(':') + 1));
        if (item != null) {
            int v = Math.max(0, Math.min(MAX, value));
            if (order) {
                Production.setOrder(player.server, c, item, v);
            } else {
                Production.setTarget(player.server, c, item, v);
            }
        }
        return Optional.of(ViewProvider.Result.NONE);
    }
}
