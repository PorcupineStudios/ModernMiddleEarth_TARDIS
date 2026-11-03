package com.porcupinestudios.lotrport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void modIdIsLowercase() {
        assertEquals(LotrPort.MOD_ID.toLowerCase(), LotrPort.MOD_ID);
    }
}
