package com.porcupinestudios.lotrport.sound;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every LOTR sound event, registered from the generated list {@code lotr_port/content/sounds.json}
 * ({@code converter/sounds.py}: LOTR's 101 events from its sounds.json, ids lower-cased, files under
 * {@code assets/lotr_port/sounds/}). Look one up with {@link #get}; data files name them by id
 * ({@code lotr_port:orc.say}).
 */
public final class LotrSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
            LotrPort.MOD_ID);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_ID = new LinkedHashMap<>();

    private LotrSounds() {}

    public static void register(IEventBus modBus) {
        for (JsonObject e : ContentData.read("sounds.json")) {
            String id = e.get("id").getAsString();
            BY_ID.put(id, SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(LotrPort.id(id))));
        }
        SOUNDS.register(modBus);
    }

    /** A LOTR sound by its id ("orc.say"), or empty if the generated list has no such sound. */
    public static Optional<Holder<SoundEvent>> get(String id) {
        DeferredHolder<SoundEvent, SoundEvent> h = BY_ID.get(id);
        return h == null ? Optional.empty() : Optional.of(h);
    }

    /** Plays a LOTR sound to one player only, at the player (LOTR's event sounds: pledge, fast travel...). */
    public static void playTo(ServerPlayer player, String id) {
        get(id).ifPresent(sound -> player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), 1f, 1f, player.getRandom().nextLong())));
    }

    /** Plays a LOTR sound at a position for everyone near. */
    public static void playAt(Level level, double x, double y, double z, String id, SoundSource source, float volume,
            float pitch) {
        get(id).ifPresent(sound -> level.playSound(null, x, y, z, sound, source, volume, pitch));
    }

    /** Any registered sound by full id (data files: {@code lotr_port:orc.say} or a vanilla sound). */
    public static Optional<SoundEvent> byId(ResourceLocation id) {
        return BuiltInRegistries.SOUND_EVENT.getOptional(id);
    }
}
