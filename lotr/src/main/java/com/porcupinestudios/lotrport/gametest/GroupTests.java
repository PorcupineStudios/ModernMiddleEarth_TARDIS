package com.porcupinestudios.lotrport.gametest;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.group.Group;
import com.porcupinestudios.lotrport.group.GroupId;
import com.porcupinestudios.lotrport.group.GroupKinds;
import com.porcupinestudios.lotrport.group.Groups;
import com.porcupinestudios.lotrport.group.Owner;
import com.porcupinestudios.lotrport.group.PartType;
import com.porcupinestudios.lotrport.group.Parts;
import com.porcupinestudios.lotrport.group.PersonId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shared core for colonies and hired units ({@code group/}): one owner can hold several groups, a person moves
 * between groups, parts survive a restart, and a part this game can't read is written back untouched.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroupTests {
    private static final String EMPTY = "empty";
    private static final PartType<Integer> TEST_PART = Parts.register(PartType.of(LotrPort.id("test_part"), 1,
            Codec.INT));

    private GroupTests() {}

    @GameTest(template = EMPTY)
    public static void groupsHoldPeoplePartsAndSurviveARestart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // a store of its own, so nothing from other tests is in it
        Groups groups = new Groups();
        UUID alice = UUID.randomUUID();
        UUID ruler = UUID.randomUUID();
        Group first = groups.ensure(GroupId.random(), GroupKinds.COLONY, Owner.player(alice), "First", 10);
        Group second = groups.ensure(GroupId.random(), GroupKinds.COLONY, Owner.player(alice), "Second", 20);
        Group company = groups.company(alice, 30);
        helper.assertTrue(groups.company(alice, 40) == company, "a second company was made");
        helper.assertTrue(groups.ownedBy(alice, GroupKinds.COLONY).equals(List.of(first, second)),
                "one owner's colonies are not listed oldest first");

        PersonId soldier = PersonId.of(UUID.randomUUID());
        groups.join(company, soldier, GroupKinds.ROLE_FOLLOWER, 50, Set.of());
        groups.join(first, soldier, GroupKinds.ROLE_CITIZEN, 60, Set.of());
        helper.assertTrue(company.size() == 0 && first.size() == 1 && groups.groupOf(soldier).orElse(null) == first,
                "garrisoning did not move the person from the company to the colony");

        groups.setOwner(second, Owner.aiRuler(ruler));
        helper.assertTrue(groups.ownedBy(alice, GroupKinds.COLONY).equals(List.of(first))
                && groups.ownedBy(ruler, GroupKinds.COLONY).equals(List.of(second)), "the handover lost a colony");

        groups.setPart(first, TEST_PART, 7);
        CompoundTag saved = groups.save(new CompoundTag(), server.registryAccess());
        CompoundTag mystery = new CompoundTag();
        mystery.putInt("dataVersion", 1);
        mystery.put("data", StringTag.valueOf("keep me"));
        ((CompoundTag) saved.getList("groups", Tag.TAG_COMPOUND).get(0)).getCompound("parts")
                .put("othermod:mystery", mystery);

        Groups reloaded = Groups.load(saved, server.registryAccess());
        Group again = reloaded.get(first.id()).orElseThrow();
        helper.assertTrue(again.part(TEST_PART).orElse(0) == 7 && again.name().equals("First"),
                "the group or its part did not survive saving");
        helper.assertTrue(reloaded.groupOf(soldier).map(g -> g.id().equals(first.id())).orElse(false),
                "membership did not survive saving");
        CompoundTag resaved = reloaded.save(new CompoundTag(), server.registryAccess());
        Tag kept = ((CompoundTag) resaved.getList("groups", Tag.TAG_COMPOUND).get(0)).getCompound("parts")
                .getCompound("othermod:mystery").get("data");
        helper.assertTrue(kept != null && kept.getAsString().equals("keep me"),
                "a part this game cannot read was dropped");
        helper.succeed();
    }
}
