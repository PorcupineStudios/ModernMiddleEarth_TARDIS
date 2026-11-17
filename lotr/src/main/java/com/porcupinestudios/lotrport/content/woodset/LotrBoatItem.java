package com.porcupinestudios.lotrport.content.woodset;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A LOTR wood's boat or chest boat: placed where you look (on water or ground), as vanilla's boat items. */
public class LotrBoatItem extends Item {
    private final String wood;
    private final boolean chest;

    public LotrBoatItem(Properties properties, String wood, boolean chest) {
        super(properties);
        this.wood = wood;
        this.chest = chest;
    }

    /** A boat of this item's wood, not yet in the world. */
    public Boat create(Level level, Vec3 at, float yaw) {
        Boat boat;
        if (chest) {
            LotrChestBoat b = new LotrChestBoat(WoodSets.CHEST_BOAT.get(), level);
            b.setWood(wood);
            boat = b;
        } else {
            LotrBoat b = new LotrBoat(WoodSets.BOAT.get(), level);
            b.setWood(wood);
            boat = b;
        }
        boat.setPos(at.x, at.y, at.z);
        boat.setYRot(yaw);
        return boat;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        Boat boat = create(level, hit.getLocation(), player.getYRot());
        if (!level.noCollision(boat, boat.getBoundingBox())) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            level.addFreshEntity(boat);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getLocation());
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
