package com.porcupinestudios.tardisrb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void modIdIsLowercase() {
        assertEquals(TardisRB.MOD_ID.toLowerCase(), TardisRB.MOD_ID);
    }
}
