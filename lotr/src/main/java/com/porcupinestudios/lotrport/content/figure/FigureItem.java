package com.porcupinestudios.lotrport.content.figure;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.content.ContentData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Sets a {@link Figure} down on the top of a block, facing the player (troll statues, rugs). */
public class FigureItem extends Item {
    private final JsonObject spec;

    public FigureItem(Properties properties, JsonObject spec) {
        super(properties);
        this.spec = spec;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos().above();
        if (!level.getEntitiesOfClass(Figure.class, new AABB(pos)).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            Figure f = Figures.TYPE.get().create(level);
            if (f == null) {
                return InteractionResult.FAIL;
            }
            f.setup(ContentData.str(spec, "model", ""), ContentData.str(spec, "texture", ""),
                    ContentData.bool(spec, "flat", false), ctx.getItemInHand());
            float yaw = ctx.getPlayer() == null ? 0 : ctx.getPlayer().getYRot() + 180f;
            f.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
            level.addFreshEntity(f);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
