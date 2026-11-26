package com.porcupinestudios.lotrport.world.delve;

/**
 * Ground height (surface y) at a column: a pure function of the world seed and the map ({@code MiddleEarthMap.heightAt}
 * in game), so a delve's plan never needs a generated chunk.
 */
@FunctionalInterface
public interface Ground {
    double height(int x, int z);
}
