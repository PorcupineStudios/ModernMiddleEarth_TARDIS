package com.porcupinestudios.lotrport.land;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Society;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * LOTR's faction banners: plant one on the ground to claim land. Near a colony you may place banners for (role
 * permission {@code banners}, within {@code banner_reach} chunks of its Hall) it claims for the colony, up to the
 * colony's banner allowance; anywhere else it is a personal banner, up to {@code personal_banners}.
 */
public class ClaimBannerItem extends Item {
    public ClaimBannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        BlockPos at = ctx.getClickedPos().above();
        Result r = plant(player, level, at, ctx.getItemInHand().copyWithCount(1));
        player.displayClientMessage(r.message(), true);
        if (r.ok() && !player.getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return r.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** What planting gave: done, or why not. */
    public record Result(boolean ok, Component message, Optional<ClaimBanner> banner) {}

    /** Plants a banner at {@code at} and claims the land, if allowed. */
    public static Result plant(ServerPlayer player, ServerLevel level, BlockPos at,
            net.minecraft.world.item.ItemStack stack) {
        LandSettings s = LandSettings.get();
        Claims claims = Claims.get(player.server);
        int cx = at.getX() >> 4;
        int cz = at.getZ() >> 4;
        Optional<Colony> colony = Colonies.get(player.server).all().stream()
                .filter(c -> c.dimension().equals(level.dimension())
                        && Permissions.can(player.getUUID(), c, Perm.BANNERS)
                        && Math.max(Math.abs((c.hall().getX() >> 4) - cx), Math.abs((c.hall().getZ() >> 4) - cz))
                                <= s.bannerReach())
                .min(Comparator.comparingDouble(c -> c.hall().distSqr(at)));
        Owner owner;
        int radius;
        if (colony.isPresent()) {
            Colony c = colony.get();
            int allowed = s.bannersBase() + (s.citizensPerBanner() > 0
                    ? Society.citizenCount(player.server, c) / s.citizensPerBanner() : 0);
            long placed = claims.ownedBy(c.asOwner()).stream().filter(x -> x.banner().isPresent()).count();
            if (placed >= allowed) {
                return fail("land.lotr_port.banner_limit", "%s may hold %s banners; grow it for more", c.name(),
                        allowed);
            }
            owner = c.asOwner();
            radius = s.bannerRadius();
        } else {
            owner = Owner.player(player.getUUID());
            long placed = claims.ownedBy(owner).size();
            if (placed >= s.personalBanners()) {
                return fail("land.lotr_port.personal_limit", "You may plant %s banners outside a colony",
                        s.personalBanners());
            }
            radius = s.personalRadius();
        }
        ClaimBanner banner = LandEntities.BANNER.get().create(level);
        if (banner == null) {
            return fail("land.lotr_port.no_banner", "The banner could not be planted");
        }
        UUID id = banner.getUUID();
        Claim claim = Claim.around(id, owner, level.dimension(), at, radius, Optional.of(at.immutable()));
        Optional<Claim> clash = claims.conflict(claim);
        if (clash.isPresent()) {
            return fail("land.lotr_port.taken", "This land belongs to %s",
                    Protection.ownerName(player.server, clash.get()));
        }
        banner.setItem(stack);
        banner.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180, 0);
        claims.add(player.server, claim);
        level.addFreshEntity(banner);
        return new Result(true, colony.isPresent()
                ? Component.translatableWithFallback("land.lotr_port.claimed_colony", "Land claimed for %s",
                        colony.get().name())
                : Component.translatableWithFallback("land.lotr_port.claimed_personal", "Land claimed"),
                Optional.of(banner));
    }

    private static Result fail(String key, String fallback, Object... args) {
        return new Result(false, Component.translatableWithFallback(key, fallback, args), Optional.empty());
    }
}
