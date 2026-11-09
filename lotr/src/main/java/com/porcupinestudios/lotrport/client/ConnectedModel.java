package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.content.GateBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;

/**
 * LOTR's connected textures (gates, portcullises, connected bricks): model loader {@code lotr_port:connected}. Every
 * face is drawn a quarter at a time from a sheet the converter made ({@code assets.py sheet}): five tiles, picked per
 * quarter by whether the face's neighbours beside and above/below it are connected, and whether the one diagonal to it
 * is (an inside corner). Row 0 is shut, row 1 open (LOTR drew open gates without their base: only the frame).
 * Shapes: {@code cube}, {@code gate} (a 4 px panel across the facing axis), {@code full_gate} (a whole block while
 * shut).
 */
public final class ConnectedModel implements IDynamicBakedModel {
    public static final IGeometryLoader<Geometry> LOADER = (json, ctx) -> new Geometry(GsonHelper.getAsString(json,
            "shape", "cube"));
    /** Per face (Direction 3D order) 9 bits: connected left, right, up, down, up-left, up-right, down-left, down-right
     * in the face's own texture directions, and hidden (a connected gate on the other side). */
    private static final ModelProperty<Long> MASK = new ModelProperty<>();
    private static final int HIDDEN = 1 << 8;
    /** The world direction of a face's texture right (+u) and down (+v), as vanilla's default face UVs run. */
    private static final Direction[] RIGHT = {Direction.EAST, Direction.EAST, Direction.WEST, Direction.EAST,
            Direction.SOUTH, Direction.NORTH};
    private static final Direction[] DOWN = {Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.DOWN,
            Direction.DOWN, Direction.DOWN};

    private final String shape;
    private final TextureAtlasSprite sheet;
    private final TextureAtlasSprite particle;
    /** Baked quads by neighbour mask + open + axis, each split by cull face (index 6: none). */
    private final Map<Long, List<List<BakedQuad>>> cache = new ConcurrentHashMap<>();
    private static final FaceBakery BAKERY = new FaceBakery();

    private ConnectedModel(String shape, TextureAtlasSprite sheet, TextureAtlasSprite particle) {
        this.shape = shape;
        this.sheet = sheet;
        this.particle = particle;
    }

