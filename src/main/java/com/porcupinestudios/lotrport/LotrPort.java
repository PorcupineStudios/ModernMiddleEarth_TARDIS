package com.porcupinestudios.lotrport;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LotrPort.MOD_ID)
public final class LotrPort {
    public static final String MOD_ID = "lotrport";
    public static final Logger LOG = LogUtils.getLogger();

    public LotrPort(IEventBus modBus, ModContainer container) {
        LOG.info("LOTR Port loading");
    }
}
