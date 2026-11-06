package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's trolls (LOTREntityTroll, LOTREntityAITrollFleeSun):
 * <ul>
 * <li>In daylight under open sky (outside biomes where monsters walk by day) a troll starts burning: smoke, and
 * {@code burn_ticks} (300) later it turns to stone with {@code troll.transform}. It runs for shade meanwhile, up to
 * {@code shade_range} (12) blocks away. Mirk-trolls and olog-hai ({@code sun_immune}) never burn.</li>
 * <li>Tickled with a feather by a player of {@code tickle_alignment} (100) or more who it isn't fighting, it sniffs
 * and sneezes ({@code troll.sniff}, {@code troll.sneeze} at the 8th of 16 ticks), blowing 2-4 slimeballs out of
 * its nose at the 4th; its hurt face shows while it sneezes.</li>
 * <li>Its size ({@code scale}: mountain 1.6, olog-hai 1.25, Mirk 1.2, snow 0.8) and two heads (the chieftain) are
 * state the converted model reads.</li>
 * </ul>
 */
public class TrollBehaviour implements CreatureBehaviour {
    private static final TagKey<Item> FEATHERS = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:feathers"));

    @Override
    public void init(LotrNpc npc, NpcType type) {
        npc.setState("getTrollScale", CreatureBehaviour.data(type, "scale", 1f));
        npc.setState("hasTwoHeads", CreatureBehaviour.data(type, "two_heads", 0f));
        npc.setState("burnTime", -1f);
        // LOTREntityTroll: outfit nextInt(3); olog-hai and Mirk-trolls pick an armour skin (the renderer takes this
        // modulo its textures)
        npc.setState("outfitSkin", npc.random().nextInt(1024));
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new FleeSunGoal(npc));
    }

    static boolean inSun(LotrNpc npc) {
        if (CreatureBehaviour.data(npc, "sun_immune", 0f) > 0 || npc.state("is.LOTREntityStoneTroll") > 0) {
            return false;
        }
        BlockPos at = BlockPos.containing(npc.getX(), npc.getBoundingBox().minY, npc.getZ());
        return npc.level().isDay() && npc.level().canSeeSky(at) && !npc.level().isRaining();
    }

    @Override
    public void tick(LotrNpc npc) {
        if (npc.state("is.LOTREntityStoneTroll") > 0) {
            npc.setDeltaMovement(Vec3.ZERO);
            return;
        }
        int burn = (int) npc.state("burnTime");
        if (inSun(npc)) {
            burn = burn < 0 ? (int) CreatureBehaviour.data(npc, "burn_ticks", 300f) : burn - 1;
            if (npc.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, npc.getX(), npc.getY() + npc.getBbHeight() * 0.5,
                        npc.getZ(), 1, npc.getBbWidth() * 0.5, npc.getBbHeight() * 0.5, npc.getBbWidth() * 0.5, 0);
            }
            if (burn <= 0) {
                turnToStone(npc);
                return;
            }
        } else {
            burn = -1;
        }
        npc.setState("burnTime", burn);
        // sneezing (LOTREntityTroll.onLivingUpdate)
        int sneeze = (int) npc.state("getSneezingTime");
        if (sneeze > 0) {
            sneeze--;
            if (sneeze == 8) {
                LotrSounds.playAt(npc.level(), npc.getX(), npc.getY(), npc.getZ(), "troll.sneeze", SoundSource.HOSTILE,
                        1.5f, npc.getVoicePitch());
            }
            if (sneeze == 4) {
                int n = 2 + npc.random().nextInt(3);
                for (int i = 0; i < n; i++) {
                    ItemEntity slime = new ItemEntity(npc.level(), npc.getX(), npc.getEyeY(), npc.getZ(),
                            new ItemStack(Items.SLIME_BALL));
                    slime.setPickUpDelay(40);
                    Vec3 look = npc.getLookAngle().scale(0.3);
                    slime.setDeltaMovement(look.x + (npc.random().nextFloat() - 0.5f) * 0.1, 0.1 + look.y,
                            look.z + (npc.random().nextFloat() - 0.5f) * 0.1);
                    npc.level().addFreshEntity(slime);
                }
            }
            npc.setState("getSneezingTime", sneeze);
        }
        npc.setState("shouldRenderHeadHurt", sneeze > 0 ? 1f : 0f);
        float sniff = npc.state("sniffTime");
        if (sniff > 0) {
            npc.setState("sniffTime", sniff - 1);
        }
    }

    private static void turnToStone(LotrNpc npc) {
        LotrSounds.playAt(npc.level(), npc.getX(), npc.getY(), npc.getZ(), "troll.transform", SoundSource.HOSTILE,
                1f, npc.getVoicePitch());
        if (npc.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, npc.getX(), npc.getY() + 1, npc.getZ(), 30,
                    npc.getBbWidth() * 0.5, npc.getBbHeight() * 0.5, npc.getBbWidth() * 0.5, 0.02);
        }
        npc.setState("is.LOTREntityStoneTroll", 1f);
        npc.setState("is.LOTREntityTroll", 0f);
        npc.setState("burnTime", -1f);
        npc.setNoAi(true);
        npc.setPersistenceRequired();
        npc.setTarget(null);
        npc.getOwnerPlayer().ifPresent(p -> p.sendSystemMessage(Component.translatableWithFallback(
                "lotr_port.hired.troll_stone", "%s has turned to stone in the sunlight!", npc.getName())));
    }

    @Override
    public float hurt(LotrNpc npc, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (npc.state("is.LOTREntityStoneTroll") > 0) {
            // a stone troll is a statue: a player's blow breaks it, nothing else harms it
            if (source.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                npc.spawnAtLocation(new ItemStack(Items.COBBLESTONE, 2 + npc.random().nextInt(3)));
                npc.discard();
            }
            return 0f;
        }
        return amount;
    }

    @Override
    public InteractionResult interact(LotrNpc npc, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(FEATHERS) || npc.getTarget() != null || npc.state("getSneezingTime") > 0
                || npc.state("burnTime") >= 0 || npc.state("is.LOTREntityStoneTroll") > 0) {
            return null;
        }
        float needed = CreatureBehaviour.data(npc, "tickle_alignment", 100f);
        if (npc.faction().map(f -> Alignment.get(player, f) < needed).orElse(true)) {
            return null;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        npc.setState("getSneezingTime", 16);
        npc.setState("sniffTime", 16);
        LotrSounds.playAt(npc.level(), npc.getX(), npc.getY(), npc.getZ(), "troll.sniff", SoundSource.HOSTILE, 1f,
                npc.getVoicePitch());
        return InteractionResult.SUCCESS;
    }

    /** Runs for shade while burning (LOTREntityAITrollFleeSun): a spot out of the sky within the range. */
    private static final class FleeSunGoal extends Goal {
        private final LotrNpc npc;
        private Vec3 shade;

        FleeSunGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (npc.state("burnTime") < 0) {
                return false;
            }
            int range = (int) CreatureBehaviour.data(npc, "shade_range", 12f);
            for (int i = 0; i < 32; i++) {
                BlockPos p = npc.blockPosition().offset(Mth.nextInt(npc.random(), -range, range),
                        Mth.nextInt(npc.random(), -6, 6), Mth.nextInt(npc.random(), -range, range));
                if (!npc.level().canSeeSky(p) && npc.level().getBlockState(p.below()).isSolid()
                        && npc.level().getBlockState(p).isAir()) {
                    shade = Vec3.atBottomCenterOf(p);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            npc.getNavigation().moveTo(shade.x, shade.y, shade.z, 1.4);
        }

        @Override
        public boolean canContinueToUse() {
            return npc.state("burnTime") >= 0 && !npc.getNavigation().isDone();
        }
    }
}
