package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.ColonyBlocks;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.ui.BuildingViews;
import com.porcupinestudios.lotrport.colony.ui.ColonyViews;
import com.porcupinestudios.lotrport.colony.ui.LedgerViews;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonyScheduler;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.Views;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The colony's furniture screens (2026-11-17): plaques find their building; the Treasury Ledger, Town Hall Ledger,
 * Customs Counter and building page build every tab; a pay day writes the books.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuiTests {
    private GuiTests() {}

    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 200)
    public static void furnitureScreensBuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int x0 = 8_500_000 + level.random.nextInt(10_000) * 512;
        int z0 = 7_000_000;
        int y0 = 150;
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + 32) >> 4; cx++) {
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + 16) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Gui Test", new BlockPos(x0 + 20, y0, z0 - 8), LotrPort.id("hobbit"))
                .colony().orElseThrow();
        try {
            LifeTests.room(level, x0, y0, z0, Blocks.CHEST);
            level.setBlock(new BlockPos(x0 + 4, y0 + 1, z0 + 2), Blocks.FURNACE.defaultBlockState(), 2);
            Building kitchen = LifeTests.add(helper, player, level, x0, y0, z0, Optional.of(LotrPort.id("kitchen")));
            LifeTests.citizen(Persons.get(server), colony, LotrPort.id("gondor_man"));
            colonies.set(colony, CoreComponents.TREASURY, new CoreComponents.Treasury(100));

            // a plaque inside the room, and one outside by the door, both name the kitchen
            Buildings all = Buildings.of(colonies.get(colony.id()).orElseThrow());
            Optional<Building> inside = ColonyBlocks.plaqueBuilding(all, new BlockPos(x0 + 5, y0 + 2, z0 + 3),
                    Direction.WEST);
            Optional<Building> byDoor = ColonyBlocks.plaqueBuilding(all, new BlockPos(x0 - 1, y0 + 2, z0 + 2),
                    Direction.WEST);
            helper.assertTrue(inside.map(b -> b.id().equals(kitchen.id())).orElse(false), "plaque inside: " + inside);
            helper.assertTrue(byDoor.map(b -> b.id().equals(kitchen.id())).orElse(false), "plaque by door: " + byDoor);
            helper.assertTrue(ColonyBlocks.plaqueBuilding(all, new BlockPos(x0 + 40, y0, z0), Direction.WEST)
                    .isEmpty(), "a plaque far away names nothing");

            ColonyScheduler.get(server).runDay(server, colonies.get(colony.id()).orElseThrow(), 1);
            Colony now = colonies.get(colony.id()).orElseThrow();
            helper.assertTrue(!now.get(WorkComponents.BOOKS).orElse(WorkComponents.Books.EMPTY).days().isEmpty(),
                    "the pay day is in the books");

            String id = now.id().toString();
            screens(helper, player, LedgerViews.TREASURY, id, "books", "accounts", "payroll");
            screens(helper, player, ColonyViews.HALL_LEDGER, id, "register", "overview", "buildings", "people",
                    "members");
            screens(helper, player, ColonyViews.CUSTOMS_COUNTER, id, "immigration", "customs");
            screens(helper, player, BuildingViews.ID, id + "/" + kitchen.id(), "page", "people", "work");
            books(helper, player, com.porcupinestudios.lotrport.colony.book.TreasuryBook.KIND, id, "accounts",
                    "history", "taxes", "production", "stores");
            books(helper, player, com.porcupinestudios.lotrport.colony.book.HallLedgerBook.KIND, id, "people",
                    "people:idle", "buildings", "council", "research", "members", "war");
            books(helper, player, com.porcupinestudios.lotrport.colony.book.CustomsBook.KIND, id, "", "levels");
            books(helper, player, com.porcupinestudios.lotrport.colony.book.PlaqueBook.KIND,
                    com.porcupinestudios.lotrport.colony.book.PlaqueBook.context(now,
                            Buildings.of(now).get(kitchen.id()).orElseThrow()), "", "work", "goods", "stores", "room");
            helper.succeed();
        } finally {
            Society.citizens(server, colony).forEach(p -> Persons.get(server).remove(server, p.id()));
            colonies.disband(server, colony, "test");
            BuildingTests.fill(level, x0 - 1, y0, z0 - 1, x0 + 8, y0 + 6, z0 + 8, Blocks.AIR);
        }
    }

    /** Every book tab builds and survives the trip to the client (NBT out and back). */
    private static void books(GameTestHelper helper, ServerPlayer player, String kind, String context,
            String... tabs) {
        for (String tab : tabs) {
            var book = com.porcupinestudios.lotrport.colony.book.Books.provider(kind).orElseThrow()
                    .build(player, context, tab);
            helper.assertTrue(book.isPresent(), kind + " " + tab + " builds");
            var back = com.porcupinestudios.lotrport.colony.book.Book.load(book.get().save());
            helper.assertTrue(back.right().size() == book.get().right().size() && back.title().equals(
                    book.get().title()), kind + " " + tab + " survives the network");
        }
    }

    /** Every tab builds, opens on itself, and the view's tab list matches. */
    private static void screens(GameTestHelper helper, ServerPlayer player, ResourceLocation view, String context,
            String... tabs) {
        for (String tab : tabs) {
            Optional<View> v = Views.provider(view).orElseThrow().build(player, context, tab);
            helper.assertTrue(v.isPresent(), view + " " + tab + " builds");
            helper.assertTrue(v.get().tab().equals(tab), view + " opens " + tab + ", not " + v.get().tab());
            helper.assertTrue(v.get().tabs().size() == tabs.length, view + " has " + v.get().tabs().size() + " tabs");
        }
    }
}
