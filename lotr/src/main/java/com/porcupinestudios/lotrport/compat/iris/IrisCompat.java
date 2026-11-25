package com.porcupinestudios.lotrport.compat.iris;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/**
 * Iris (optional): whether a shader pack is on, through Iris's public API ({@code net.irisshaders.iris.api.v0.IrisApi})
 * by reflection, so the mod neither compiles against nor needs Iris. With a pack on, Iris draws the world into its
 * own targets and composites at the end of the frame, so {@code client/ColorGrade} draws over the finished frame
 * instead of after the level.
 */
public final class IrisCompat {
    private static boolean looked;
    private static Object api;
    private static Method inUse;

    private IrisCompat() {}

    public static boolean shaderPackInUse() {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("iris")) {
                try {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError e) {
                    com.mojang.logging.LogUtils.getLogger().warn("Iris is loaded but its API isn't: {}", e.toString());
                }
            }
        }
        if (inUse == null) {
            return false;
        }
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            inUse = null;
            return false;
        }
    }
}
