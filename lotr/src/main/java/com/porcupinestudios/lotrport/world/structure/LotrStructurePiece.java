package com.porcupinestudios.lotrport.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * One LOTR structure's template, turned about its middle, with LOTR's foundations: under every block of the ground
 * layer, open space (air, water, plants) is filled down to solid ground, with dirt under grass and dirt and a copy
 * of the block above otherwise (stone under stone, cobble under cobble), as LOTR's generators filled downwards.
 */
public final class LotrStructurePiece extends TemplateStructurePiece {
    private final int ground;
    private final int maxFoundation;

    LotrStructurePiece(StructureTemplateManager manager, ResourceLocation id, BlockPos middle, Rotation rotation,
            int ground, int maxFoundation) {
        super(LotrStructures.PIECE.get(), 0, manager, id, id.toString(), settings(manager, id, rotation),
                corner(manager, id, middle));
        this.ground = ground;
        this.maxFoundation = maxFoundation;
    }

    public LotrStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(LotrStructures.PIECE.get(), tag, manager,
                id -> settings(manager, id, Rotation.valueOf(tag.getString("Rot"))));
        this.ground = tag.getInt("Ground");
        this.maxFoundation = tag.getInt("MaxFoundation");
    }

    private static StructurePlaceSettings settings(StructureTemplateManager manager, ResourceLocation id,
            Rotation rotation) {
        Vec3i size = manager.getOrCreate(id).getSize();
        return new StructurePlaceSettings().setRotation(rotation)
                .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
    }

    private static BlockPos corner(StructureTemplateManager manager, ResourceLocation id, BlockPos middle) {
        Vec3i size = manager.getOrCreate(id).getSize();
        return middle.offset(-size.getX() / 2, 0, -size.getZ() / 2);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
        tag.putInt("Ground", ground);
        tag.putInt("MaxFoundation", maxFoundation);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random,
            BoundingBox box) {
        // LOTR's templates carry no data markers
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, box, chunk, pivot);
        int y = templatePosition.getY() + ground;
        BoundingBox mine = getBoundingBox();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(mine.minX(), box.minX()); x <= Math.min(mine.maxX(), box.maxX()); x++) {
            for (int z = Math.max(mine.minZ(), box.minZ()); z <= Math.min(mine.maxZ(), box.maxZ()); z++) {
                BlockState top = level.getBlockState(at.set(x, y, z));
                // paths and farmland (porches, garden beds) aren't full blocks but stand on dirt all the same
                boolean soil = top.is(Blocks.DIRT_PATH) || top.is(Blocks.FARMLAND);
                if (top.isAir() || !soil && !top.isCollisionShapeFullBlock(level, at)) {
                    continue;
                }
                BlockState fill = soil || top.is(BlockTags.DIRT) ? Blocks.DIRT.defaultBlockState() : top;
                for (int d = 1; d <= maxFoundation && y - d > level.getMinBuildHeight(); d++) {
                    BlockState below = level.getBlockState(at.set(x, y - d, z));
                    if (!open(below)) {
                        break;
                    }
                    level.setBlock(at, fill, 2);
                }
            }
        }
    }

    private static boolean open(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }
}
