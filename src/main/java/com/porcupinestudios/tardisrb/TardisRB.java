package com.porcupinestudios.tardisrb;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TardisRB.MOD_ID)
public final class TardisRB {
    public static final String MOD_ID = "tardis_rb";
    public static final Logger LOG = LogUtils.getLogger();

    public TardisRB(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        LOG.info("TARDIS: Ring Bearer loading");
    }
}
