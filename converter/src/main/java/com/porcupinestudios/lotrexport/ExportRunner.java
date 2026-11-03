package com.porcupinestudios.lotrexport;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;

/** Waits for the main menu (all textures stitched), runs the export, then stops the game. */
public class ExportRunner {
    private boolean done;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiMainMenu)) {
            return;
        }
        done = true;
        try {
            new Exporter(new File(System.getProperty("lotrexport.out", "export"))).run();
            System.out.println("[lotrexport] finished");
        } catch (Throwable t) {
            // CI checks that blocks.json exists, so a failed export fails the job.
            t.printStackTrace();
        }
        // FML traps System.exit and Runtime.halt; a normal shutdown ends the game loop with exit code 0.
        Minecraft.getMinecraft().shutdown();
    }
}
