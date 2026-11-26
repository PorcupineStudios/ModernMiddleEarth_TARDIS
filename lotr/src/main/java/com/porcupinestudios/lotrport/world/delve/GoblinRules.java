package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;

/**
 * Goblin Town's plan rules, checked without a world (GameTests {@code GoblinTests}, and outside the game): every part
 * is placed and zoned, the chute drops from the Front Porch to the landing, the falls and the lake lie under the
 * town, and a decoy porch at the real porch's spot is the same block for block above its floor.
 */
public final class GoblinRules {
    private GoblinRules() {}

    /** Null if the town keeps the rules, else what is wrong. */
    public static String check(JsonObject spec, long seed, Ground ground) {
        DelveSpec s = DelveSpec.parse(spec);
        int[] anchor = J.ints(spec.getAsJsonArray("anchor"));
        DelvePlan plan = GoblinPlanner.plan(s, seed, ground, anchor[0], anchor[1]);
        if (plan == null) {
            return "no plan";
        }
        String[][] parts = {{"front_porch", "porch"}, {"landing", "landing"}, {"throne", "throne"},
                {"lake", "gollum_lake"}, {"back_door", "back_door"}};
        for (String[] p : parts) {
            int[] d = plan.doorway(p[0]);
            if (d == null) {
                return p[0] + " wasn't placed:\n" + plan.summary;
            }
            String zone = plan.zoneAt(d[0], d[1] + 1, d[2]);
            if (!p[1].equals(zone)) {
                return p[0] + " at " + d[0] + " " + d[1] + " " + d[2] + " is in zone " + zone + ", not " + p[1];
            }
        }
        int[] porch = plan.doorway("front_porch");
        int[] landing = plan.doorway("landing");
        int[] lake = plan.doorway("lake");
        int[] throne = plan.doorway("throne");
        if (landing[1] > porch[1] - 16) {
            return "the chute drops only " + (porch[1] - landing[1]) + " blocks";
        }
        if (lake[1] >= throne[1]) {
            return "Gollum's lake (y " + lake[1] + ") isn't under the town (throne y " + throne[1] + ")";
        }
        // a decoy at the porch's own spot: everything from the floor up must be the same
        JsonObject decoySpec = spec.deepCopy();
        decoySpec.getAsJsonObject("porch").addProperty("armed", false);
        decoySpec.getAsJsonObject("porch").addProperty("chute", false);
        DelvePlan decoy = GoblinPlanner.plan(DelveSpec.parse(decoySpec), seed, ground, anchor[0], anchor[1]);
        if (decoy == null || decoy.doorway("front_porch") == null
                || !java.util.Arrays.equals(decoy.doorway("front_porch"), porch)) {
            return "the decoy porch isn't where the real one is";
        }
        int x0 = porch[0] - 24;
        int z0 = porch[2] - 24;
        MemoryCanvas real = new MemoryCanvas(x0, z0, x0 + 48, z0 + 48, ground);
        MemoryCanvas twin = new MemoryCanvas(x0, z0, x0 + 48, z0 + 48, ground);
        plan.build(real);
        decoy.build(twin);
        int floors = 0;
        int under = 0;
        for (int x = x0; x <= x0 + 48; x++) {
            for (int z = z0; z <= z0 + 48; z++) {
                for (int y = porch[1] - 1; y <= porch[1] + 12; y++) {
                    Mat m = real.at(x, y, z);
                    Mat n = twin.at(x, y, z);
                    String a = m == null ? "-" : m.toString();
                    String b = n == null ? "-" : n.toString();
                    if (!a.equals(b)) {
                        return "the decoy differs at " + x + " " + y + " " + z + ": " + a + " vs " + b;
                    }
                    if (y == porch[1] - 1 && m != null && m.id.equals("lotr_port:porch_floor")) {
                        floors++;
                        Mat below = real.at(x, y - 1, z);
                        Mat decoyBelow = twin.at(x, y - 1, z);
                        boolean open = below != null && below.isAir();
                        boolean shut = decoyBelow == null ? twin.solid(x, y - 1, z) : !decoyBelow.isAir();
                        if (open && shut) {
                            under++;
                        }
                    }
                }
            }
        }
        if (floors < 20) {
            return "the porch has only " + floors + " floor blocks";
        }
        if (under != floors) {
            return under + " of " + floors + " floor blocks are over the chute in the real porch and on rock in the decoy";
        }
        return null;
    }
}
