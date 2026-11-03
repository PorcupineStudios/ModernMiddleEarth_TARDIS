package com.porcupinestudios.lotrexport;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Waits for the main menu (all textures stitched), runs the export, then (unless {@code -Dlotrexport.structures=false})
 * the {@link StructureExporter} in a throwaway client world, and stops the game.
 */
public class ExportRunner {
    private boolean done;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
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
                WorldClient world = new WorldClient(new NetHandlerPlayClient(mc, null, null),
                        new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT), 0,
                        EnumDifficulty.NORMAL, mc.mcProfiler);
                new StructureExporter(out).run(world);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        System.out.println("[lotrexport] finished");
        // FML traps System.exit and Runtime.halt; a normal shutdown ends the game loop with exit code 0.
        mc.shutdown();
    }
}
