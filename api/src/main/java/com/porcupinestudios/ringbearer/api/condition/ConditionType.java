package com.porcupinestudios.ringbearer.api.condition;

import com.mojang.serialization.MapCodec;

/** A kind of condition: how its fields are read from data. Registered in {@link Conditions#REGISTRY}. */
public record ConditionType<T extends Condition>(MapCodec<T> codec) {}
