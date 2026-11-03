package com.porcupinestudios.lotrport.registry;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.player.PlayerLotrData;
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

    private LotrRegistries() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
