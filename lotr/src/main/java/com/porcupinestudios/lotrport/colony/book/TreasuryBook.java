package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.people.Council;
import com.porcupinestudios.lotrport.colony.people.EdictDef;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.LedgerNet;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Treasury ledger (RingRealms' Treasury book): the coffers on the left page (coins, what comes in and goes out a
 * day, how long the reserve lasts); Accounts (each wage bill by trade, taxes, soldiers, edicts), Taxes (head tax and
 * wage bonus, {@link Perm#TREASURY}), Production (orders and stock targets for every good, {@link Perm#PRODUCTION})
 * and Stores (what the colony holds). Context: the colony id.
 */
public final class TreasuryBook implements BookProvider {
    public static final String KIND = "treasury";
    static final int GRID_MAX = 64;

    private TreasuryBook() {}

    static void register() {
        Books.register(KIND, new TreasuryBook());
    }

    /** One day's money, as the colony runs now. */
    record Accounts(Map<ResourceLocation, long[]> wages, long taxes, long soldiers, long edicts, int taxed) {
        long wageTotal() {
            return wages.values().stream().mapToLong(a -> a[1]).sum();
        }

        long net() {
            return taxes - wageTotal() - soldiers - edicts;
        }
    }

    static Accounts accounts(MinecraftServer server, Colony c) {
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        Map<ResourceLocation, long[]> wages = new TreeMap<>();
        long soldiers = 0;
        int taxed = 0;
        for (Person p : Society.citizens(server, c)) {
            if (p.has(CoreComponents.UNIT)) {
                soldiers += p.get(CoreComponents.UNIT).map(CoreComponents.Unit::wage).orElse(0);
                continue;
            }
            taxed++;
            p.get(WorkComponents.JOB).ifPresent(j -> {
                int w = JobDef.get(j.job()).map(JobDef::wage).orElse(0) + eco.wageBonus();
                long[] a = wages.computeIfAbsent(j.job(), k -> new long[2]);
                a[0]++;
                a[1] += Math.max(0, w);
            });
        }
        long edicts = 0;
        for (ResourceLocation id : Council.state(server, c).edicts()) {
            edicts += EdictDef.get(id).map(EdictDef::upkeep).orElse(0);
        }
        return new Accounts(wages, (long) taxed * eco.headTax(), soldiers, edicts, taxed);
    }

    @Override
    public Optional<Book> build(ServerPlayer player, String context, String tab) {
        Optional<Colony> found = Pages.colony(player.server, context);
        if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
            return Optional.empty();
        }
        MinecraftServer server = player.server;
        Colony c = found.get();
        long coins = c.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
        Accounts a = accounts(server, c);
        List<Book.Tab> tabs = List.of(
                new Book.Tab("accounts", "Accounts", "minecraft:writable_book"),
                new Book.Tab("taxes", "Taxes", "minecraft:gold_nugget"),
                new Book.Tab("production", "Production", "minecraft:crafting_table"),
                new Book.Tab("stores", "Stores", "minecraft:chest"));
        String open = tabs.stream().anyMatch(t -> t.id().equals(tab)) ? tab : "accounts";

        List<El> left = new ArrayList<>();
        left.add(El.pair("In the coffers", Pages.coins(coins), coins > 0 ? El.GOOD : El.BAD)
                .withItem("minecraft:gold_ingot"));
        left.add(El.gap());
        left.add(El.pair("Comes in a day", "+" + a.taxes(), El.GOOD));
        left.add(El.pair("Goes out a day", "-" + (a.wageTotal() + a.soldiers() + a.edicts()), El.WARN));
        long net = a.net();
        left.add(El.pair("Each day", (net >= 0 ? "+" : "") + net, net >= 0 ? El.GOOD : El.BAD));
        if (net < 0) {
            long days = coins / Math.max(1, -net);
            left.add(El.meter("Lasts", days + " days", Math.min(1, days / 30.0))
                    .withTip("When the coffers run dry, workers strike and edicts lapse"));
        }
        left.add(El.gap());
        left.add(El.buttons(List.of(El.button("Town Hall", "goto", HallLedgerBook.KIND),
                El.button("Market", "market", ""))));

        List<El> right = switch (open) {
            case "taxes" -> taxes(player, c, a);
            case "production" -> production(player, c);
            case "stores" -> stores(server, c);
            default -> accounts(a);
        };
        List<El> chips = List.of(El.chip("minecraft:gold_nugget", Long.toString(coins), "Treasury"),
                El.chip(net >= 0 ? "minecraft:emerald" : "minecraft:redstone", (net >= 0 ? "+" : "") + net,
                        "Each day"));
        return Optional.of(new Book(KIND, context, c.name(), "Treasury ledger", chips, tabs, open, left, right, "", 2));
    }

    static List<El> accounts(Accounts a) {
        List<El> out = new ArrayList<>();
        out.add(El.head("Income a day"));
        out.add(El.pair("Head tax (" + a.taxed() + " citizens)", "+" + a.taxes(), El.GOOD)
                .withItem("minecraft:gold_nugget"));
        out.add(El.head("Wages a day"));
        if (a.wages().isEmpty()) {
            out.add(El.text("Nobody is on the payroll."));
        }
        a.wages().forEach((job, v) -> out.add(El.pair(Pages.job(job) + " x" + v[0], "-" + v[1])));
        out.add(El.pair("All wages", "-" + a.wageTotal(), El.WARN));
        out.add(El.head("Other costs a day"));
        out.add(El.pair("Soldiers", "-" + a.soldiers()).withItem("minecraft:iron_sword"));
        out.add(El.pair("Edicts", "-" + a.edicts()).withItem("minecraft:paper"));
        out.add(El.text("Tavern drinks, market trade and caravans change the coffers too."));
        return out;
    }

    static List<El> taxes(ServerPlayer player, Colony c, Accounts a) {
        boolean may = Permissions.can(player.getUUID(), c, Perm.TREASURY);
        String why = may ? "" : "Only those who may keep the treasury can";
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        List<El> out = new ArrayList<>();
        out.add(El.stepper("Head tax", Pages.coins(eco.headTax()) + " a day", "tax", "", eco.headTax(), 0,
                LedgerNet.MAX_TAX, 1, may, why));
        out.add(El.text("Every citizen pays it from their purse each day. Above 1 coin it makes them unhappier."));
        out.add(El.stepper("Wage bonus", (eco.wageBonus() >= 0 ? "+" : "") + eco.wageBonus() + " a day", "bonus", "",
                eco.wageBonus(), LedgerNet.MIN_BONUS, LedgerNet.MAX_BONUS, 1, may, why));
        out.add(El.text("Added to every worker's wage. Workers left unpaid go on strike."));
        return out;
    }

    static List<El> production(ServerPlayer player, Colony c) {
        boolean may = Permissions.can(player.getUUID(), c, Perm.PRODUCTION);
        String why = may ? "" : "Only those who may direct production can";
        Production.Plan plan = Production.plan(player.server, c);
        List<El> out = new ArrayList<>();
        out.add(El.text("Order: made first, then the order ends. Keep: trades keep this many in stock and rest once "
                + "every target is met. Tools keep one spare per worker by themselves."));
        Map<ResourceLocation, List<ResourceLocation>> byJob = new LinkedHashMap<>();
        Production.makeable().forEach((item, job) -> byJob.computeIfAbsent(job, k -> new ArrayList<>()).add(item));
        byJob.forEach((job, items) -> {
            out.add(El.head(Pages.job(job)));
            for (ResourceLocation item : items) {
                int order = plan.order(item);
                int target = plan.targets().getOrDefault(item, 0);
                out.add(El.pair(Pages.item(item), "").withItem(item.toString()));
                out.add(El.buttons(List.of(
                        El.stepper("Order", order == 0 ? "-" : Integer.toString(order), "order", item.toString(),
                                order, 0, 4096, 1, may, why),
                        El.stepper("Keep", target == 0 ? "-" : Integer.toString(target), "target", item.toString(),
                                target, 0, 4096, 1, may, why))));
            }
        });
        return out;
    }

    static List<El> stores(MinecraftServer server, Colony c) {
        Stores stores = new Stores(server, server.getLevel(c.dimension()), c);
        Map<ResourceLocation, Integer> held = stores.items(null);
        List<El> out = new ArrayList<>();
        if (held.isEmpty()) {
            out.add(El.text("The storehouses are empty (or not loaded)."));
            return out;
        }
        List<El> grid = new ArrayList<>();
        held.entrySet().stream().sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed())
                .limit(GRID_MAX).forEach(e -> grid.add(El.stack(e.getKey().toString(), e.getValue())));
        out.add(El.head("In the storehouses"));
        out.add(El.items(grid));
        if (held.size() > GRID_MAX) {
            out.add(El.text("and " + (held.size() - GRID_MAX) + " more kinds"));
        }
        return out;
    }

    @Override
    public Result act(ServerPlayer player, String context, String tab, String action, String arg, int num) {
        Optional<Colony> found = Pages.colony(player.server, context);
        if (found.isEmpty()) {
            return Result.NONE;
        }
        Colony c = found.get();
        switch (action) {
            case "goto" -> {
                return Result.go(arg, context, "");
            }
            case "market" -> {
                com.porcupinestudios.lotrport.ui.Views.open(player, new com.porcupinestudios.lotrport.ui.View.Target(
                        com.porcupinestudios.lotrport.colony.ui.MarketViews.ID, c.id().toString(), "buy"), true);
                return Result.NONE;
            }
            case "tax", "bonus" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.TREASURY)) {
                    return Result.say("Only those who may keep the treasury can");
                }
                WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
                WorkComponents.Economy e = action.equals("tax")
                        ? new WorkComponents.Economy(Math.max(0, Math.min(LedgerNet.MAX_TAX, num)), eco.wageBonus())
                        : new WorkComponents.Economy(eco.headTax(), Math.max(LedgerNet.MIN_BONUS,
                                Math.min(LedgerNet.MAX_BONUS, num)));
                Colonies.get(player.server).set(c, WorkComponents.ECONOMY, e);
                return Result.NONE;
            }
            case "order", "target" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.PRODUCTION)) {
                    return Result.say("Only those who may direct production can");
                }
                ResourceLocation item = ResourceLocation.tryParse(arg);
                if (item != null) {
                    int v = Math.max(0, Math.min(4096, num));
                    if (action.equals("order")) {
                        Production.setOrder(player.server, c, item, v);
                    } else {
                        Production.setTarget(player.server, c, item, v);
                    }
                }
                return Result.NONE;
            }
            default -> {
                return Result.NONE;
            }
        }
    }
}
