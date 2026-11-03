package com.porcupinestudios.tardisrb.progression;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A room the crew can add ({@code tardis/room}): the structure {@code template} to build, its {@code cost}, and extra
 * {@code requirements} on top of the {@code tardis_rb:add_room} feature and the room cap. Rooms are built one behind
 * another past the console room's back wall, {@code room_step} blocks apart (tuning), with a doorway carved between.
 */
public record Room(ResourceLocation template, Either<ResourceLocation, Cost> cost, Optional<Condition> requirements) {
    public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(Room::template),
            Cost.REF_CODEC.optionalFieldOf("cost", Cost.NONE).forGetter(Room::cost),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Room::requirements))
            .apply(i, Room::new));
}
