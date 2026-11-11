package com.porcupinestudios.lotrport.colonist;

/**
 * Something on a colonist's mind ({@code colonist_thought/<id>.json}).
 *
 * @param mood points added to mood (negative for a bad thought), per stack
 * @param durationTicks how long a remembered thought lasts; 0 for a thought that lasts as long as its cause (a need
 *     level, a trait, housing) and is never remembered
 * @param stackLimit most times it counts at once
 */
public record ThoughtDef(int mood, long durationTicks, int stackLimit) {}
