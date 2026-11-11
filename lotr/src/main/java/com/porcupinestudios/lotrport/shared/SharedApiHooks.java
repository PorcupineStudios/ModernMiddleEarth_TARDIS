package com.porcupinestudios.lotrport.shared;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.LotrWorldData;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.provider.PlanetSurface;
import com.porcupinestudios.ringbearer.api.provider.RegisterProvidersEvent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
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
        // Middle-earth is a planet at full size (Aaron, 2026-11-04): exactly LOTR's map, looping at its edges, and the
        // globe seen from space is painted from the map.
        modBus.addListener((RegisterProvidersEvent event) -> event.planet(level ->
                LotrDimensions.isMiddleEarth(level) ? Optional.of(middleEarthPlanet()) : Optional.empty()));
        // Colonists research TARDIS tech for their founder's crew.
        modBus.addListener((RegisterProvidersEvent event) -> event.researchers((server, player) ->
                Colonies.get(server).researchers(server, player)));
    }

    /** Middle-earth's planet surface: exactly the map. */
    public static PlanetSurface middleEarthPlanet() {
        MiddleEarthMap map = MiddleEarthMap.get();
        BlockPos min = map.blockOfPixel(0, 0);
        BlockPos max = map.blockOfPixel(map.width(), map.height());
        return new PlanetSurface(min.getX(), max.getX(), min.getZ(), max.getZ(),
                Optional.of(new PlanetSurface.Picture(LotrPort.id("textures/gui/map.png"), min.getX(), max.getX(),
                        min.getZ(), max.getZ(), -1)));
    }
}
