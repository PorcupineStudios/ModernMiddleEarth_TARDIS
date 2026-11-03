package com.porcupinestudios.lotrexport;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/**
 * Reads the running LOTR 36.15 game and writes its content as JSON for the 1.21.1 port. It does nothing unless the
 * game is started with {@code -Dlotrexport.run=true}; then it exports once the main menu is up and quits. Output goes
 * to {@code -Dlotrexport.out} (default {@code export/}).
 */
@Mod(modid = "lotrexport", name = "LOTR Exporter", version = "1", dependencies = "required-after:lotr")
public class LotrExport {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient() && Boolean.getBoolean("lotrexport.run")) {
            FMLCommonHandler.instance().bus().register(new ExportRunner());
        }
    }
}
