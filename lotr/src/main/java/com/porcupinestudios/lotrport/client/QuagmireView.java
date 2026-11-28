package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.Hazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * With your eyes in quagmire you see mud, not the block's inside faces: thick fog of its colour, as lava and powder
 * snow do (#96). Colour and distances: behaviors.json "quagmire_feel".
 */
final class QuagmireView {
    private QuagmireView() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(QuagmireView::fog);
        NeoForge.EVENT_BUS.addListener(QuagmireView::fogColor);
    }

    private static boolean inMud(ViewportEvent e) {
        var camera = e.getCamera();
        BlockState state = camera.getEntity().level().getBlockState(BlockPos.containing(camera.getPosition()));
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals(LotrPort.MOD_ID) && Hazards.of(id.getPath()) == Hazards.Hazard.QUAGMIRE;
    }

    private static void fog(ViewportEvent.RenderFog e) {
        if (inMud(e)) {
            e.setNearPlaneDistance((float) Hazards.feel("fog_near"));
            e.setFarPlaneDistance((float) Hazards.feel("fog_far"));
            e.setCanceled(true);
        }
    }

    private static void fogColor(ViewportEvent.ComputeFogColor e) {
        if (inMud(e)) {
            int c = Integer.parseInt(com.porcupinestudios.lotrport.content.ContentData.behaviours()
                    .getAsJsonObject("quagmire_feel").get("fog_color").getAsString(), 16);
            e.setRed((c >> 16 & 0xFF) / 255f);
            e.setGreen((c >> 8 & 0xFF) / 255f);
            e.setBlue((c & 0xFF) / 255f);
        }
    }
}
