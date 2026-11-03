package com.porcupinestudios.tardisrb.compat.create;

import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.simibubi.create.api.stress.BlockStressValues;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Create hooks (Create is the one hard dependency): the winder's stress impact, read live from the tuning file. */
public final class CreateCompat {
    private CreateCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> BlockStressValues.IMPACTS.register(
                TardisBlocks.WINDER.get(), () -> TardisContent.tuning().winder().stress())));
    }
}
