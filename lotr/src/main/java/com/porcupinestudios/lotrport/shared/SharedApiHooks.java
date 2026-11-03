package com.porcupinestudios.lotrport.shared;

import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.LotrWorldData;
import com.porcupinestudios.ringbearer.api.provider.RegisterProvidersEvent;
import java.util.Optional;
import net.neoforged.bus.api.IEventBus;

/** Everything this mod offers through the shared Ring Bearer API. Conditions join here in phase 4. */
public final class SharedApiHooks {
    private SharedApiHooks() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterProvidersEvent event) -> event.crashSite(server ->
                LotrWorldData.get(server).startsInMiddleEarth()
                        ? Optional.of(LotrDimensions.MIDDLE_EARTH)
                        : Optional.empty()));
    }
}
