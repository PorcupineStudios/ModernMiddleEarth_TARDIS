package com.porcupinestudios.tardisrb.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (the 1.21 WAILA, optional): looking at a console control names it, says what it does and how it is set
 * (Aaron, 2026-11-08: "hit boxes that are WAILA compatible"). Jade finds this class by its annotation; it is only
 * loaded when Jade is installed. Everything is client side ({@link JadeConsole}).
 */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        JadeConsole.register(registration);
    }
}
