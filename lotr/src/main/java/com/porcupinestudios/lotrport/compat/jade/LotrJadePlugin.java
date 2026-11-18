package com.porcupinestudios.lotrport.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (the 1.21 WAILA, optional): a set-down drink shows what it holds. Jade finds this class by its annotation and
 * only loads it when Jade is installed. Client side only ({@link JadeVessel}).
 */
@WailaPlugin
public final class LotrJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(JadeVessel.INSTANCE, com.porcupinestudios.lotrport.drink.VesselBlock.class);
    }
}
