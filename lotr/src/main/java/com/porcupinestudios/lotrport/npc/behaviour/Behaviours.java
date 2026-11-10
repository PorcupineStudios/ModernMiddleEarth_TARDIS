package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.NpcType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The LOTR creature behaviours by id, as NPC types name them. */
public final class Behaviours {
    private static final Map<String, Supplier<CreatureBehaviour>> ALL = Map.ofEntries(
            Map.entry("troll", TrollBehaviour::new),
            Map.entry("rock_thrower", () -> new RangedBehaviour(LotrProjectile.Kind.ROCK, "isThrowingRocks")),
            Map.entry("snow_thrower", () -> new RangedBehaviour(LotrProjectile.Kind.SNOW, "isThrowingSnow")),
            Map.entry("spider", SpiderBehaviour::new),
            Map.entry("tree", TreeBehaviour::new),
            Map.entry("ent", EntBehaviour::new),
            Map.entry("huorn", HuornBehaviour::new),
            Map.entry("balrog", BalrogBehaviour::new),
            Map.entry("wight", WightBehaviour::new),
            Map.entry("marsh_wraith", MarshWraithBehaviour::new),
            Map.entry("gollum", GollumBehaviour::new),
            Map.entry("warg", WargBehaviour::new),
            Map.entry("half_troll", HalfTrollBehaviour::new),
            Map.entry("animal", AnimalBehaviour::new),
            Map.entry("archer", ArcherBehaviour::new));

    private Behaviours() {}

    public static List<CreatureBehaviour> of(NpcType type) {
        List<CreatureBehaviour> out = new ArrayList<>();
        for (String id : type.persona().behaviours()) {
            Supplier<CreatureBehaviour> b = ALL.get(id);
            if (b != null) {
                out.add(b.get());
            }
        }
        return out;
    }
}
