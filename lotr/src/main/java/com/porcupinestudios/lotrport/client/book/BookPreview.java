package com.porcupinestudios.lotrport.client.book;

import com.porcupinestudios.lotrport.colony.book.Book;
import com.porcupinestudios.lotrport.colony.book.El;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev only: with {@code -Dlotr_port.bookpreview=true} the client opens sample colony books over the title screen,
 * screenshots each ({@code screenshots/book_<n>.png}) and quits. For checking the book screen's look headless (the
 * cloud sandbox), with no world or server. The samples use the same element kinds the real books send.
 */
public final class BookPreview {
    private static List<Book> books;
    private static int at = -1;
    private static int wait;

    private BookPreview() {}

    public static void init() {
        if (!Boolean.getBoolean("lotr_port.bookpreview")) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (books == null) {
            if (!(mc.screen instanceof TitleScreen)) {
                return;
            }
            books = samples();
            wait = 40;
        }
        if (--wait > 0) {
            return;
        }
        if (at >= 0) {
            Screenshot.grab(mc.gameDirectory, "book_" + at + ".png", mc.getMainRenderTarget(), m -> { });
        }
        at++;
        if (at >= books.size()) {
            mc.stop();
            return;
        }
        mc.setScreen(new BookScreen(books.get(at)));
        wait = 15;
    }

    private static final String HOBBIT = "lotr_port:textures/entity/hobbit/hobbit_male/%d.png|64";
    private static final String SOLDIER = "lotr_port:textures/entity/gondor/gondor_soldier/%d.png|64";

