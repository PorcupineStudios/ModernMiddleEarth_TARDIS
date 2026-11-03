package com.porcupinestudios.lotrexport;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Waits for the main menu (all textures stitched), runs the export, then (unless {@code -Dlotrexport.structures=false})
 * opens a flat test world for {@link StructureExporter}, and stops the game when that is done.
 */
public class ExportRunner {
    private boolean done;
    private StructureExporter structures;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (structures != null) {
            if (structures.finished) {
                System.out.println("[lotrexport] finished");
                mc.shutdown();
            }
            return;
        }
        if (done || !(mc.currentScreen instanceof GuiMainMenu)) {
            return;
        }
        done = true;
        File out = new File(System.getProperty("lotrexport.out", "export"));
        try {
            new Exporter(out).run();
        } catch (Throwable t) {
            // CI checks that blocks.json exists, so a failed export fails the job.
            t.printStackTrace();
        }
        if (Boolean.parseBoolean(System.getProperty("lotrexport.structures", "true"))) {
            try {
                structures = new StructureExporter(out);
                FMLCommonHandler.instance().bus().register(structures);
                WorldSettings settings = new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false,
                        WorldType.FLAT);
                mc.launchIntegratedServer("lotrexport-structures", "lotrexport-structures", settings);
                return;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        System.out.println("[lotrexport] finished");
        // FML traps System.exit and Runtime.halt; a normal shutdown ends the game loop with exit code 0.
        mc.shutdown();
    }
}
