package com.porcupinestudios.lotrport.colonist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Skill XP and levels, and how opinions drift. */
class SkillsOpinionsTest {
    @Test
    void xpLevelsUpAndStopsAtTheTop() {
        Skills.Settings s = new Skills.Settings(3, 10, 4, 0.06, 3);
        Skills.Skill k = Skills.train(new Skills.Skill(0, 0), 9, s);
        assertEquals(0, k.level());
        k = Skills.train(k, 1, s);
        assertEquals(1, k.level(), "10 XP for level 1");
        k = Skills.train(k, 20 + 30, s);
        assertEquals(3, k.level(), "20 more for 2, 30 more for 3");
        assertEquals(0, Skills.train(k, 1000, s).xp(), "no XP kept at the top");
    }

    @Test
    void opinionsDriftTowardWhatTheyShare() {
        Opinions.Settings s = Opinions.Settings.DEFAULT;
        ResourceLocation grumpy = ResourceLocation.fromNamespaceAndPath("lotr_port", "grumpy");
        Opinions.Pair housemates = new Opinions.Pair(true, true, false, false, List.of(), List.of());
        assertEquals(25, Opinions.target(housemates, s), 1e-9);
        Opinions.Settings likes = new Opinions.Settings(0.08, 10, -5, 15, 10, 40, Map.of("lotr_port:grumpy",
                Map.of("lotr_port:grumpy", -30.0)));
        Opinions.Pair grumps = new Opinions.Pair(true, false, false, false, List.of(grumpy), List.of(grumpy));
        assertEquals(-20, Opinions.target(grumps, likes), 1e-9);
        double v = Opinions.drift(0, 25, s, 10);
        assertTrue(v > 10 && v < 25, "ten days toward 25: " + v);
        assertEquals(100, Opinions.drift(100, 500, s, 1), 1e-9);
    }
}
