package com.porcupinestudios.lotrport.land;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.content.GateBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Keeps claimed land safe: breaking, placing, containers and doors need the matching {@link Perm} in the colony
 * that owns the chunk (or to be the player who owns a personal claim). Operators are never stopped. Other systems
 * may open land up through {@link #exceptions} (a siege, say), so this class never needs to know about wars.
 */
public final class Protection {
    /** A rule that allows something protection would refuse. */
    @FunctionalInterface
    public interface Exception {
        boolean allows(ServerPlayer player, Claim claim, String perm);
    }

    private static final List<Exception> EXCEPTIONS = new ArrayList<>();

    private Protection() {}

    /** Adds an exception (wars add siege rules here). */
    public static void exception(Exception rule) {
        EXCEPTIONS.add(rule);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Protection::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Protection::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Protection::onUse);
        NeoForge.EVENT_BUS.addListener(Protection::onExplode);
    }

    /** Whether the player may do {@code perm} at {@code pos}: unclaimed land, their own, or a role that grants it. */
    public static boolean allowed(ServerPlayer player, Level level, BlockPos pos, String perm) {
        if (player.hasPermissions(2)) {
            return true;
        }
        Optional<Claim> claim = Claims.get(player.server).at(level.dimension(), pos);
        return claim.isEmpty() || allowed(player.server, player, claim.get(), perm);
    }

    static boolean allowed(MinecraftServer server, ServerPlayer player, Claim claim, String perm) {
        UUID id = player.getUUID();
        boolean ok = switch (claim.owner()) {
            case com.porcupinestudios.lotrport.society.Owner.OfPlayer p -> p.id().equals(id);
            case com.porcupinestudios.lotrport.society.Owner.OfColony c -> Colonies.get(server).get(c.id())
                    .map(col -> Permissions.can(id, col, perm)).orElse(true);
            default -> true;
        };
        if (ok) {
            return true;
        }
        for (Exception e : EXCEPTIONS) {
            if (e.allows(player, claim, perm)) {
                return true;
            }
        }
        return false;
    }

    private static void refuse(ServerPlayer player, Level level, BlockPos pos) {
        Claims.get(player.server).at(level.dimension(), pos).ifPresent(c -> player.displayClientMessage(
                Component.translatableWithFallback("land.lotr_port.protected", "This land belongs to %s",
                        ownerName(player.server, c)), true));
    }

    public static Component ownerName(MinecraftServer server, Claim claim) {
        return switch (claim.owner()) {
            case com.porcupinestudios.lotrport.society.Owner.OfColony c -> Component.literal(
                    Colonies.get(server).get(c.id()).map(com.porcupinestudios.lotrport.colony.Colony::name)
                            .orElse("a colony"));
            case com.porcupinestudios.lotrport.society.Owner.OfPlayer p -> Component.literal(
                    Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(p.id()))
                            .map(com.mojang.authlib.GameProfile::getName).orElse("another player"));
            default -> Component.literal("someone");
        };
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp && event.getLevel() instanceof Level level
                && !allowed(sp, level, event.getPos(), Perm.BUILD)) {
            event.setCanceled(true);
            refuse(sp, level, event.getPos());
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && event.getLevel() instanceof Level level
                && !allowed(sp, level, event.getPos(), Perm.BUILD)) {
            event.setCanceled(true);
            refuse(sp, level, event.getPos());
        }
    }

    /** Right-clicks: doors and switches need {@code doors}, containers {@code containers}, using items {@code build}. */
    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        String perm = permFor(level, pos, state);
        if (perm != null && !allowed(sp, level, pos, perm)) {
            event.setUseBlock(TriState.FALSE);
            if (!allowed(sp, level, pos.relative(event.getFace() == null ? net.minecraft.core.Direction.UP
                    : event.getFace()), Perm.BUILD)) {
                event.setUseItem(TriState.FALSE);
            }
            event.setCanceled(true);
            refuse(sp, level, pos);
        } else if (!sp.getMainHandItem().isEmpty() && !allowed(sp, level, pos, Perm.BUILD)
                && !(sp.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem)) {
            // buckets, flint and steel, hoes, bone meal...; block items are checked when placed
            event.setUseItem(TriState.FALSE);
        }
    }

    /** Which permission a right-click on this block needs (null = none). */
    static String permFor(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof GateBlock) {
            return Perm.DOORS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider || be instanceof net.minecraft.world.Container) {
            return Perm.CONTAINERS;
        }
        return null;
    }

    private static void onExplode(ExplosionEvent.Detonate event) {
        if (!LandSettings.get().protectExplosions() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Claims claims = Claims.get(level.getServer());
        event.getAffectedBlocks().removeIf(p -> claims.at(level.dimension(), p).isPresent());
    }

    /** For tests and other systems: would this player be stopped here? */
    public static boolean blocks(Player player, Level level, BlockPos pos, String perm) {
        return player instanceof ServerPlayer sp && !allowed(sp, level, pos, perm);
    }
}
