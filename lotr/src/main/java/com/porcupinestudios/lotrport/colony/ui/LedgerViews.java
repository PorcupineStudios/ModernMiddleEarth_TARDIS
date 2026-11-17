package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.tech.MilestoneDef;
import com.porcupinestudios.lotrport.colony.tech.Techs;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.LedgerNet;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The ledgers (RingRealms' Ledger Stand), opened from their blocks:
 * <ul>
 *   <li>{@code lotr_port:treasury}, the Treasury Ledger: Books (balance, the day's expected takings and pay, head tax
 *       and wage bonus), Accounts (the last days as written in {@link WorkComponents#BOOKS}) and Payroll (who is
 *       paid what).</li>
 *   <li>The Town Hall Ledger ({@link ColonyViews#HALL_LEDGER}): the {@link #REGISTER} tab here plus the colony view's
 *       Overview, Buildings, People and Members.</li>
 * </ul>
 * Context: the colony's id.
 */
public final class LedgerViews {
    public static final ResourceLocation TREASURY = LotrPort.id("treasury");
    static final String REGISTER = "register";
    static final String BOOKS = "books";
    static final String ACCOUNTS = "accounts";
    static final String PAYROLL = "payroll";

    private LedgerViews() {}

    static void register() {
        Views.register(TREASURY, new Treasury());
    }

    // ---------------------------------------------------------------- Town Hall Ledger: Register

    static Page register(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        long dayTicks = Math.max(1, com.porcupinestudios.lotrport.colony.ColonyData.settings().dayTicks());
        long today = server.overworld().getGameTime() / dayTicks;
        long founded = c.founded() / dayTicks;
        List<Stat> charter = List.of(
                Stat.of(tr("register.name", "Name"), c.name()),
                Stat.of(tr("register.founder", "Founded by"), ColonyViews.playerName(server, c.founder())),
                Stat.of(tr("register.faction", "Allegiance"), Component.translatable("faction."
                        + c.faction().getNamespace() + "." + c.faction().getPath()).getString()),
                Stat.of(tr("register.founded", "Founded"), "day " + founded + " (" + Math.max(0, today - founded)
                        + " days ago)", Tone.MUTED),
                Stat.of(tr("register.hall", "Town Hall"), c.hall().toShortString(), Tone.MUTED));
        Techs.State st = Techs.state(server, c);
        List<Stat> deeds = new ArrayList<>();
        for (Map.Entry<ResourceLocation, MilestoneDef> m : MilestoneDef.all().entrySet()) {
            boolean done = st.milestones().containsKey(m.getKey());
            deeds.add(Stat.of(Component.literal(m.getValue().title(m.getKey())), done ? "achieved" : "not yet",
                    done ? Tone.GOOD : Tone.MUTED));
        }
        if (deeds.isEmpty()) {
            deeds.add(Stat.of(tr("register.no_milestones", "No milestones written"), "", Tone.MUTED));
        }
        List<Stat> learning = List.of(Stat.of(tr("register.techs", "Research completed"),
                Integer.toString(st.techs().size())));
        return new Page.Stats(List.of(
                new Page.Section(tr("section.charter", "Charter"), charter),
                new Page.Section(tr("section.learning", "Learning"), learning),
                new Page.Section(tr("section.milestones", "Milestones"), deeds)), List.of());
    }

    // ---------------------------------------------------------------- Treasury Ledger

    static final class Treasury implements ViewProvider {
        private static final ColonyViews.ColonyProvider COLONY = new ColonyViews.ColonyProvider();

        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Colony> found = ColonyViews.colony(player.server, context);
            if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
                return Optional.empty();
            }
            Colony c = found.get();
            List<View.Tab> tabs = List.of(new View.Tab(BOOKS, tr("tab.books", "Books")),
                    new View.Tab(ACCOUNTS, tr("tab.accounts", "Accounts")),
                    new View.Tab(PAYROLL, tr("tab.payroll", "Payroll")));
            String open = tabs.stream().anyMatch(t -> t.id().equals(tab)) ? tab : BOOKS;
            Page page = switch (open) {
                case ACCOUNTS -> accounts(c);
                case PAYROLL -> payroll(player.server, c);
                default -> books(player, c);
            };
            return Optional.of(new View(TREASURY, context, Component.literal(c.name() + " — ")
                    .append(tr("title.treasury", "Treasury Ledger")), tabs, open, page, Optional.empty()));
        }

        @Override
        public Component denied() {
            return tr("denied", "Only members of a colony may manage it");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            // tax, wage bonus and opening a citizen work as on the colony view
            return COLONY.act(player, context, tab, action, row, value);
        }
    }

    /** What the next pay day should bring: [taxes in, wages out, soldiers' pay out]. */
    static long[] forecast(MinecraftServer server, Colony c) {
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        long taxes = 0;
        long wages = 0;
        for (Person p : Society.citizens(server, c)) {
            if (p.has(CoreComponents.UNIT)) {
                continue;
            }
            long purse = p.get(WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins();
            Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
            int wage = job.map(j -> Math.max(0, JobDef.get(j.job()).map(JobDef::wage).orElse(0) + eco.wageBonus()))
                    .orElse(0);
            wages += wage;
            taxes += Math.min(purse + wage, Math.max(0, eco.headTax()));
        }
        long soldiers = 0;
        for (Person p : Society.soldiers(server, c)) {
            soldiers += Math.max(0, p.get(CoreComponents.UNIT).map(CoreComponents.Unit::wage).orElse(0));
        }
        return new long[] {taxes, wages, soldiers};
    }

    static Page books(ServerPlayer player, Colony c) {
        long coins = c.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(0L);
        long[] f = forecast(player.server, c);
        long net = f[0] - f[1] - f[2];
        List<Stat> purse = new ArrayList<>();
        purse.add(Stat.of(tr("books.balance", "Coins in the treasury"), coins + "c", coins > 0 ? Tone.GOOD : Tone.BAD));
        if (net < 0) {
            long days = coins / -net;
            purse.add(Stat.of(tr("books.runway", "Lasts"), days + (days == 1 ? " day" : " days") + " at this rate",
                    days < 3 ? Tone.BAD : days < 10 ? Tone.WARN : Tone.NORMAL));
        }
        List<Stat> day = List.of(
                Stat.of(tr("books.taxes", "Head tax in"), "+" + f[0] + "c", Tone.GOOD),
                Stat.of(tr("books.wages", "Wages out"), "-" + f[1] + "c", f[1] > 0 ? Tone.WARN : Tone.MUTED),
                Stat.of(tr("books.soldiers", "Soldiers' pay out"), "-" + f[2] + "c", f[2] > 0 ? Tone.WARN : Tone.MUTED),
                Stat.of(tr("books.net", "Each day"), (net >= 0 ? "+" : "") + net + "c", net >= 0 ? Tone.GOOD : Tone.BAD));
        boolean may = Permissions.can(player.getUUID(), c, Perm.TREASURY);
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        Stat tax = Stat.of(tr("head_tax", "Head tax"), eco.headTax() + "c a day");
        Stat bonus = Stat.of(tr("wage_bonus", "Wage bonus"), (eco.wageBonus() >= 0 ? "+" : "") + eco.wageBonus()
                + "c a day");
        List<Stat> policy = new ArrayList<>();
        if (may) {
            policy.add(tax.withStepper(new Stat.Stepper("head_tax", eco.headTax(), 0, LedgerNet.MAX_TAX, 1)));
            policy.add(bonus.withStepper(new Stat.Stepper("wage_bonus", eco.wageBonus(), LedgerNet.MIN_BONUS,
                    LedgerNet.MAX_BONUS, 1)));
            policy.add(Stat.of(tr("books.tax_note", "Higher taxes make citizens less happy"), "", Tone.MUTED));
        } else {
            policy.add(tax);
            policy.add(bonus);
            policy.add(Stat.of(tr("books.no_perm", "Only those with the treasury permission set these"), "",
                    Tone.MUTED));
        }
        return new Page.Stats(List.of(
                new Page.Section(tr("section.purse", "Treasury"), purse),
                new Page.Section(tr("section.day", "Expected each day"), day),
                new Page.Section(tr("section.policy", "Taxes and wages"), policy)), List.of());
    }

    static Page accounts(Colony c) {
        List<WorkComponents.Entry> days = c.get(WorkComponents.BOOKS).orElse(WorkComponents.Books.EMPTY).days();
        return TableBuilder.of(days, e -> Long.toString(e.day()))
                .column(Column.number("day", tr("col.day", "Day"), 2), e -> Cell.number(e.day()))
                .column(Column.number("taxes", tr("col.taxes", "Taxes"), 2),
                        e -> Cell.number(e.taxes(), "+" + e.taxes(), e.taxes() > 0 ? Tone.GOOD : Tone.MUTED))
                .column(Column.number("wages", tr("col.wages", "Wages"), 2),
                        e -> Cell.number(e.wages(), "-" + e.wages(), e.wages() > 0 ? Tone.WARN : Tone.MUTED))
                .column(Column.number("soldiers", tr("col.soldier_pay", "Soldiers"), 2),
                        e -> Cell.number(e.soldiers(), "-" + e.soldiers(), e.soldiers() > 0 ? Tone.WARN : Tone.MUTED))
                .column(Column.number("net", tr("col.net", "Net"), 2), e -> {
                    long n = (long) e.taxes() - e.wages() - e.soldiers();
                    return Cell.number(n, (n >= 0 ? "+" : "") + n, n >= 0 ? Tone.GOOD : Tone.BAD);
                })
                .column(Column.number("balance", tr("col.balance", "Balance"), 3), e -> Cell.number(e.balance()))
                .sortBy("day", true)
                .empty(tr("accounts.none", "Nothing written yet: the books fill in on each pay day"))
                .build();
    }

    static Page payroll(MinecraftServer server, Colony c) {
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        List<Person> paid = new ArrayList<>();
        for (Person p : Society.citizens(server, c)) {
            if (p.has(WorkComponents.JOB) || p.has(CoreComponents.UNIT)) {
                paid.add(p);
            }
        }
        return TableBuilder.of(paid, p -> p.id().toString())
                .column(Column.text("name", tr("col.name", "Name"), 3), p -> Cell.text(ColonyViews.personName(p)))
                .column(Column.text("work", tr("col.work", "Work"), 3), p -> Cell.text(p.get(WorkComponents.JOB)
                        .map(j -> JobDef.titleOf(j.job()).getString()).orElse("Soldier"), Tone.MUTED))
                .column(Column.number("wage", tr("col.wage", "Wage"), 2), p -> {
                    int w = p.get(CoreComponents.UNIT).map(CoreComponents.Unit::wage).orElseGet(() -> p.get(
                            WorkComponents.JOB).map(j -> Math.max(0, JobDef.get(j.job()).map(JobDef::wage).orElse(0)
                            + eco.wageBonus())).orElse(0));
                    return Cell.number(w, w + "c", Tone.NORMAL);
                })
                .column(Column.number("purse", tr("col.purse", "Purse"), 2), p -> Cell.number(p.get(
                        WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins()))
                .column(Column.number("unpaid", tr("col.unpaid", "Unpaid days"), 2), p -> {
                    int u = p.get(CoreComponents.UNIT).map(CoreComponents.Unit::unpaid).orElseGet(() -> p.get(
                            WorkComponents.NEEDS).map(WorkComponents.Needs::unpaid).orElse(0));
                    return Cell.number(u, Integer.toString(u), u > 0 ? Tone.BAD : Tone.MUTED);
                })
                .open("citizen")
                .rowAction(Action.of("citizen", tr("open_citizen", "Citizen page")))
                .sortBy("wage", true)
                .empty(tr("payroll.none", "Nobody draws a wage yet"))
                .build();
    }
}
