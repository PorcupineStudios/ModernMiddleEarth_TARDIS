package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The TARDIS's own sounds (Aaron 2026-11-16): {@code materialise} when it dematerialises and rematerialises, and
 * {@code stutter} (six cuts of a malfunction mix, one picked at random) when it breaks down and crash lands. Both
 * play outside at the box and inside in the console room. The audio is not in this repo (BBC sound effects): it
 * comes with the private resource-only jar {@code tardis_rb_sounds}; without it these events play nothing.
 */
public final class TardisSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
            TardisRB.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> MATERIALISE = SOUNDS.register("materialise",
            () -> SoundEvent.createFixedRangeEvent(TardisRB.id("materialise"), 48));
    public static final DeferredHolder<SoundEvent, SoundEvent> STUTTER = SOUNDS.register("stutter",
            () -> SoundEvent.createFixedRangeEvent(TardisRB.id("stutter"), 48));

    private TardisSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    /** Plays {@code sound} at {@code outside} (if given) and in the TARDIS's console room. */
    public static void play(MinecraftServer server, TardisRecord tardis, GlobalPos outside,
            DeferredHolder<SoundEvent, SoundEvent> sound) {
        if (outside != null) {
            ServerLevel level = server.getLevel(outside.dimension());
            if (level != null) {
                level.playSound(null, outside.pos(), sound.get(), SoundSource.BLOCKS, 3f, 1f);
            }
        }
        if (tardis.plot() >= 0) {
            Interiors.level(server).ifPresent(level -> level.playSound(null, Interiors.spawn(tardis), sound.get(),
                    SoundSource.BLOCKS, 3f, 1f));
        }
    }
}
