package com.porcupinestudios.lotrport.colonist;

import java.util.Optional;

/**
 * Something on a colonist's mind ({@code colonist_thought/<id>.json}).
 *
 * @param mood points added to mood (negative for a bad thought), per stack
 * @param durationTicks how long a remembered thought lasts; 0 for a thought that lasts as long as its cause (a need
 *     level, a trait, housing) and is never remembered
 * @param stackLimit most times it counts at once
 * @param name what the citizen page calls it (the lang key {@code thought.<namespace>.<id>} wins when present)
 */
public record ThoughtDef(int mood, long durationTicks, int stackLimit, Optional<String> name) {
    public ThoughtDef(int mood, long durationTicks, int stackLimit) {
        this(mood, durationTicks, stackLimit, Optional.empty());
    }
}
