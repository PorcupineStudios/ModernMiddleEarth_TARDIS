package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * LOTR's orcs (LOTREntityOrc). Weak orcs ({@code weak_orc}: not Uruks, Black Uruks, captains, Utumno orcs...) under
 * open sky by day get weakness and slowness for {@code daylight_ticks} (200), renewed every second, except in biomes
 * where hostiles walk by day (Mordor...). Weak orcs other than Mordor's ({@code avoid_good}) run from players at
 * {@code avoid_alignment} (-500) or worse within {@code avoid_range} (8), unless they or a friendly NPC near them is
 * already fighting a player (LOTREntityAIOrcAvoidGoodPlayer).
 * <p>LOTR's weakness was level -1, which in 1.7.10 took no strength away; here it is weakness I.
 */
public class OrcBehaviour implements CreatureBehaviour {
    /** Ticks left of LOTR's orcSkirmishTick (160 from the start of a fight with another orc). */
    private int skirmish;

    @Override
    public void addGoals(LotrNpc npc) {
        npc.targets().addGoal(4, new SkirmishGoal(npc));
        if (CreatureBehaviour.data(npc, "avoid_good", 0) > 0) {
            // above the melee goal: LOTR's orcs flee before they fight
            npc.goals().addGoal(0, new FleeGoal(npc,
                    p -> npc.faction().map(f -> Alignment.get(p, f)).orElse(0f)
                            <= CreatureBehaviour.data(npc, "avoid_alignment", -500) && p != revenge(npc),
                    () -> npc.owner().isEmpty() && revenge(npc) == null && !alliesFightingPlayers(npc),
                    CreatureBehaviour.data(npc, "avoid_range", 8), CreatureBehaviour.data(npc, "avoid_speed", 1.5f),
                    CreatureBehaviour.data(npc, "avoid_speed", 1.5f)));
        }
    }

    /** LOTR's currentRevengeTarget: whoever hurt it, while it is still fighting. */
    private static Object revenge(LotrNpc npc) {
        return npc.getTarget() != null && npc.getTarget() == npc.getLastHurtByMob() ? npc.getTarget() : null;
    }

    private static boolean alliesFightingPlayers(LotrNpc npc) {
        double r = CreatureBehaviour.data(npc, "avoid_range", 8);
        return !npc.level().getEntitiesOfClass(LotrNpc.class, npc.getBoundingBox().inflate(r, r / 2, r),
                n -> n != npc && n.getTarget() instanceof Player && n.faction().isPresent() && npc.faction().isPresent()
                        && (n.faction().equals(npc.faction()) || Factions.relation(npc.getServer(),
                                npc.faction().get(), n.faction().get()).isGood())).isEmpty();
    }

    private static OrcBehaviour orc(LotrNpc npc) {
        for (CreatureBehaviour b : npc.behaviours()) {
            if (b instanceof OrcBehaviour o) {
                return o;
            }
        }
        return null;
    }

    /** LOTR's canOrcSkirmish: a free orc on foot, not a trader or captain. */
    private static boolean canSkirmish(LotrNpc npc) {
        var t = npc.npcType();
        return orc(npc) != null && npc.isAlive() && npc.owner().isEmpty() && npc.getVehicle() == null
                && t != null && !t.trader() && !t.captain();
    }

    /**
     * LOTREntityAIOrcSkirmish: an orc that isn't skirmishing starts one with chance 1 in {@code skirmish_chance}
     * (20000) a tick, ten times likelier for each skirmishing orc within 16 (8 up or down), at least 1 in
     * {@code skirmish_min} (40); then it (and a skirmisher, every tick) goes for the nearest orc it can see.
     */
    private final class SkirmishGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final LotrNpc npc;
        private LotrNpc other;

        SkirmishGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(java.util.EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!com.porcupinestudios.lotrport.LotrConfig.ORC_SKIRMISHES.get() || !canSkirmish(npc) || npc.getTarget() != null) {
                return false;
            }
            if (skirmish <= 0) {
                float chance = CreatureBehaviour.data(npc, "skirmish_chance", 20000);
                for (LotrNpc n : npc.level().getEntitiesOfClass(LotrNpc.class, npc.getBoundingBox().inflate(16, 8, 16))) {
                    OrcBehaviour o = orc(n);
                    if (o != null && o.skirmish > 0) {
                        chance /= 10;
                    }
                }
                chance = Math.max(chance, CreatureBehaviour.data(npc, "skirmish_min", 40));
                if (npc.random().nextInt((int) chance) != 0) {
                    return false;
                }
            }
            double r = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            other = npc.level().getNearestEntity(LotrNpc.class,
                    net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(r)
                            .selector(e -> e instanceof LotrNpc n && canSkirmish(n)),
                    npc, npc.getX(), npc.getEyeY(), npc.getZ(), npc.getBoundingBox().inflate(r, 4, r));
            return other != null;
        }

        @Override
        public void start() {
            npc.setTarget(other);
            skirmish = (int) CreatureBehaviour.data(npc, "skirmish_ticks", 160);
        }

        @Override
        public boolean canContinueToUse() {
            return false; // the target stays set; the melee goal fights it
        }
    }

    /**
     * LOTREntityUrukHai.onDeath: an Uruk-hai killed by a player within 12 blocks of an Uruk spawner gives raidUrukCamp.
     * shortcut: the port has no spawner blocks, so it is "inside an Uruk camp", until respawners exist.
     */
    @Override
    public void died(LotrNpc npc, net.minecraft.world.damagesource.DamageSource source) {
        if (CreatureBehaviour.data(npc, "raid_camp", 0) <= 0
                || !(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)
                || !(npc.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        var structures = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (String camp : new String[] {"uruk_camp_120", "uruk_camp_2000"}) {
            var s = structures.get(com.porcupinestudios.lotrport.LotrPort.id(camp));
            if (s != null && level.structureManager().getStructureWithPieceAt(npc.blockPosition(), s).isValid()) {
                com.porcupinestudios.lotrport.progress.Achievements.grant(p, "raidUrukCamp");
                return;
            }
        }
    }

    @Override
    public void tick(LotrNpc npc) {
        if (skirmish > 0 && !(npc.getTarget() instanceof LotrNpc t && orc(t) != null)) {
            skirmish--;
        }
        if (npc.tickCount % 20 != 0 || CreatureBehaviour.data(npc, "weak_orc", 0) <= 0) {
            return;
        }
        var level = npc.level();
        if (!level.isDay() || !level.canSeeSky(npc.blockPosition())) {
            return;
        }
        boolean darkBiome = level.getBiome(npc.blockPosition()).unwrapKey()
                .flatMap(k -> NpcSpawns.forBiome(k.location())).map(NpcSpawns.Table::hostilesInDay).orElse(false);
        if (!darkBiome) {
            int t = (int) CreatureBehaviour.data(npc, "daylight_ticks", 200);
            npc.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t));
            npc.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t));
        }
    }
}