    public record Geometry(String shape) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
            return new ConnectedModel(shape, sprites.apply(context.getMaterial("sheet")),
                    sprites.apply(context.getMaterial("particle")));
        }
    }

    // ------------------------------------------------------------------ neighbours

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        long mask = 0;
        for (Direction face : Direction.values()) {
            Direction right = RIGHT[face.ordinal()];
            Direction down = DOWN[face.ordinal()];
            BlockPos l = pos.relative(right.getOpposite());
            BlockPos r = pos.relative(right);
            BlockPos u = pos.relative(down.getOpposite());
            BlockPos d = pos.relative(down);
            int bits = bit(level, pos, state, l, 0) | bit(level, pos, state, r, 1) | bit(level, pos, state, u, 2)
                    | bit(level, pos, state, d, 3) | bit(level, pos, state, l.relative(down.getOpposite()), 4)
                    | bit(level, pos, state, r.relative(down.getOpposite()), 5)
                    | bit(level, pos, state, l.relative(down), 6) | bit(level, pos, state, r.relative(down), 7);
            BlockPos across = pos.relative(face);
            if (state.getBlock() instanceof GateBlock && level.getBlockState(across).getBlock() instanceof GateBlock
                    && GateBlock.connects(level, pos, state, across)) {
                bits |= HIDDEN;
            }
            mask |= (long) bits << (face.ordinal() * 9);
        }
        return ModelData.builder().with(MASK, mask).build();
    }

    private static int bit(BlockGetter level, BlockPos pos, BlockState state, BlockPos other, int bit) {
        return GateBlock.connects(level, pos, state, other) ? 1 << bit : 0;
    }

    // ------------------------------------------------------------------ quads

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
            RenderType renderType) {
        Long mask = data.get(MASK);
        boolean gate = state != null && state.getBlock() instanceof GateBlock;
        boolean open = gate && state.getValue(GateBlock.OPEN);
        Direction.Axis axis = gate ? state.getValue(GateBlock.FACING).getAxis() : Direction.Axis.Y;
        long key = (mask == null ? 0 : mask) | (open ? 1L << 54 : 0) | ((long) axis.ordinal() << 55);
        return cache.computeIfAbsent(key, k -> bake(k & ((1L << 54) - 1), open, axis, gate))
                .get(side == null ? 6 : side.ordinal());
    }

    private List<List<BakedQuad>> bake(long mask, boolean open, Direction.Axis axis, boolean gate) {
        float[] box = box(gate && !(shape.equals("full_gate") && !open), axis);
        List<List<BakedQuad>> quads = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            quads.add(new ArrayList<>());
        }
        for (Direction face : Direction.values()) {
            int bits = (int) (mask >> (face.ordinal() * 9)) & 0x1FF;
            if ((bits & HIDDEN) != 0) {
                continue;
            }
            float[] uv = defaultUv(face, box);
            boolean edge = switch (face) {
                case DOWN -> box[1] == 0;
                case UP -> box[4] == 16;
                case NORTH -> box[2] == 0;
                case SOUTH -> box[5] == 16;
                case WEST -> box[0] == 0;
                case EAST -> box[3] == 16;
            };
            for (int qu = 0; qu < 2; qu++) {
                for (int qv = 0; qv < 2; qv++) {
                    float u1 = Math.max(uv[0], qu * 8), u2 = Math.min(uv[2], qu * 8 + 8);
                    float v1 = Math.max(uv[1], qv * 8), v2 = Math.min(uv[3], qv * 8 + 8);
                    if (u1 >= u2 || v1 >= v2) {
                        continue;
                    }
                    boolean h = (bits & (1 << qu)) != 0;
                    boolean v = (bits & (1 << (2 + qv))) != 0;
                    boolean diag = (bits & (1 << (4 + qv * 2 + qu))) != 0;
                    int tile = !h && !v ? 0 : h && !v ? 1 : !h ? 2 : diag ? 4 : 3;
                    int row = open ? 1 : 0;
                    float[] sub = sub(face, box, u1, v1, u2, v2);
                    BlockFaceUV faceUv = new BlockFaceUV(new float[] {(tile * 16 + u1) / 5f, (row * 16 + v1) / 2f,
                            (tile * 16 + u2) / 5f, (row * 16 + v2) / 2f}, 0);
                    Direction cull = edge ? face : null; // only faces on the block's edge are hidden by neighbours
                    quads.get(edge ? face.ordinal() : 6).add(BAKERY.bakeQuad(new Vector3f(sub[0], sub[1], sub[2]),
                            new Vector3f(sub[3], sub[4], sub[5]), new BlockElementFace(cull, -1, "", faceUv), sheet,
                            face, BlockModelRotation.X0_Y0, null, true));
                }
            }
        }
        return quads;
    }

    /** from/to of the shape: a cube, or a gate's 4 px panel across its facing axis. */
    private static float[] box(boolean panel, Direction.Axis axis) {
        if (!panel) {
            return new float[] {0, 0, 0, 16, 16, 16};
        }
        return switch (axis) {
            case X -> new float[] {6, 0, 0, 10, 16, 16};
            case Y -> new float[] {0, 6, 0, 16, 10, 16};
            case Z -> new float[] {0, 0, 6, 16, 16, 10};
        };
    }

    /** Vanilla's default UV rectangle of a box face (u1, v1, u2, v2). */
    private static float[] defaultUv(Direction face, float[] b) {
        return switch (face) {
            case DOWN -> new float[] {b[0], 16 - b[5], b[3], 16 - b[2]};
            case UP -> new float[] {b[0], b[2], b[3], b[5]};
            case NORTH -> new float[] {16 - b[3], 16 - b[4], 16 - b[0], 16 - b[1]};
            case SOUTH -> new float[] {b[0], 16 - b[4], b[3], 16 - b[1]};
            case WEST -> new float[] {b[2], 16 - b[4], b[5], 16 - b[1]};
            case EAST -> new float[] {16 - b[5], 16 - b[4], 16 - b[2], 16 - b[1]};
        };
    }

    /** The part of the box's face whose default UVs are u1..u2, v1..v2 (the inverse of {@link #defaultUv}). */
    private static float[] sub(Direction face, float[] b, float u1, float v1, float u2, float v2) {
        return switch (face) {
            case DOWN -> new float[] {u1, b[1], 16 - v2, u2, b[4], 16 - v1};
            case UP -> new float[] {u1, b[1], v1, u2, b[4], v2};
            case NORTH -> new float[] {16 - u2, 16 - v2, b[2], 16 - u1, 16 - v1, b[5]};
            case SOUTH -> new float[] {u1, 16 - v2, b[2], u2, 16 - v1, b[5]};
            case WEST -> new float[] {b[0], 16 - v2, u1, b[3], 16 - v1, u2};
            case EAST -> new float[] {b[0], 16 - v2, 16 - u2, b[3], 16 - v1, 16 - u1};
        };
    }

    // ------------------------------------------------------------------ the rest of BakedModel

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout()); // every connected model is cutout (generate.py)
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
