package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.GameRules;

/**
 * LOTR's men (LOTREntityMan.onDeath): killed by someone on the orcs' or trolls' side (a player with positive alignment
 * with an orc or troll faction, or an NPC of one) with a blade or tool of orc or half-troll make, one in
 * {@code flesh_chance} (5) drops 1-2 man-flesh.
 */
public class ManFleshBehaviour implements CreatureBehaviour {
    /** LOTRMaterial's materials that canHarvestManFlesh, as the port's item id prefixes. */
    private static final List<String> FLESH_MATERIALS = List.of("mordor_", "uruk_", "morgul_", "gundabad_uruk_",
            "angmar_", "dol_guldur_", "black_uruk_", "utumno_", "half_troll_");

    private static boolean orcish(ResourceLocation faction) {
        var def = Factions.definitions().get(faction);
        return def != null && (def.types().contains("orc") || def.types().contains("troll"));
    }

    @Override
    public void died(LotrNpc npc, DamageSource source) {
        if (!(source.getDirectEntity() instanceof LivingEntity killer)
                || !npc.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                || npc.random().nextInt((int) CreatureBehaviour.data(npc, "flesh_chance", 5)) != 0) {
            return;
        }
        boolean aligned = killer instanceof Player p
                ? Factions.definitions().keySet().stream().anyMatch(f -> orcish(f) && Alignment.get(p, f) > 0)
                : FactionKills.factionOf(killer).map(ManFleshBehaviour::orcish).orElse(false);
        ItemStack weapon = killer.getMainHandItem();
        String id = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
        boolean blade = weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof DiggerItem
                || id.endsWith("throwing_axe");
        if (aligned && blade && FLESH_MATERIALS.stream().anyMatch(id::startsWith)) {
            npc.spawnAtLocation(new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("man_flesh")), 1 + npc.random().nextInt(2)));
        }
    }
}
