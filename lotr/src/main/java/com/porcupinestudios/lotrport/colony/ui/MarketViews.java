package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.market.Market;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.TableBuilder;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The colony market screen (context: colony id): Buy (from the stores, never below the caravan floors), Sell (from
 * your inventory, within each item's daily limit), Limits (research points, the market's level, and each item's
 * daily limit; {@link Perm#MARKET}). Anyone may trade; right-clicking a locked colony store opens it.
 */
public final class MarketViews {
    public static final ResourceLocation ID = LotrPort.id("market");

    private MarketViews() {}

    public static void register() {
        Views.register(ID, new Provider());
    }

    public static void open(ServerPlayer player, Colony c) {
        Views.open(player, new View.Target(ID, c.id().toString(), "buy"), true);
    }

    static String name(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }

    static Map<ResourceLocation, Integer> carried(ServerPlayer player) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && s.getComponentsPatch().isEmpty()
                    && com.porcupinestudios.lotrport.npc.Coins.value(s) == 0) {
                out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    static final class Provider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Colony> found = ColonyViews.colony(player.server, context);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            Colony c = found.get();
            MinecraftServer server = player.server;
            List<View.Tab> tabs = List.of(new View.Tab("buy", tr("market.tab.buy", "Buy")),
                    new View.Tab("sell", tr("market.tab.sell", "Sell")),
                    new View.Tab("limits", tr("market.tab.limits", "Limits")),
                    new View.Tab("research", tr("market.tab.research", "Research")));
            String open = tab.equals("sell") || tab.equals("limits") || tab.equals("research") ? tab : "buy";
            Map<ResourceLocation, Integer> have = CaravanViews.items(server, c);
            Market.State st = Market.state(server, c);
            Page page;
            if (open.equals("buy")) {
                List<Map.Entry<ResourceLocation, Integer>> rows = new ArrayList<>(have.entrySet());
                page = TableBuilder.of(rows, (Map.Entry<ResourceLocation, Integer> e) -> e.getKey().toString())
                        .column(Column.text("item", tr("col.item", "Item"), 4), e -> Cell.text(name(e.getKey())))
                        .column(Column.number("stock", tr("market.col.stock", "In stores"), 2),
                                e -> Cell.number(e.getValue()))
                        .column(Column.number("price", tr("market.col.price", "Price"), 2), e -> Cell.number(
                                Market.buyCost(e.getKey(), e.getValue(), 1)))
                        .rowAction(Action.of("buy1", tr("market.buy1", "Buy 1")))
                        .rowAction(Action.of("buy16", tr("market.buy16", "Buy 16")))
                        .sortBy("stock", true)
                        .empty(tr("market.empty", "The colony's stores are empty"))
                        .build();
            } else if (open.equals("sell")) {
                List<Map.Entry<ResourceLocation, Integer>> rows = new ArrayList<>(carried(player).entrySet());
                long owed = st.accounts().getOrDefault(player.getUUID(), 0L);
                page = TableBuilder.of(rows, (Map.Entry<ResourceLocation, Integer> e) -> e.getKey().toString())
                        .column(Column.text("item", tr("col.item", "Item"), 4), e -> Cell.text(name(e.getKey())))
                        .column(Column.number("carry", tr("market.col.carry", "You carry"), 2),
                                e -> Cell.number(e.getValue()))
                        .column(Column.number("pays", tr("market.col.pays", "Pays"), 2), e -> Cell.number(
                                Market.sellPay(e.getKey(), have.getOrDefault(e.getKey(), 0), 1)))
                        .column(Column.text("today", tr("market.col.today", "Takes today"), 2), e -> {
                            int room = Market.room(st, e.getKey());
                            return Cell.text(room + " / " + Market.limit(st, e.getKey()), room > 0 ? Tone.NORMAL
                                    : Tone.BAD);
                        })
                        .rowAction(Action.of("sell1", tr("market.sell1", "Sell 1")))
                        .rowAction(Action.of("sellall", tr("market.sellall", "Sell all")))
                        .button(owed > 0 ? Action.of("collect", Component.literal("Collect " + owed + " coins"))
                                : Action.disabled("collect", tr("market.collect", "Collect"), Component.literal(
                                        "Barrels in a market are delivery hatches: what hoppers put in is sold here")))
                        .empty(tr("market.nothing", "You carry nothing the colony would take"))
                        .build();
            } else if (open.equals("limits")) {
                page = limits(player, c, st, have);
            } else {
                page = research(player, c);
            }
            return Optional.of(new View(ID, context, Component.literal(c.name() + ": market"), tabs, open, page,
                    Optional.of(new View.Target(ColonyViews.COLONY, c.id().toString(), "stock"))));
        }

        private Page limits(ServerPlayer player, Colony c, Market.State st, Map<ResourceLocation, Integer> have) {
            MinecraftServer server = player.server;
            boolean may = Permissions.can(player.getUUID(), c, Perm.MARKET);
            int ceiling = Market.ceiling(st.level());
            double cost = Market.upgradeCost(st.level());
            List<Stat> research = new ArrayList<>();
            research.add(Stat.of(Component.literal("Market level"), st.level() + " / " + Market.settings().maxLevel()));
            research.add(Stat.of(Component.literal("Most of one item a day"), Integer.toString(ceiling)));
            ResourceLocation tier = Market.upgradeTier(st.level());
            if (cost >= 0) {
                research.add(Stat.of(Component.literal("Next level costs"), (int) cost + " " + Research.name(tier)
                        + " points (has " + (int) Research.points(server, c, tier) + ")"));
            }
            List<Stat> rows = new ArrayList<>();
            rows.add(Stat.of(Component.literal("The most the colony takes from players a day, per item"), "",
                    Tone.MUTED));
            Set<ResourceLocation> items = new LinkedHashSet<>(st.limits().keySet());
            items.addAll(have.keySet());
            items.addAll(Market.settings().prices().keySet());
            for (ResourceLocation id : items) {
                int lim = Market.limit(st, id);
                int taken = st.taken().getOrDefault(id, 0);
                rows.add(CaravanViews.stepper(name(id) + " (" + taken + " today)", "limit:" + id, lim, 0, ceiling));
            }
            List<Action> buttons = new ArrayList<>();
            Component up = Component.literal(cost < 0 ? "Fully upgraded" : "Upgrade (" + (int) cost + " points)");
            buttons.add(may && cost >= 0 ? Action.of("upgrade", up) : Action.disabled("upgrade", up, may
                    ? Component.literal("The market is as large as it gets")
                    : tr("market.no_perm", "Only those who may run the market can")));
            return new Page.Stats(List.of(new Page.Section(tr("market.level", "Market"), research),
                    new Page.Section(tr("market.limits", "Daily limits"), rows)), buttons);
        }

        private Page research(ServerPlayer player, Colony c) {
            MinecraftServer server = player.server;
            var assigned = Research.assignment(server, c);
            int total = Research.researchers(server, c).size();
            Map<ResourceLocation, Integer> plan = Research.plan(server, c);
            List<Page.Section> sections = new ArrayList<>();
            sections.add(new Page.Section(tr("market.research_how", "Research"), List.of(
                    Stat.of(Component.literal("Researchers working in a library"), Integer.toString(total)),
                    Stat.of(Component.literal("Those not put on a tier do basic research"), "", Tone.MUTED),
                    Stat.of(Component.literal("Higher tiers use goods from the stores"), "", Tone.MUTED))));
            for (Map.Entry<ResourceLocation, Research.TierDef> e : Research.tiers().entrySet()) {
                ResourceLocation id = e.getKey();
                List<Stat> st = new ArrayList<>();
                st.add(Stat.of(Component.literal("Points"), String.format("%.1f", Research.points(server, c, id))));
                int on = assigned.getOrDefault(id, List.of()).size();
                double earn = 0;
                for (var p : assigned.getOrDefault(id, List.of())) {
                    earn += e.getValue().rate() * com.porcupinestudios.lotrport.colonist.Skills.workFactor(p,
                            Research.JOB);
                }
                st.add(Stat.of(Component.literal("Earned a day"), String.format("%.1f", earn), Tone.MUTED));
                st.add(Stat.of(Component.literal("Costs"), Research.costText(id), Tone.MUTED));
                Optional<Component> why = Research.whyNot(server, c, id);
                if (why.isPresent()) {
                    st.add(Stat.of(why.get(), "", Tone.BAD));
                }
                if (id.equals(Research.BASIC)) {
                    st.add(Stat.of(Component.literal("Researchers on it"), Integer.toString(on)));
                } else {
                    st.add(CaravanViews.stepper("Researchers on it (" + on + " working)", "plan:" + id,
                            plan.getOrDefault(id, 0), 0, Math.max(total, plan.getOrDefault(id, 0))));
                }
                sections.add(new Page.Section(Component.literal(Research.name(id)), st));
            }
            return new Page.Stats(sections, List.of());
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<Colony> found = ColonyViews.colony(player.server, context);
            if (found.isEmpty()) {
                return Result.NONE;
            }
            Colony c = found.get();
            ResourceLocation item = ResourceLocation.tryParse(row);
            Market.Answer a = null;
            switch (action) {
                case "buy1", "buy16" -> {
                    if (item != null) {
                        a = Market.buy(player, c, item, action.equals("buy1") ? 1 : 16);
                    }
                }
                case "sell1", "sellall" -> {
                    if (item != null) {
                        a = Market.sell(player, c, item, action.equals("sell1") ? 1 : Integer.MAX_VALUE);
                    }
                }
                case "collect" -> a = Market.collect(player, c);
                case "upgrade" -> {
                    if (!Permissions.can(player.getUUID(), c, Perm.MARKET)) {
                        return Result.say(tr("market.no_perm", "Only those who may run the market can"));
                    }
                    a = Market.upgrade(player.server, c);
                }
                default -> {
                    if (action.startsWith("plan:")) {
                        if (!Permissions.can(player.getUUID(), c, Perm.RESEARCH)) {
                            return Result.say(tr("market.no_research", "Only those who may direct research can"));
                        }
                        ResourceLocation id = ResourceLocation.tryParse(action.substring(5));
                        if (id != null) {
                            Research.setPlan(player.server, c, id, value);
                        }
                    }
                    if (action.startsWith("limit:")) {
                        if (!Permissions.can(player.getUUID(), c, Perm.MARKET)) {
                            return Result.say(tr("market.no_perm", "Only those who may run the market can"));
                        }
                        ResourceLocation id = ResourceLocation.tryParse(action.substring(6));
                        if (id != null) {
                            Market.setLimit(player.server, c, id, value);
                        }
                    }
                }
            }
            return a == null ? Result.NONE : Result.say(a.message());
        }
    }
}
