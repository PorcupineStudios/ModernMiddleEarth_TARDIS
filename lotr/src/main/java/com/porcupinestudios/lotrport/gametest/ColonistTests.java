package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.Kin;
import com.porcupinestudios.lotrport.colonist.TraitDef;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.People;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Colonists as people: the trait, need, thought and race files load with no dangling ids, a settler gets traits,
 * needs, a mood and kin, and those survive a restart. The rules themselves are unit-tested in
 * {@code ColonistRulesTest}.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonistTests {
    private static final String EMPTY = "empty";

    private ColonistTests() {}

    @GameTest(template = EMPTY)
    public static void colonistDataLoads(GameTestHelper helper) {
        List<String> problems = ColonistData.loadProblems();
        helper.assertTrue(problems.isEmpty(), "colonist data problems: " + problems);
        helper.assertTrue(!ColonistData.traits().isEmpty() && !ColonistData.needs().isEmpty()
                && !ColonistData.thoughts().isEmpty() && !ColonistData.races().isEmpty(), "a colonist folder is empty");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aSettlerGetsTraitsNeedsMoodAndKin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        Optional<LotrNpc> spawned = LotrNpc.spawn(helper.getLevel(), LotrPort.id("bree_hobbit"), pos.x, pos.y, pos.z);
        helper.assertTrue(spawned.isPresent(), "could not spawn a Bree hobbit");
        LotrNpc npc = spawned.get();
        People people = People.get(server);
        people.settled(server, npc);
        Colonists.settle(server, npc);

        List<ResourceLocation> traits = people.part(npc.getUUID(), ColonistData.TRAITS).orElse(List.of());
        helper.assertTrue(traits.size() >= 2 && traits.size() <= ColonistData.settings().maxTraits(),
                "traits: " + traits);
        helper.assertTrue(traits.stream().anyMatch(t -> ColonistData.traits().get(t).kind() == TraitDef.Kind.POSITIVE)
                && traits.stream().anyMatch(t -> ColonistData.traits().get(t).kind() == TraitDef.Kind.NEGATIVE),
                "not one good and one bad trait: " + traits);
        Optional<Kin> kin = people.part(npc.getUUID(), ColonistData.KIN);
        helper.assertTrue(kin.isPresent() && kin.get().race().equals(LotrPort.id("hobbit")),
                "a Bree hobbit is not of the hobbit race");
        helper.assertTrue(people.part(npc.getUUID(), ColonistData.NEEDS).isPresent()
                && Colonists.mood(server, npc.getUUID()).isPresent(), "no needs or mood");

        // settling again changes nothing, and the parts survive saving
        Colonists.settle(server, npc);
        helper.assertTrue(people.part(npc.getUUID(), ColonistData.TRAITS).orElse(List.of()).equals(traits),
                "settling twice re-rolled the traits");
        People reloaded = People.load(people.save(new CompoundTag(), server.registryAccess()),
                server.registryAccess());
        helper.assertTrue(reloaded.part(npc.getUUID(), ColonistData.TRAITS).orElse(List.of()).equals(traits)
                && reloaded.part(npc.getUUID(), ColonistData.KIN).equals(kin), "the parts did not survive saving");
        helper.succeed();
    }
}
