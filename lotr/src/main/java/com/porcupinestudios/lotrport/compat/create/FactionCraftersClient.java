package com.porcupinestudios.lotrport.compat.create;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterRenderer;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Faction crafters on the client: Create's renderer and cogwheel visual (as AllBlockEntityTypes.MECHANICAL_CRAFTER),
 * Create's crafter models with every quad tinted (tint index 0) in the faction's colour.
 */
public final class FactionCraftersClient {
    private FactionCraftersClient() {}

    static void register(IEventBus modBus) {
        modBus.addListener(FactionCraftersClient::setup);
        modBus.addListener(FactionCraftersClient::renderers);
        modBus.addListener(FactionCraftersClient::models);
        modBus.addListener(FactionCraftersClient::blockColors);
        modBus.addListener(FactionCraftersClient::itemColors);
    }

    @SuppressWarnings("deprecation")
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (DeferredBlock<FactionCrafterBlock> b : FactionCrafters.CRAFTERS) {
                ItemBlockRenderTypes.setRenderLayer(b.get(), RenderType.cutoutMipped());
            }
            SimpleBlockEntityVisualizer.builder(FactionCrafters.BLOCK_ENTITY.get())
                    .factory(SingleAxisRotatingVisual.of(AllPartialModels.SHAFTLESS_COGWHEEL))
                    .skipVanillaRender(be -> false)
                    .apply();
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FactionCrafters.BLOCK_ENTITY.get(), MechanicalCrafterRenderer::new);
    }

    /** Every quad of our crafters' block and item models gets tint index 0. */
    private static void models(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (DeferredBlock<FactionCrafterBlock> b : FactionCrafters.CRAFTERS) {
            for (BlockState state : b.get().getStateDefinition().getPossibleStates()) {
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                models.computeIfPresent(key, (k, m) -> new Tinted(m));
            }
            models.computeIfPresent(ModelResourceLocation.inventory(BuiltInRegistries.BLOCK.getKey(b.get())),
                    (k, m) -> new Tinted(m));
        }
    }

    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (DeferredBlock<FactionCrafterBlock> b : FactionCrafters.CRAFTERS) {
            int tint = b.get().tint();
            event.register((state, level, pos, index) -> index == 0 ? tint : -1, b.get());
        }
    }

    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        for (DeferredBlock<FactionCrafterBlock> b : FactionCrafters.CRAFTERS) {
            int tint = 0xFF000000 | b.get().tint();
            event.register((stack, index) -> index == 0 ? tint : -1, b.get());
        }
    }

    private static final class Tinted extends BakedModelWrapper<BakedModel> {
        Tinted(BakedModel model) {
            super(model);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                RenderType type) {
            return tint(super.getQuads(state, side, rand, data, type));
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return tint(super.getQuads(state, side, rand));
        }

        private static List<BakedQuad> tint(List<BakedQuad> quads) {
            List<BakedQuad> out = new ArrayList<>(quads.size());
            for (BakedQuad q : quads) {
                out.add(q.isTinted() ? q : new BakedQuad(q.getVertices(), 0, q.getDirection(), q.getSprite(),
                        q.isShade(), q.hasAmbientOcclusion()));
            }
            return out;
        }
    }
}
