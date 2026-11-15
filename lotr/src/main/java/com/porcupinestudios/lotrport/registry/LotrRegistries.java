package com.porcupinestudios.lotrport.registry;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.combat.AttackTiming;
import com.porcupinestudios.lotrport.player.PlayerLotrData;
import com.porcupinestudios.lotrport.quest.QuestLog;
import com.porcupinestudios.lotrport.travel.TravelData;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers for things that don't belong to one content area. Blocks, items and entities get one register class per
 * area as they are added (replaces the old {@code LOTRMod} god class).
 */
public final class LotrRegistries {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LotrPort.MOD_ID);

    /** Per-player LOTR data; saved with the player and kept on death. */
    public static final Supplier<AttachmentType<PlayerLotrData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.builder(() -> PlayerLotrData.EMPTY)
                    .serialize(PlayerLotrData.CODEC)
                    .copyOnDeath()
                    .build());

    /** Per-player mini-quests; saved with the player and kept on death. */
    public static final Supplier<AttachmentType<QuestLog>> QUEST_LOG = ATTACHMENTS.register("quests",
            () -> AttachmentType.builder(() -> QuestLog.EMPTY).serialize(QuestLog.CODEC).copyOnDeath().build());

    /** Per-player fast travel state (unlocked regions, last travel); saved with the player and kept on death. */
    public static final Supplier<AttachmentType<TravelData>> TRAVEL = ATTACHMENTS.register("travel",
            () -> AttachmentType.builder(() -> TravelData.EMPTY).serialize(TravelData.CODEC).copyOnDeath().build());

    /** The colony a citizen lives in (its id, "" for none). Read through Colonies.colonyOf. */
    public static final Supplier<AttachmentType<String>> COLONY = ATTACHMENTS.register("colony",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build());

    /**
     * The journey an entity's body belongs to ("" for none): a caravan's or army's people, steeds and wagons while a
     * player sees them in person. Such bodies are saved with their chunk like any other, so one that loads when its
     * journey has gone back to being data is removed ({@code journey.Journeys}).
     */
    public static final Supplier<AttachmentType<String>> JOURNEY = ATTACHMENTS.register("journey",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build());

    /** The faction an entity belongs to ("" for none); NPCs set it when they spawn. Read through FactionKills. */
    public static final Supplier<AttachmentType<String>> ENTITY_FACTION = ATTACHMENTS.register("faction",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build());

    /** The player's chosen LOTR title id ("" for none); kept on death. */
    public static final Supplier<AttachmentType<String>> TITLE = ATTACHMENTS.register("title",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    /** The player's chosen LOTR shield id ("" for none); kept on death. */
    public static final Supplier<AttachmentType<String>> SHIELD = ATTACHMENTS.register("shield",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    /** The player's chosen LOTR cape id ("" for none); kept on death. */
    public static final Supplier<AttachmentType<String>> CAPE = ATTACHMENTS.register("cape",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    /** The faction whose alignment bar the player watches ("" = automatic: pledge, then strongest); kept on death. */
    public static final Supplier<AttachmentType<String>> VIEW_FACTION = ATTACHMENTS.register("view_faction",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    /** LOTR's melee wait in progress (not saved; on both sides, each counting its own player's ticks). */
    public static final Supplier<AttachmentType<AttackTiming.Wait>> ATTACK_WAIT = ATTACHMENTS.register("attack_wait",
            () -> AttachmentType.builder(() -> AttackTiming.Wait.NONE).build());

    private LotrRegistries() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
