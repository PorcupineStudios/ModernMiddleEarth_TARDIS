package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonyAccess;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.land.ClaimBanner;
import com.porcupinestudios.lotrport.land.ClaimBannerItem;
import com.porcupinestudios.lotrport.land.Claims;
import com.porcupinestudios.lotrport.land.LandSettings;
import com.porcupinestudios.lotrport.land.Protection;
import com.porcupinestudios.lotrport.society.Owner;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Town Hall roles and land: the owner may do everything, members what their role grants, outsiders nothing on the
 * colony's land; roles are given only below your own; banners claim whole chunks and never overlap another owner.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandTests {
    private LandTests() {}

    @GameTest(template = "empty")
    @SuppressWarnings("deprecation")
    public static void rolesGrantWhatTheySayAndLandIsProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(Permissions.loadProblems().isEmpty() && LandSettings.loadProblems().isEmpty(),
                "data problems: " + Permissions.loadProblems() + LandSettings.loadProblems());
        helper.assertTrue(Permissions.roles().size() >= 5, "roles: " + Permissions.roles().keySet());
        ServerPlayer ruler = helper.makeMockServerPlayerInLevel();
        ServerPlayer steward = helper.makeMockServerPlayerInLevel();
        ServerPlayer citizen = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!stranger.hasPermissions(2), "mock players are operators: protection can't be tested");

        // a colony far from the test area, so no other test's land is in the way
        // a fresh spot each run: the test world keeps its saved data between runs
        BlockPos hall = new BlockPos(5_000_016 + level.random.nextInt(10_000) * 16 * 64, 70, 5_000_016);
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.foundAi(server, UUID.randomUUID(), ruler.getUUID(), "Land Test Hold",
                Level.OVERWORLD, hall, LotrPort.id("gondor"), 0).orElseThrow();
        Claims claims = Claims.get(server);
        try {
            int r = LandSettings.get().hallRadius();
            helper.assertTrue(claims.at(Level.OVERWORLD, hall.offset(r * 16, 0, -r * 16)).isPresent()
                    && claims.at(Level.OVERWORLD, hall.offset((r + 1) * 16, 0, 0)).isEmpty(),
                    "the Hall did not claim " + (2 * r + 1) + " x " + (2 * r + 1) + " chunks");

            // roles: given by someone allowed, only below their own rank
            helper.assertTrue(Permissions.setRole(server, ruler.getUUID(), colony, steward.getUUID(),
                    Optional.of(LotrPort.id("steward"))).ok(), "the ruler could not make a steward");
            helper.assertTrue(!Permissions.setRole(server, citizen.getUUID(), colony, stranger.getUUID(),
                    Optional.of(LotrPort.id("citizen"))).ok(), "a non-member gave out roles");
            helper.assertTrue(Permissions.setRole(server, steward.getUUID(), colony, citizen.getUUID(),
                    Optional.of(LotrPort.id("citizen"))).ok(), "the steward could not add a citizen");
            helper.assertTrue(!Permissions.setRole(server, steward.getUUID(), colony, citizen.getUUID(),
                    Optional.of(LotrPort.id("steward"))).ok(), "a steward made another steward");
            helper.assertTrue(!Permissions.setRole(server, steward.getUUID(), colony, ruler.getUUID(),
                    Optional.empty()).ok(), "a steward removed the ruler");
            helper.assertTrue(Permissions.can(citizen.getUUID(), colony, Perm.BUILD)
                    && !Permissions.can(citizen.getUUID(), colony, Perm.COMMAND)
                    && Permissions.can(steward.getUUID(), colony, Perm.COMMAND)
                    && Permissions.can(ruler.getUUID(), colony, Perm.WAR)
                    && !Permissions.can(stranger.getUUID(), colony, Perm.DOORS), "roles grant the wrong things");
            helper.assertTrue(ColonyAccess.canManage(server, steward.getUUID(), colony)
                    && !ColonyAccess.canManage(server, citizen.getUUID(), colony), "managing follows roles");

            // protection on the colony's land
            BlockPos spot = hall.offset(3, 0, 3);
            helper.assertTrue(Protection.blocks(stranger, level, spot, Perm.BUILD), "a stranger may build here");
            helper.assertTrue(!Protection.blocks(citizen, level, spot, Perm.BUILD), "a citizen may not build");
            helper.assertTrue(Protection.blocks(citizen, level, spot, Perm.BANNERS), "a citizen may move banners");
            helper.assertTrue(!Protection.blocks(stranger, level, hall.offset(200, 0, 0), Perm.BUILD),
                    "unclaimed land is protected");

            // banners: the steward plants one for the colony; a stranger's personal banner can't overlap it
            ItemStack banner = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("banner_of_gondor")));
            helper.assertTrue(banner.getItem() instanceof ClaimBannerItem, "LOTR banners don't claim land");
            level.getChunk(hall.offset(64, 0, 0));
            ClaimBannerItem.Result planted = ClaimBannerItem.plant(steward, level, hall.offset(64, 0, 0), banner);
            helper.assertTrue(planted.ok(), "the steward could not plant: " + planted.message().getString());
            helper.assertTrue(claims.at(Level.OVERWORLD, hall.offset(64 + 16, 0, 0)).map(c -> c.owner()
                    .equals(colony.asOwner())).orElse(false), "the banner's land is not the colony's");
            ClaimBannerItem.Result clash = ClaimBannerItem.plant(stranger, level, hall.offset(80, 0, 0), banner);
            helper.assertTrue(!clash.ok(), "a personal banner overlapped colony land");
            BlockPos far = hall.offset(400, 0, 0);
            level.getChunk(far);
            ClaimBannerItem.Result personal = ClaimBannerItem.plant(stranger, level, far, banner);
            helper.assertTrue(personal.ok(), "no personal banner: " + personal.message().getString());
            helper.assertTrue(claims.at(Level.OVERWORLD, far).map(c -> c.owner()
                    .equals(Owner.player(stranger.getUUID()))).orElse(false), "personal land not the player's");
            helper.assertTrue(!ClaimBannerItem.plant(stranger, level, far.offset(200, 0, 0), banner).ok(),
                    "more personal banners than allowed");
            helper.assertTrue(Protection.blocks(ruler, level, far, Perm.BUILD), "others may build on personal land");

            // taking a banner down frees its land
            ClaimBanner planted2 = personal.banner().orElseThrow();
            helper.assertTrue(!planted2.hurt(level.damageSources().playerAttack(ruler), 1)
                    && claims.at(Level.OVERWORLD, far).isPresent(), "someone else took the banner down");
            planted2.hurt(level.damageSources().playerAttack(stranger), 1);
            helper.assertTrue(claims.at(Level.OVERWORLD, far).isEmpty(), "the land stayed claimed");

            // lookups are cheap: one map get per check
            long t = System.nanoTime();
            int hits = 0;
            for (int i = 0; i < 100_000; i++) {
                hits += claims.at(Level.OVERWORLD, hall.offset((i % 200) * 4, 0, 0)).isPresent() ? 1 : 0;
            }
            long us = (System.nanoTime() - t) / 1000;
            LotrPort.LOG.info("[land] 100000 claim lookups: {} us ({} hits)", us, hits);
            helper.assertTrue(us < 200_000, "claim lookups took " + us + " us");
            level.setBlock(helper.absolutePos(new BlockPos(0, 1, 0)), Blocks.STONE.defaultBlockState(), 3);
        } finally {
            colonies.disband(server, colony, "test");
            claims.ownedBy(Owner.player(stranger.getUUID())).forEach(c -> claims.remove(server, c.id()));
        }
        helper.assertTrue(claims.at(Level.OVERWORLD, hall).isEmpty(), "a disbanded colony kept its land");
        helper.succeed();
    }
}
