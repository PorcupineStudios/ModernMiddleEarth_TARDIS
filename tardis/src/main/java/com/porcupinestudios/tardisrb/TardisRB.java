package com.porcupinestudios.tardisrb;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.command.TardisCommand;
import com.porcupinestudios.tardisrb.compat.create.CreateCompat;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
import com.porcupinestudios.tardisrb.flight.CrashSite;
import com.porcupinestudios.tardisrb.flight.Piloting;
import com.porcupinestudios.tardisrb.gametest.WorldCheck;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
import com.porcupinestudios.tardisrb.net.DoorNet;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.net.WorkshopNet;
import com.porcupinestudios.tardisrb.repair.WorldDamage;
import com.porcupinestudios.tardisrb.research.Researching;
import com.porcupinestudios.tardisrb.space.SpaceTravel;
import com.porcupinestudios.tardisrb.progression.TardisConditions;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import net.minecraft.resources.ResourceLocation;
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
        TardisConditions.register(modBus);
        TardisContent.register();
        TardisCommand.register();
        TardisBlocks.register(modBus);
        TardisEntities.register(modBus);
        Piloting.register();
        ConsoleNet.register(modBus);
        DoorNet.register(modBus);
        CrashSite.register();
        CreateCompat.register(modBus);
        WorldCheck.register();
        Researching.register();
        WorldDamage.register();
        Doors.register();
        WorkshopNet.register(modBus);
        SkyNet.register(modBus);
        SpaceTravel.register();
        LOG.info("TARDIS: Ring Bearer loading");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
