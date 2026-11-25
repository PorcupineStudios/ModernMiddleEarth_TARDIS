package com.porcupinestudios.lotrport.compat.create;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Create's mechanical crafter, one per faction: it is a mechanical crafter in every way (Create treats it as one:
 * {@code mixin/create/BlockEntryMixin}), and a grid holding one also crafts that faction's table recipes
 * ({@code mixin/create/CrafterRecipeMixin}).
 */
public class FactionCrafterBlock extends MechanicalCrafterBlock {
    private final ResourceLocation table;
    private final int tint;

    public FactionCrafterBlock(Properties properties, ResourceLocation table, int tint) {
        super(properties);
        this.table = table;
        this.tint = tint;
    }

    /** The faction table whose recipes this crafter knows. */
    public ResourceLocation table() {
        return table;
    }

    /** The faction's colour the model is tinted with (0xRRGGBB). */
    public int tint() {
        return tint;
    }

    @Override
    public BlockEntityType<? extends MechanicalCrafterBlockEntity> getBlockEntityType() {
        return FactionCrafters.BLOCK_ENTITY.get();
    }
}
