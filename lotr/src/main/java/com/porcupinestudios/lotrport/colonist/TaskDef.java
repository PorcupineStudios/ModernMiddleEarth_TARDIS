package com.porcupinestudios.lotrport.colonist;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One kind of task a colonist can take up ({@code colonist_task/<id>.json}): sleep, eat, work, leisure, wander. A
 * pack adds kinds with new files; what a body does for one is its {@code action}.
 *
 * @param action what the body does: {@code sleep} (goes to bed: indoors if they have a home), {@code eat},
 *     {@code work}, {@code leisure}, {@code wander}
 * @param place where it happens: {@code home}, {@code workplace}, {@code food} (a storehouse or kitchen),
 *     {@code hall}
 * @param priority priority (1-10) by schedule block ({@code work}, {@code leisure}, {@code sleep}, {@code any}); a
 *     block not listed, or 0, means not in those hours
 * @param need the need it answers ({@code energy}, {@code food}), if any
 * @param wantBelow offered only while that need is below this (a full colonist doesn't go to eat), except in the
 *     blocks of {@code alwaysIn} (they sleep in sleep hours however rested)
 * @param alwaysIn see above
 * @param urgentBelow below this the task jumps to {@code urgentPriority} in every hour it is allowed
 * @param urgentPriority see above
 * @param requires what must hold: {@code job} (a workplace), {@code paid} (no wages owed: unpaid workers strike),
 *     {@code works} (not told to rest), {@code food_place} (the colony has a storehouse or kitchen)
 */
public record TaskDef(String action, String place, Map<String, Integer> priority, Optional<String> need,
        double wantBelow, List<String> alwaysIn, double urgentBelow, int urgentPriority, List<String> requires) {}
