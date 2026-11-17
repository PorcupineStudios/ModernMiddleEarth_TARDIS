package com.porcupinestudios.lotrport.client;

import com.mojang.datafixers.util.Pair;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.woodset.LotrBoat;
import com.porcupinestudios.lotrport.content.woodset.LotrChestBoat;
import com.porcupinestudios.lotrport.content.woodset.WoodSets;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * LOTR woods' signs, hanging signs and boats. Vanilla builds a sign model and sign picture for every registered
 * wood type, ours included (they register before the client starts); this adds the renderers for our block entity
 * types and draws our boats on vanilla's oak boat model with the wood's own picture
 * ({@code textures/entity/boat/<wood>.png}, {@code chest_boat/<wood>.png}).
 */
public final class WoodSetRenderers {
    private WoodSetRenderers() {}

    /** Client setup: the sign pictures, in case the sheets were made before our wood types existed. */
    public static void setup() {
        WoodSets.types().values().forEach(Sheets::addWoodType);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WoodSets.SIGN.get(), SignRenderer::new);
        event.registerBlockEntityRenderer(WoodSets.HANGING_SIGN.get(), HangingSignRenderer::new);
        event.registerEntityRenderer(WoodSets.BOAT.get(), ctx -> new LotrBoatRenderer(ctx, false));
        event.registerEntityRenderer(WoodSets.CHEST_BOAT.get(), ctx -> new LotrBoatRenderer(ctx, true));
    }

    static final class LotrBoatRenderer extends BoatRenderer {
        private final ListModel<Boat> model;
        private final boolean chest;

        LotrBoatRenderer(EntityRendererProvider.Context ctx, boolean chest) {
            super(ctx, chest);
            this.chest = chest;
            this.model = chest ? new ChestBoatModel(ctx.bakeLayer(ModelLayers.createChestBoatModelName(Boat.Type.OAK)))
                    : new BoatModel(ctx.bakeLayer(ModelLayers.createBoatModelName(Boat.Type.OAK)));
        }

        @Override
        public Pair<ResourceLocation, ListModel<Boat>> getModelWithLocation(Boat boat) {
            String wood = boat instanceof LotrBoat b ? b.wood() : boat instanceof LotrChestBoat c ? c.wood() : "oak";
            return Pair.of(LotrPort.id("textures/entity/" + (chest ? "chest_boat/" : "boat/") + wood + ".png"), model);
        }
    }
}
