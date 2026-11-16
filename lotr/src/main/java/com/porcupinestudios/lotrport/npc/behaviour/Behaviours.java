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
            Map.entry("archer", ArcherBehaviour::new),
            Map.entry("termite", TermiteBehaviour::new),
            Map.entry("elf_scout", ElfScoutBehaviour::new),
            Map.entry("grape_guard", GrapeGuardBehaviour::new),
            Map.entry("boss_jump", BossJumpBehaviour::new),
            Map.entry("on_hit", OnHitBehaviour::new),
            Map.entry("trail", TrailBehaviour::new),
            Map.entry("warg_bomb", WargBombBehaviour::new),
            Map.entry("scorpion", ScorpionBehaviour::new),
            Map.entry("crocodile", CrocodileBehaviour::new),
            Map.entry("prey_chase", PreyChaseBehaviour::new),
            Map.entry("crop_eater", CropEaterBehaviour::new),
            Map.entry("bird_thief", BirdThiefBehaviour::new),
            Map.entry("farmhand", () -> new CreatureBehaviour() {}),
            Map.entry("bandit", BanditBehaviour::new),
            Map.entry("ruffian", RuffianBehaviour::new),
            Map.entry("orc", OrcBehaviour::new),
            Map.entry("avoid_evil", AvoidEvilBehaviour::new),
            Map.entry("burning_panic", BurningPanicBehaviour::new));

    private Behaviours() {}

    public static List<CreatureBehaviour> of(NpcType type) {
        List<CreatureBehaviour> out = new ArrayList<>();
        for (String id : type.persona().behaviours()) {
            Supplier<CreatureBehaviour> b = ALL.get(id);
            if (b != null) {
                out.add(b.get());
            }
        }
        Map<String, Float> data = type.persona().behaviourData();
        if (data.containsKey("eat_chance") || data.containsKey("drink_chance")) {
            out.add(new EatDrinkBehaviour()); // LOTR's AIEat / AIDrink, wherever a type has their chances
        }
        return out;
    }
}
