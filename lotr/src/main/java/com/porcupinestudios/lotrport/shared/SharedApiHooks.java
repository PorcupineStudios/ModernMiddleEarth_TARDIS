package com.porcupinestudios.lotrport.shared;

import com.porcupinestudios.lotrport.world.LotrWorldData;
import com.porcupinestudios.ringbearer.api.provider.RegisterProvidersEvent;
import java.util.Optional;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;

/**
 * Everything this mod offers through the shared Ring Bearer API. Condition types are registered by
 * {@link com.porcupinestudios.lotrport.faction.FactionConditions}.
 */
public final class SharedApiHooks {
    private SharedApiHooks() {}

    public static void register(IEventBus modBus) {
        // In a Middle-earth world the overworld is Middle-earth, so the wreck lands at its spawn (the Shire).
        modBus.addListener((RegisterProvidersEvent event) -> event.crashSite(server ->
                LotrWorldData.get(server).startsInMiddleEarth()
                        ? Optional.of(Level.OVERWORLD)
                        : Optional.empty()));
    }
}
