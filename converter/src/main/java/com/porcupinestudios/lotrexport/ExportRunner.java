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
        int code = 0;
        try {
            new Exporter(new File(System.getProperty("lotrexport.out", "export"))).run();
        } catch (Throwable t) {
            t.printStackTrace();
            code = 1;
        }
        System.out.println("[lotrexport] finished with code " + code);
        System.out.flush();
        Runtime.getRuntime().halt(code);
    }
}
