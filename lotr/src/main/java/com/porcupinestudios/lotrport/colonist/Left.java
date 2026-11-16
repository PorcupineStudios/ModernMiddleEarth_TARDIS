package com.porcupinestudios.lotrport.colonist;

import java.util.UUID;

/**
 * Someone who left a colony and is remembered because they have family (a person part): their record stays, owned by
 * nobody, so family trees can still show them.
 *
 * @param colony the colony they left
 * @param why {@code abandoned} (unhappy too long) or {@code banished} (sent away from the citizen page)
 * @param since game time they left
 */
public record Left(UUID colony, String why, long since) {
    public static final String ABANDONED = "abandoned";
    public static final String BANISHED = "banished";
}
