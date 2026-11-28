package com.porcupinestudios.lotrport.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;

/**
 * LOTR's clover petals (model loader {@code lotr_port:petals}): {@code count} flat square petals round the block's
 * middle at exactly 360/count degrees from each other, each a little above the last so they don't flicker where they
 * meet. A JSON block model can only turn an element by 22.5 degrees, which can't make three petals 120 apart, so the
 * quads are baked here with {@link BlockElementRotation} (its angle is only checked when read from JSON).
 * Fields: {@code petals} (3 or 4), {@code height} (pixels, the lowest petal's), {@code size} (pixels, the quad's side),
 * {@code step} (pixels each petal is above the last), textures {@code petal} and {@code particle}. Tinted like grass.
 */
public final class PetalsModel implements IDynamicBakedModel {
    public static final IGeometryLoader<Geometry> LOADER = (json, ctx) -> new Geometry(
            GsonHelper.getAsInt(json, "petals", 3), GsonHelper.getAsFloat(json, "height", 5f),
            GsonHelper.getAsFloat(json, "size", 10f), GsonHelper.getAsFloat(json, "step", 0.15f));
    private static final FaceBakery BAKERY = new FaceBakery();

    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particle;

    private PetalsModel(List<BakedQuad> quads, TextureAtlasSprite particle) {
        this.quads = quads;
        this.particle = particle;
    }

    /** The angle of petal {@code i} of {@code count} round the block's middle, in degrees. */
    public static float angle(int count, int i) {
        return 360f * i / count;
    }

    public record Geometry(int petals, float height, float size, float step) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
            TextureAtlasSprite petal = sprites.apply(context.getMaterial("petal"));
            float lo = 8 - size / 2, hi = 8 + size / 2;
            List<BakedQuad> quads = new ArrayList<>();
            for (int i = 0; i < petals; i++) {
                float y = Math.round((height + step * i) * 100f) / 100f;
                BlockElementRotation turn = new BlockElementRotation(new Vector3f(0.5f, y / 16f, 0.5f),
                        Direction.Axis.Y, angle(petals, i), false);
                for (Direction face : new Direction[] {Direction.UP, Direction.DOWN}) {
                    float[] uv = face == Direction.UP ? new float[] {0, 0, 16, 16} : new float[] {0, 16, 16, 0};
                    quads.add(BAKERY.bakeQuad(new Vector3f(lo, y, lo), new Vector3f(hi, y, hi),
                            new BlockElementFace(null, 0, "#petal", new BlockFaceUV(uv, 0)), petal, face,
                            BlockModelRotation.X0_Y0, turn, false));
                }
            }
            return new PetalsModel(quads, sprites.apply(context.getMaterial("particle")));
        }
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
            RenderType renderType) {
        return side == null ? quads : List.of();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
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
