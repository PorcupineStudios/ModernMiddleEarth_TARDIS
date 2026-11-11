package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.Bodies;
import com.porcupinestudios.lotrport.colony.work.LedgerNet;
import com.porcupinestudios.lotrport.colony.work.LifeSettings;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Bodies on demand: a colony of 45 citizens near a player has at most {@code bodies_max} walking about, brought out a
 * few a pulse with their own ids and names; extra bodies go back inside. The Ledger and citizen pages survive the
 * network codec.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyTests {
    private BodyTests() {}

    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 200)
    public static void bodiesComeOutUpToTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos hall = helper.absolutePos(new BlockPos(0, 1, 0));
        for (BlockPos p : BlockPos.betweenClosed(hall.offset(-3, -1, -3), hall.offset(3, -1, 3))) {
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Body Test", hall, LotrPort.id("gondor")).colony().orElse(null);
        if (colony == null) {
            helper.succeed(); // another colony's Hall is close in the shared test world: nothing to measure here
            return;
        }
        Persons persons = Persons.get(server);
        List<UUID> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 45; i++) {
                Person p = persons.migrated(UUID.randomUUID(), LotrPort.id("gondor_man"), "Body " + i, 0,
                        colony.asOwner());
                persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(0, false));
                ids.add(p.id());
            }
            int max = LifeSettings.get().bodiesMax();
            for (int pulse = 0; pulse < 20; pulse++) {
                Bodies.run(new SystemContext(server, colony, level, Tier.ACTIVE, level.getGameTime(), 0));
            }
            long out = ids.stream().filter(id -> level.getEntity(id) instanceof LotrNpc).count();
            helper.assertTrue(out == Math.min(45, max), "bodies out: " + out + ", cap " + max);
            LotrNpc one = (LotrNpc) ids.stream().map(level::getEntity).filter(e -> e instanceof LotrNpc).findFirst()
                    .orElseThrow();
            helper.assertTrue(one.personName().startsWith("Body ") && Colonies.colonyOf(one).isPresent(),
                    "a body isn't its citizen: " + one.personName());
            // force extra bodies out: the next pulse sends them back inside
            for (UUID id : ids) {
                if (level.getEntity(id) == null) {
                    LotrNpc npc = LotrNpc.bodyFor(level, persons.get(id).orElseThrow()).orElseThrow();
                    npc.moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
                    level.addFreshEntity(npc);
                }
            }
            SystemContext back = new SystemContext(server, colony, level, Tier.ACTIVE, level.getGameTime(), 0);
            Bodies.run(back);
            long after = ids.stream().filter(id -> level.getEntity(id) instanceof LotrNpc n && !n.isRemoved()).count();
            helper.assertTrue(after == Math.min(45, max) && back.counted(Bodies.INSIDE) == 45 - Math.min(45, max),
                    "after the cap: " + after + " out, " + back.counted(Bodies.INSIDE) + " sent in");
            helper.assertTrue(Society.citizenCount(server, colony) == 45, "records were lost with their bodies");

            // the Ledger's and the citizen page's codecs
            var row = new LedgerNet.Row("House", "2/2 living · floor 20", 44, "1 2 3");
            var ledger = new LedgerNet.Ledger(colony.id(), "Body Test", 45, 61, 14, 120, 2, -1, true, List.of(row),
                    List.of(row, row), List.of(), true);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            LedgerNet.Ledger.CODEC.encode(buf, ledger);
            helper.assertTrue(LedgerNet.Ledger.CODEC.decode(buf).equals(ledger), "the Ledger changed on the way");
            var page = new LedgerNet.CitizenView(UUID.randomUUID(), "Bob", "Body Test", "hobbit", "Farmer", "House",
                    14, 61, 30, 7, 0);
            LedgerNet.CitizenView.CODEC.encode(buf, page);
            helper.assertTrue(LedgerNet.CitizenView.CODEC.decode(buf).equals(page), "the citizen page changed");
            helper.succeed();
        } finally {
            for (UUID id : ids) {
                var e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
                persons.remove(server, id);
            }
            colonies.disband(server, colony, "test");
        }
    }
}
