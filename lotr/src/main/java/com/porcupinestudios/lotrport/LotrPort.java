package com.porcupinestudios.lotrport;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.lotrport.command.FactionCommand;
import com.porcupinestudios.lotrport.command.LotrCommand;
import com.porcupinestudios.lotrport.command.NpcCommand;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import com.porcupinestudios.lotrport.content.LotrItems;
import com.porcupinestudios.lotrport.content.LotrTabs;
import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.combat.AttackTiming;
import com.porcupinestudios.lotrport.faction.AlignmentHud;
import com.porcupinestudios.lotrport.faction.FactionConditions;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrEntities;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.npc.NpcSpawner;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import com.porcupinestudios.lotrport.quest.QuestData;
import com.porcupinestudios.lotrport.quest.Quests;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.net.MapNet;
import com.porcupinestudios.lotrport.net.HudNet;
import com.porcupinestudios.lotrport.net.QuestNet;
import com.porcupinestudios.lotrport.command.TravelCommand;
import com.porcupinestudios.lotrport.ai.AiCommand;
import com.porcupinestudios.lotrport.ai.AiData;
import com.porcupinestudios.lotrport.ai.AiRulers;
import com.porcupinestudios.lotrport.colony.ColonyCommand;
import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.colony.ColonyItems;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Migrants;
import com.porcupinestudios.lotrport.colony.Recruiting;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import com.porcupinestudios.lotrport.shared.SharedApiHooks;
import com.porcupinestudios.lotrport.world.LotrWorldgen;
import com.porcupinestudios.lotrport.world.WorldConditions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(LotrPort.MOD_ID)
public final class LotrPort {
    public static final String MOD_ID = "lotr_port";
    public static final Logger LOG = LogUtils.getLogger();

    public LotrPort(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, LotrConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, LotrClientConfig.SPEC);
        LotrRegistries.register(modBus);
        LotrSounds.register(modBus);
        LotrBlocks.register(modBus);
        com.porcupinestudios.lotrport.content.LotrComponents.register(modBus);
        LotrItems.register(modBus);
        LotrTabs.register(modBus);
        LotrRecipes.register(modBus);
        LotrWorldgen.register(modBus);
        LotrEntities.register(modBus);
        SharedApiHooks.register(modBus);
        FactionConditions.register(modBus);
        WorldConditions.register(modBus);
        Factions.register();
        FactionKills.register();
        NpcData.register();
        NpcSpawns.register();
        NpcSpawner.register();
        QuestData.register();
        com.porcupinestudios.lotrport.content.SaplingRules.register();
        com.porcupinestudios.lotrport.tree.TreeRecords.register();
        Quests.register();
        FastTravel.register();
        MapNet.register(modBus);
        QuestNet.register(modBus);
        HudNet.register(modBus);
        AlignmentHud.register();
        AttackTiming.register();
        ColonyItems.register(modBus);
        ColonyData.register();
        Colonies.register();
        Migrants.register();
        AiData.register();
        AiRulers.register();
        Recruiting.register();
        com.porcupinestudios.lotrport.npc.InvasionSpawner.register();
        com.porcupinestudios.lotrport.progress.Achievements.register();
        com.porcupinestudios.lotrport.progress.Titles.register();
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            LotrCommand.register(event.getDispatcher());
            FactionCommand.register(event.getDispatcher());
            NpcCommand.register(event.getDispatcher());
            TravelCommand.register(event.getDispatcher());
            ColonyCommand.register(event.getDispatcher());
            AiCommand.register(event.getDispatcher());
            com.porcupinestudios.lotrport.progress.Titles.registerCommands(event.getDispatcher());
            com.porcupinestudios.lotrport.progress.Cosmetics.registerCommands(event.getDispatcher());
            com.porcupinestudios.lotrport.progress.Calendar.registerCommands(event.getDispatcher());
            AlignmentHud.registerCommands(event.getDispatcher());
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