    static List<Book> samples() {
        List<Book> out = new ArrayList<>();
        List<El> hallLeft = List.of(El.pair("Ruler", "Aaron"), El.pair("People of", "Hobbits of the Shire"),
                El.pair("Founded", "day 12", El.MUTED), El.gap(),
                El.meter("Happiness", "64 / 100", 0.64, 0.25), El.meter("Food", "15 / 20", 0.75),
                El.meter("Housed", "11 of 12", 11 / 12.0), El.meter("Employed", "9 of 12", 0.75),
                El.meter("Unity", "48 / 100", 0.48, 0.5), El.gap(), El.pair("Buildings", "9"),
                El.pair("Soldiers", "2"), El.gap(),
                El.buttons(List.of(El.button("Treasury", "goto", "treasury"), El.button("Customs", "goto",
                        "customs"))));
        List<El> chips = List.of(El.chip("minecraft:player_head", "12", "Citizens"),
                El.chip("minecraft:red_bed", "14", "Beds"), El.chip("minecraft:gold_ingot", "340", "Coins"));
        List<Book.Tab> hallTabs = List.of(new Book.Tab("people", "People", "minecraft:player_head"),
                new Book.Tab("buildings", "Buildings", "minecraft:bricks"),
                new Book.Tab("council", "Council", "minecraft:bell"),
                new Book.Tab("research", "Research", "minecraft:enchanted_book"),
                new Book.Tab("members", "Members", "minecraft:name_tag"),
                new Book.Tab("war", "War", "minecraft:iron_sword"));
        List<El> people = new ArrayList<>();
        people.add(El.buttons(List.of(El.button("All 12", "filter", "all", false, ""), El.button("Homeless 1",
                "filter", "homeless"), El.button("Idle 3", "filter", "idle"), El.button("Unhappy 2", "filter",
                "unhappy"))));
        String[][] who = {{"Bilbo Proudfoot", "Hobbit, 44 - Farmer at the Bywater fields"},
            {"Rosie Cotton", "Hobbit, 31 - Brewer at the Green Dragon"},
            {"Hamfast Gamgee", "Hobbit, 62 - no work"}, {"Tom Brandybuck", "Hobbit, 27 - Miller"},
            {"Lily Burrows", "Hobbit, 36 - Baker"}};
        for (int i = 0; i < who.length; i++) {
            people.add(El.card(String.format(HOBBIT, i), who[i][0], who[i][1], i == 2 ? El.WARN : El.NORMAL,
                    "citizen", "x", List.of(El.meter("Mood", (80 - i * 13) + "", (80 - i * 13) / 100.0),
                            El.meter("Food", (18 - i * 3) + "", (18 - i * 3) / 20.0))));
        }
        out.add(new Book("hall_ledger", "c", "Bywater", "Town Hall ledger", chips, hallTabs, "people", hallLeft,
                people, "", 1));

        List<El> plaqueLeft = List.of(El.pair("Bakery", "").withItem("minecraft:bread"),
                El.meter("Quality", "72 / 100", 0.72), El.pair("Floor", "planks"), El.pair("Storeys", "1"),
                El.meter("Bakers", "2 of 3", 2 / 3.0), El.pair("Wage", "4 coins a day"),
                El.pair("Door", "112, 64, -340", El.MUTED), El.pair("Built", "day 14", El.MUTED));
        List<Book.Tab> plaqueTabs = List.of(new Book.Tab("work", "Work", "minecraft:iron_pickaxe"),
                new Book.Tab("goods", "Goods", "minecraft:crafting_table"),
                new Book.Tab("stores", "Stores", "minecraft:chest"),
                new Book.Tab("room", "Room", "minecraft:oak_door"));
        List<El> work = new ArrayList<>();
        work.add(El.head("Bakers"));
        work.add(El.card(String.format(HOBBIT, 4), "Lily Burrows", "Hobbit - skill 34", El.NORMAL, "citizen", "x",
                List.of(El.meter("Skill", "34", 0.34), El.meter("Mood", "71", 0.71))));
        work.add(El.card(String.format(HOBBIT, 7), "Odo Brockhouse", "Hobbit - skill 12, unpaid 1 day", El.BAD,
                "citizen", "x", List.of(El.meter("Skill", "12", 0.12), El.meter("Mood", "38", 0.38))));
        work.add(El.head("Made yesterday"));
        work.add(El.items(List.of(El.stack("minecraft:bread", 14), El.stack("minecraft:cake", 1),
                El.stack("minecraft:cookie", 22), El.stack("minecraft:pumpkin_pie", 3))));
        work.add(El.head("Tools"));
        work.add(El.meter("Rolling pin", "worn 62%", 0.38).withItem("minecraft:stick"));
        work.add(El.head("Goods"));
        work.add(El.pair("Bread", "").withItem("minecraft:bread"));
        work.add(El.buttons(List.of(El.stepper("Order", "8", "order", "minecraft:bread", 8, 0, 4096, 1, true, ""),
                El.stepper("Keep", "32", "target", "minecraft:bread", 32, 0, 4096, 1, true, ""))));
        out.add(new Book("plaque", "c/b", "The Old Bakehouse", "Bywater - Bakery",
                List.of(El.chip("minecraft:bread", "14", "Made yesterday"), El.chip("minecraft:player_head", "2/3",
                        "Workers")), plaqueTabs, "work", plaqueLeft, work, "", 0));

        List<El> trLeft = List.of(El.pair("In the coffers", "340 coins", El.GOOD).withItem("minecraft:gold_ingot"),
                El.gap(), El.pair("Comes in a day", "+36", El.GOOD), El.pair("Goes out a day", "-41", El.WARN),
                El.pair("Each day", "-5", El.BAD), El.meter("Lasts", "68 days", 1), El.gap(),
                El.buttons(List.of(El.button("Town Hall", "goto", "x"), El.button("Market", "market", ""))));
        List<El> hist = new ArrayList<>();
        hist.add(El.head("The last 30 pay days"));
        for (int d = 0; d < 8; d++) {
            int net = d % 3 == 0 ? -5 : 7;
            hist.add(El.meter("Day " + (40 - d) + "   +36 / -" + (36 - net), (340 - d * 6) + " coins (" + (net >= 0
                    ? "+" : "") + net + ")", (340 - d * 6) / 380.0).withTone(net >= 0 ? El.GOOD : El.BAD));
        }
        out.add(new Book("treasury", "c", "Bywater", "Treasury ledger", chips, List.of(
                new Book.Tab("accounts", "Accounts", "minecraft:writable_book"),
                new Book.Tab("history", "Pay days", "minecraft:clock"),
                new Book.Tab("taxes", "Taxes", "minecraft:gold_nugget"),
                new Book.Tab("production", "Production", "minecraft:crafting_table"),
                new Book.Tab("stores", "Stores", "minecraft:chest")), "history", trLeft, hist, "Head tax set to 3",
                2));

        List<El> cuLeft = List.of(El.pair("Standing", "Village (level 2)"), El.pair("Places on the board", "3"),
                El.pair("Biggest group", "4"), El.meter("Skill of arrivals", "average", 0.5),
                El.pair("Free beds", "2", El.WARN), El.pair("New faces in", "12 min", El.MUTED));
        List<El> arr = new ArrayList<>();
        arr.add(El.head("A family of three from Gondor"));
        arr.add(El.card(String.format(SOLDIER, 0), "Beregond", "Man of Gondor, 38 - father", El.NORMAL, "", "",
                List.of(El.meter("Farming", "41", 0.41), El.meter("Building", "22", 0.22),
                        El.buttons(List.of(El.tag("Hard-working", El.GOOD), El.tag("Proud", El.WARN))))));
        arr.add(El.card(String.format(SOLDIER, 1), "Ioreth", "Woman of Gondor, 35 - mother", El.NORMAL, "", "",
                List.of(El.meter("Healing", "64", 0.64), El.buttons(List.of(El.tag("Kind", El.GOOD))))));
        arr.add(El.buttons(List.of(El.button("Welcome", "accept", "0"), El.button("Turn away", "decline", "0"))));
        arr.add(El.head("Alone"));
        arr.add(El.card(String.format(HOBBIT, 9), "Fosco Tunnelly", "Hobbit, 22", El.NORMAL, "", "",
                List.of(El.meter("Mining", "18", 0.18))));
        arr.add(El.buttons(List.of(El.button("Welcome", "accept", "1", false, "No free bed"), El.button("Turn away",
                "decline", "1"))));
        out.add(new Book("customs", "c", "Bywater", "Customs Counter", chips, List.of(
                new Book.Tab("arrivals", "Arrivals", "minecraft:oak_boat"),
                new Book.Tab("levels", "Levels", "minecraft:experience_bottle")), "arrivals", cuLeft, arr, "", 3));
        return out;
    }
}
