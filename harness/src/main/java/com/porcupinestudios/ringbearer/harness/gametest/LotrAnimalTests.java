package com.porcupinestudios.ringbearer.harness.gametest;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import com.porcupinestudios.ringbearer.harness.dev.TestWagon.Kind;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import com.porcupinestudios.ringbearer.harness.gametest.TowRun.Scenario;
import com.porcupinestudios.ringbearer.harness.gametest.TowRun.Surface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Every animal ridden in the LOTR mod, and the port's Rhosgobel rabbit, in a harness: it goes on (in place of a saddle
 * where there is one), the animal has draught data, and it pulls a wagon (or the rabbit a sled) at a trot with the
 * horse's pass marks. Generated only when the LOTR port is loaded (harness runs with {@code -PwithLotr}); the harness
 * doesn't depend on it, so the animals are named, not referenced ({@link Animals}).
 */
@GameTestHolder(HarnessMod.MOD_ID)
public final class LotrAnimalTests {
    /** The LOTR port's ridden animals: its mount breeds, and the creatures its NPCs ride (one of each kind). */
    public static final List<String> ANIMALS = List.of(
            "lotr_port:horse", "lotr_port:shire_pony", "lotr_port:zebra", "lotr_port:wild_boar", "lotr_port:camel",
            "lotr_port:elk", "lotr_port:rhino", "lotr_port:giraffe", "lotr_port:rhosgobel_rabbit",
            "lotr_port:npc|lotr_port:mordor_warg", "lotr_port:npc|lotr_port:utumno_ice_warg",
            "lotr_port:rigged_npc|lotr_port:mirkwood_spider");
    private static final double TROT = 4;
    private static final int TIMEOUT = 700;

    private LotrAnimalTests() {}

    /** Tow runs, also played by the dev movie recorder. */
    public static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();
        for (String a : ANIMALS) {
            if (Animals.exists(a)) {
                out.add(new Scenario("lotr_" + Animals.kind(a).getPath() + "_flat", Surface.FLAT, TROT, 0, 3)
                        .on(Kind.WAGON_WHEELS).by(a));
            }
        }
        if (Animals.exists("lotr_port:rhosgobel_rabbit")) {
            out.add(new Scenario("lotr_rhosgobel_rabbit_sled", Surface.STEP, TROT, 0, 3).on(Kind.SLED_GENTLE)
                    .by("lotr_port:rhosgobel_rabbit"));
            out.add(new Scenario("lotr_rhosgobel_rabbit_uphill", Surface.UPHILL, TROT, 0, 3).on(Kind.WAGON_WHEELS)
                    .by("lotr_port:rhosgobel_rabbit"));
        }
        return out;
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        List<TestFunction> out = new ArrayList<>();
        for (String a : ANIMALS) {
            if (Animals.exists(a)) {
                out.add(new TestFunction("defaultBatch", "lotr_wears_" + Animals.kind(a).getPath(),
                        HarnessMod.MOD_ID + ":arena", 100, 0L, true, h -> wears(h, a)));
            }
        }
        for (Scenario sc : scenarios()) {
            out.add(new TestFunction("defaultBatch", sc.name(), HarnessMod.MOD_ID + ":arena", TIMEOUT, 0L, true,
                    h -> TowRun.start(h, sc)));
        }
        return out;
    }

    /** A harness goes on (a saddle, if it had one, comes off), and it can pull. */
    private static void wears(GameTestHelper h, String spec) {
        Mob animal = Animals.create(h.getLevel(), spec);
        if (animal == null) {
            h.fail("couldn't make " + spec);
            return;
        }
        Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        animal.moveTo(at.x, at.y, at.z, 0, 0);
        animal.setNoAi(true);
        h.getLevel().addFreshEntity(animal);
        h.assertTrue(DraughtAnimals.of(animal) != null, spec + " has no draught data");
        boolean saddled = false;
        if (animal instanceof AbstractHorse horse && horse.isSaddleable()) {
            horse.equipSaddle(new ItemStack(Items.SADDLE), null);
            saddled = horse.isSaddled();
        }
        final boolean wasSaddled = saddled;
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HarnessMod.HARNESS.get()));
        p.interactOn(animal, InteractionHand.MAIN_HAND);
        h.assertTrue(Harnessing.wears(animal), "using a harness on " + spec + " didn't put it on");
        h.assertTrue(Harnessing.slot(animal).map(s -> s.getStackInSlot(0).is(HarnessMod.HARNESS.get())).orElse(false),
                spec + " has no harness in a Curios harness slot");
        if (wasSaddled) {
            h.assertTrue(((AbstractHorse) animal).isSaddled(), "the saddle came off " + spec + " for the harness");
        }
        if (animal instanceof AbstractHorse) {
            Harnessing.takeOff(animal, p);
            h.assertFalse(Harnessing.wears(animal), "taking it off " + spec + " left it worn");
        } else {
            // no saddle slot: sneak and use it with an empty hand
            Player q = h.makeMockPlayer(GameType.SURVIVAL);
            q.setShiftKeyDown(true);
            q.interactOn(animal, InteractionHand.MAIN_HAND);
            h.assertFalse(Harnessing.wears(animal), "sneaking with an empty hand didn't take it off " + spec);
            h.assertTrue(q.getInventory().countItem(HarnessMod.HARNESS.get()) == 1,
                    "the harness didn't come back from " + spec);
        }
        animal.discard();
        com.mojang.logging.LogUtils.getLogger().info("[lotr-wears] {} wears a harness", spec);
        h.succeed();
    }
}
