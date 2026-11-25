package com.porcupinestudios.ringbearer.harness.dev;

import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev only ({@code -Dringbearer_harness.showcase=true}): when a player joins, builds a scene round where they stand
 * (on the ground under them) for screenshots (client side: {@code client/ShowcaseCamera}): the test wagon with a harnessed horse hitched to it, and
 * a second harnessed horse standing free. Positions are relative to the join position, which the client reads too.
 */
public final class Showcase {
    public static final boolean ON = System.getProperty("ringbearer_harness.showcase", "false").matches("true|lotr");
    /** {@code =lotr}: instead, the LOTR port's animals in a row, each in a harness (fit screenshots). */
    public static final boolean LOTR = "lotr".equals(System.getProperty("ringbearer_harness.showcase"));
    /** The row: animal i stands at x = LOTR_X0 + i * LOTR_STEP, z = LOTR_Z, facing +z (south). */
    public static final double LOTR_X0 = 2.5, LOTR_STEP = 5, LOTR_Z = 4.5;
    /** The row's heads ({@code -Dringbearer_harness.showcaseHead=<yaw>,<pitch>}, degrees; default straight ahead). */
    private static final float HEAD_YAW = head(0), HEAD_PITCH = head(1);
    /** Scene layout, blocks from the join position: the wagon's back-left deck corner, and the free horse. */
    public static final int WAGON_X = 3, WAGON_Z = 3;
    public static final double FREE_X = 12.5, FREE_Z = 3.5;

    private static ServerPlayer player;
    private static BlockPos origin;
    private static int ticks = -1;

    private Showcase() {}

    public static void register() {
        if (!ON) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                player = p;
                origin = p.blockPosition();
                ticks = 0;
                p.setGameMode(GameType.SPECTATOR);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (ticks < 0 || ++ticks != 20) {
                return;
            }
            build((ServerLevel) player.level());
        });
    }

    private static void build(ServerLevel level) {
        // on the ground under the join position (the player may be up in the air)
        origin = new BlockPos(origin.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(),
                origin.getZ()), origin.getZ());
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        if (LOTR) {
            lotrRow(level);
            return;
        }
        TestWagon.Built wagon = TestWagon.build(level, origin.offset(WAGON_X, 1, WAGON_Z), 0);
        if (wagon == null) {
            return;
        }
        Vec3 front = wagon.hitchFront();
        Horse hitched = horse(level, front.x, origin.getY(), front.z + 1.95, 0);
        Harnessing.putOn(hitched, null);
        wagon.hitch().hitch(hitched);
        Horse free = horse(level, origin.getX() + FREE_X, origin.getY(), origin.getZ() + FREE_Z, 90);
        Harnessing.putOn(free, null);
    }

    /** Each LOTR animal that's loaded, harnessed and standing still (no AI), in a row (ShowcaseCamera's stops). */
    private static void lotrRow(ServerLevel level) {
        var animals = com.porcupinestudios.ringbearer.harness.gametest.LotrAnimalTests.ANIMALS;
        for (int i = 0; i < animals.size(); i++) {
            var mob = com.porcupinestudios.ringbearer.harness.gametest.Animals.create(level, animals.get(i));
            if (mob == null) {
                continue;
            }
            mob.moveTo(origin.getX() + LOTR_X0 + i * LOTR_STEP, origin.getY(), origin.getZ() + LOTR_Z, 0, 0);
            mob.setYBodyRot(0);
            // heads turned toward the camera and lowered: the collar must follow the neck
            mob.setYHeadRot(HEAD_YAW);
            mob.setXRot(HEAD_PITCH);
            mob.setNoAi(true);
            level.addFreshEntity(mob);
            Harnessing.putOn(mob, null);
        }
    }

    private static float head(int i) {
        String[] v = System.getProperty("ringbearer_harness.showcaseHead", "0,0").split(",");
        return i < v.length ? Float.parseFloat(v[i]) : 0f;
    }

    private static Horse horse(ServerLevel level, double x, double y, double z, float yaw) {
        Horse h = EntityType.HORSE.create(level);
        h.moveTo(x, y, z, yaw, 0);
        h.setYBodyRot(yaw);
        h.setYHeadRot(yaw);
        h.setTamed(true);
        h.setNoAi(true);
        h.setPersistenceRequired();
        level.addFreshEntity(h);
        return h;
    }
}
