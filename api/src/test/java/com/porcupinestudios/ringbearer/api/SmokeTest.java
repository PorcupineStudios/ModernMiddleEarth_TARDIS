package com.porcupinestudios.ringbearer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void modIdIsLowercase() {
        assertEquals(RingBearerApi.MOD_ID.toLowerCase(), RingBearerApi.MOD_ID);
    }
}
