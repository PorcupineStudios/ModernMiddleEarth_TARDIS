package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.social.MapPresence;
import com.porcupinestudios.lotrport.social.Social;
import com.porcupinestudios.lotrport.social.SocialNet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Map visibility: by default only your colonies' members and your groups see you; everyone and chosen players are
 * opt-in; a colony's icon can be members-only or hidden; the icons a client gets are filtered on the server.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SocialTests {
    private SocialTests() {}

    @GameTest(template = "empty")
    @SuppressWarnings("deprecation")
    public static void onlyThoseYouChooseSeeYouOnTheMap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Social social = Social.get(server);
        ServerPlayer me = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        ServerPlayer neighbour = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        UUID m = me.getUUID();

        helper.assertTrue(!social.canSee(server, stranger.getUUID(), m), "a stranger sees me by default");
        // groups
        Social.Group g = social.createGroup(m, "Company").orElseThrow();
        helper.assertTrue(social.createGroup(m, "company").isEmpty(), "two groups of one name");
        social.setMember(m, "Company", friend.getUUID(), true);
        helper.assertTrue(social.canSee(server, friend.getUUID(), m) && social.canSee(server, m, friend.getUUID()),
                "group members don't see each other");
        social.setPrefs(m, social.prefs(m).withGroups(false));
        helper.assertTrue(!social.canSee(server, friend.getUUID(), m), "groups switched off still show me");

        // colonies: co-members see each other
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.foundAi(server, UUID.randomUUID(), m, "Social Test Hold", Level.OVERWORLD,
                new BlockPos(6_000_000 + helper.getLevel().random.nextInt(10_000) * 512, 70, 6_000_000),
                LotrPort.id("gondor"), 0).orElseThrow();
        try {
            Permissions.setRole(server, m, colony, neighbour.getUUID(), Optional.of(LotrPort.id("citizen")));
            helper.assertTrue(social.canSee(server, neighbour.getUUID(), m), "a fellow colonist can't see me");
            social.setPrefs(m, social.prefs(m).withColonies(false));
            helper.assertTrue(!social.canSee(server, neighbour.getUUID(), m), "colonies switched off still show me");

            // chosen players and everyone
            social.setPrefs(m, social.prefs(m).withPlayer(stranger.getUUID(), true));
            helper.assertTrue(social.canSee(server, stranger.getUUID(), m), "a chosen player can't see me");
            social.setPrefs(m, social.prefs(m).withPlayer(stranger.getUUID(), false).withEveryone(true));
            helper.assertTrue(social.canSee(server, UUID.randomUUID(), m), "'everyone' doesn't show me to everyone");

            // a colony's icon
            helper.assertTrue(MapPresence.visible(colony, stranger.getUUID()), "a public colony is hidden");
            colonies.set(colony, MapPresence.TYPE, MapPresence.MEMBERS);
            helper.assertTrue(!MapPresence.visible(colony, stranger.getUUID())
                    && MapPresence.visible(colony, neighbour.getUUID()), "members-only shows to the wrong people");
            boolean leaked = SocialNet.icons(stranger).colonies().stream()
                    .anyMatch(c -> c.name().equals(colony.name()));
            helper.assertTrue(!leaked, "the server sent a hidden colony to a stranger");
        } finally {
            colonies.disband(server, colony, "test");
            social.leave(m, g.id());
            social.setPrefs(m, Social.Prefs.DEFAULT);
        }
        helper.succeed();
    }
}
