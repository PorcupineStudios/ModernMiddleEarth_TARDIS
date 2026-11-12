package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import net.minecraft.server.MinecraftServer;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.colony.Recruiting;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import com.porcupinestudios.ringbearer.api.provider.Providers;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 9 done-when (Stage 3): a colony grows by migrants the owner accepts, and a rare race joins by quest. One test, since colonies need room
 * between their Halls and tests in a batch stand close together.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");
    private static final ResourceLocation RANGERS = LotrPort.id("ranger_north");
    private static final ResourceLocation RANGER_RECRUIT = LotrPort.id("ranger_north");

    private ColonyTests() {}

    @GameTest(template = EMPTY)
    public static void colonyDataLoads(GameTestHelper helper) {
        helper.assertTrue(ColonyData.loadProblems().isEmpty(), "colony data problems: " + ColonyData.loadProblems());
        helper.assertTrue(ColonyData.migration(GONDOR).isPresent(), "no Gondor migration table");
        helper.assertTrue(ColonyData.recruits().size() >= 2, "recruits: " + ColonyData.recruits().keySet());
        ColonyData.recruits().forEach((id, r) -> {
            helper.assertTrue(NpcData.type(r.npc()).isPresent(), id + ": unknown NPC " + r.npc());
            r.stages().forEach(s -> s.objectives().forEach(o -> o.target().ifPresent(t -> {
                boolean known = switch (o.type()) {
                    case DELIVER -> BuiltInRegistries.ITEM.containsKey(t);
                    case VISIT -> FastTravel.waypoint(t).isPresent();
                    case KILL_NPC -> NpcData.type(t).isPresent();
                    default -> true;
                };
                helper.assertTrue(known, id + ": unknown " + o.type() + " target " + t);
            })));
        });
        helper.succeed();
    }

    // own batch: the colony claims land around the test area, which would stop other tests building there
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "colonies")
    public static void aColonyGrowsAndARareRaceJoinsByQuest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Colonies colonies = Colonies.get(level.getServer());
        BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
        Colonies.Founding founding = colonies.found(player, "Test Hold", hall, GONDOR);
        helper.assertTrue(founding.colony().isPresent(), "not founded: " + founding.problem().getString());
        Colony colony = founding.colony().get();
        MinecraftServer server = level.getServer();
        helper.assertTrue(colonies.found(player, "Second", hall.offset(200, 0, 0), GONDOR).colony().isEmpty(),
                "a player founded two colonies");

        // immigration by approval: one offer, a lone settler at the start; the first settler needs no bed
        // food in the colony's stock, so nobody leaves hungry while this test counts beds
        colonies.set(colony, com.porcupinestudios.lotrport.colony.work.WorkComponents.STOCK,
                new com.porcupinestudios.lotrport.colony.work.WorkComponents.Stock(java.util.Map.of(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), 500)));
        colonies.day(level, colony);
        helper.assertTrue(Society.citizenCount(server, colony) == 0, "people came without being welcomed");
        com.porcupinestudios.lotrport.colony.Immigration.Board board = com.porcupinestudios.lotrport.colony
                .Immigration.draw(server, colony, com.porcupinestudios.lotrport.colony.Immigration.Board.EMPTY,
                        level.getGameTime());
        helper.assertTrue(board.offers().size() == 1 && board.offers().get(0).members().size() == 1,
                "a new colony's board: " + board.offers());
        helper.assertTrue(com.porcupinestudios.lotrport.colony.Immigration.accept(player, colony,
                board.offers().get(0).id()).ok(), "could not welcome the first settler");
        helper.assertTrue(Society.citizenCount(server, colony) == 1, "not welcomed");
        helper.assertTrue(colony.get(com.porcupinestudios.lotrport.colony.Immigration.BOARD).orElseThrow().offers()
                .isEmpty(), "the welcomed group is still on the board");

        // the next one needs a bed; turned away, the slot stays empty until the board is drawn again
        board = com.porcupinestudios.lotrport.colony.Immigration.draw(server, colony, board, level.getGameTime());
        int next = board.offers().get(0).id();
        helper.assertTrue(!com.porcupinestudios.lotrport.colony.Immigration.accept(player, colony, next).ok(),
                "welcomed a second settler with no bed");
        bed(helper, new BlockPos(0, 2, 0));
        bed(helper, new BlockPos(2, 2, 0));
        helper.assertTrue(Colonies.beds(level, colony) == 2, "beds counted: " + Colonies.beds(level, colony));
        helper.assertTrue(com.porcupinestudios.lotrport.colony.Immigration.accept(player, colony, next).ok(),
                "could not welcome the second settler with a bed");
        helper.assertTrue(Society.citizenCount(server, colony) == 2, "citizens: " + Society.citizenCount(server, colony));
        board = com.porcupinestudios.lotrport.colony.Immigration.draw(server, colony, board, level.getGameTime());
        helper.assertTrue(com.porcupinestudios.lotrport.colony.Immigration.decline(server, colony,
                board.offers().get(0).id()).ok(), "could not turn a group away");
        helper.assertTrue(Society.citizenCount(server, colony) == 2 && colony.get(com.porcupinestudios.lotrport
                .colony.Immigration.BOARD).orElseThrow().offers().isEmpty(), "turning away let them in");
        Person first = Society.citizens(server, colony).get(0);
        helper.assertTrue(!com.porcupinestudios.lotrport.colonist.Skills.of(first).isEmpty()
                || com.porcupinestudios.lotrport.colonist.Skills.defs().isEmpty(), "a settler came with no skills");

        for (Person c : Society.citizens(server, colony)) {
            Entity e = level.getEntity(c.id());
            helper.assertTrue(e instanceof LotrNpc, "citizen " + c.name() + " has no body");
            LotrNpc npc = (LotrNpc) e;
            helper.assertTrue(Colonies.colonyOf(npc).equals(Optional.of(colony.id())), "citizen not marked");
            helper.assertTrue(npc.faction().equals(Optional.of(GONDOR)), "citizen of " + npc.faction());
            helper.assertTrue(npc.isPersistenceRequired(), "a citizen would despawn");
        }

        helper.assertTrue(Providers.researchers(level.getServer(), Set.of(player.getUUID())) == 0,
                "two common citizens already research");

        // a rare race: a Ranger of the North, won over in three stages
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
        LotrNpc ranger = LotrNpc.spawn(level, RANGERS, at.x, at.y, at.z).orElseThrow();
        helper.assertTrue(Recruiting.forType(RANGERS).isPresent(), "Rangers are not a rare race");
        Alignment.set(player, RANGERS, 0);
        helper.assertTrue(Recruiting.interact(player, ranger) && colony.recruits().isEmpty(),
                "a Ranger listened at 0 alignment");
        Alignment.set(player, RANGERS, 200);
        Recruiting.interact(player, ranger);
        helper.assertTrue(colony.recruits().containsKey(RANGER_RECRUIT), "the recruitment did not start");

        player.getInventory().add(new ItemStack(Items.BREAD, 16));
        player.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("pipe_weed")), 4));
        Recruiting.interact(player, ranger);
        helper.assertTrue(stage(colony) == 1, "stage after delivering: " + stage(colony));
        helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 0, "the bread was not taken");

        for (int i = 0; i < 5; i++) {
            Zombie orc = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
            FactionKills.setFaction(orc, LotrPort.id("angmar"));
            Recruiting.killed(player, orc);
            orc.discard();
        }
        helper.assertTrue(stage(colony) == 2, "stage after the kills: " + stage(colony));

        Waypoint weathertop = FastTravel.waypoint(LotrPort.id("weathertop")).orElseThrow();
        level.getChunk(weathertop.x() >> 4, weathertop.z() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, weathertop.x(), weathertop.z());
        player.teleportTo(level, weathertop.x() + 0.5, y, weathertop.z() + 0.5, Set.of(), 0f, 0f);
        Recruiting.visited(player);

        helper.assertTrue(colony.recruits().isEmpty(), "the recruitment is still open at stage " + stage(colony));
        helper.assertTrue(Society.citizenCount(server, colony) == 3, "citizens after recruiting: " + Society.citizenCount(server, colony));
        helper.assertTrue(Society.citizens(server, colony).stream().anyMatch(c -> c.id().equals(ranger.getUUID())
                && c.get(CoreComponents.CITIZEN).map(CoreComponents.Citizen::rare).orElse(false)),
                "the Ranger is not a rare citizen");
        helper.assertTrue(Colonies.colonyOf(ranger).equals(Optional.of(colony.id())), "the Ranger is not marked");
        // horizontal: the Ranger lands on top of whatever stands at the Hall (Recruiting uses the heightmap)
        helper.assertTrue(ranger.blockPosition().atY(hall.getY()).closerThan(hall, 8), "the Ranger did not come to the Hall: " + ranger.blockPosition() + " hall " + hall);
        // Stage 4: colonists research TARDIS tech; the rare Ranger is a researcher for the founder
        int researchers = Providers.researchers(level.getServer(), Set.of(player.getUUID()));
        helper.assertTrue(researchers == 1, "researchers after the Ranger joined: " + researchers);
        colonies.disband(level.getServer(), colony, "test");
        helper.succeed();
    }

    private static int stage(Colony colony) {
        return colony.recruits().containsKey(RANGER_RECRUIT) ? colony.recruits().get(RANGER_RECRUIT).stage() : -1;
    }

    private static void bed(GameTestHelper helper, BlockPos foot) {
        helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
    }
}
