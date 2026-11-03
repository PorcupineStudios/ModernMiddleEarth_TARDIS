package com.porcupinestudios.lotrport.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Fired on {@code NeoForge.EVENT_BUS} after a player's alignment with a faction changed. For KubeJS and addons. */
public final class AlignmentChangeEvent extends PlayerEvent {
    private final ResourceLocation faction;
    private final float oldValue;
    private final float newValue;

    public AlignmentChangeEvent(Player player, ResourceLocation faction, float oldValue, float newValue) {
        super(player);
        this.faction = faction;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public ResourceLocation getFaction() {
        return faction;
    }

    public float getOldValue() {
        return oldValue;
    }

    public float getNewValue() {
        return newValue;
    }
}
