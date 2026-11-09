package com.porcupinestudios.lotrport.animal;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.Level;

/** LOTR's Shire pony: a vanilla horse (coats, markings, armor, taming), drawn at LOTR's 0.8 scale. */
public class ShirePony extends Horse {
    public ShirePony(EntityType<? extends ShirePony> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean canMate(Animal other) {
        return other instanceof ShirePony p && p != this && canParent() && p.canParent();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) {
        if (!(getType().create(level) instanceof ShirePony child)) {
            return null;
        }
        child.setVariant((random.nextBoolean() || !(mate instanceof ShirePony p) ? this : p).getVariant());
        setOffspringAttributes(mate, child);
        return child;
    }
}
