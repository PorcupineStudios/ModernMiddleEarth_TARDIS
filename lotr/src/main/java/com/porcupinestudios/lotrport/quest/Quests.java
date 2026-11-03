package com.porcupinestudios.lotrport.quest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Mini-quests, as LOTR's: an NPC whose type has a quest pool asks a player (with enough alignment) to bring items or
 * kill enemies; the player hands the quest in to the same NPC for coins and alignment with its faction. Talking to the
 * giver again shows progress. Kills count wherever they happen.
 */
public final class Quests {
    public static final ResourceLocation COMPLETED_HISTORY = LotrPort.id("quest_completed");

    private Quests() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Quests::onDeath);
    }

    public static QuestLog log(ServerPlayer player) {
        return player.getData(LotrRegistries.QUEST_LOG);
    }

    private static void save(ServerPlayer player, QuestLog log) {
        player.setData(LotrRegistries.QUEST_LOG, log);
    }

    /** The result of asking for a quest: the quest taken, or why there is none. */
    public record Offer(Optional<ActiveQuest> quest, Component problem) {}

    /**
     * Right-click on an NPC. Hands in or shows a quest this NPC gave, or offers a new one if it gives quests (and isn't
     * a trader or captain, who keep their own screens). Returns false when the NPC has nothing quest-related to say.
     */
    public static boolean interact(ServerPlayer player, LotrNpc npc) {
        int index = indexFrom(log(player), npc.getUUID());
        if (index >= 0) {
            handIn(player, index);
            return true;
        }
        NpcType type = npc.npcType();
        if (type == null || type.quests().isEmpty() || type.trader() || type.captain()) {
            return false;
        }
        Offer offer = offer(player, npc);
        if (offer.quest().isEmpty()) {
            player.sendSystemMessage(offer.problem());
        }
        return true;
    }

    /** Gives the player a new quest from this NPC's pool, if they may take one. */
    public static Offer offer(ServerPlayer player, LotrNpc npc) {
        NpcType type = npc.npcType();
        Optional<QuestPool> pool = type == null ? Optional.empty() : type.quests().flatMap(QuestData::pool);
        Optional<ResourceLocation> faction = npc.faction();
        if (pool.isEmpty() || faction.isEmpty()) {
            return fail("quest.lotr_port.none", "%s has nothing for you to do", npc.getDisplayName());
        }
        if (Alignment.get(player, faction.get()) < pool.get().minAlignment()) {
            return fail("quest.lotr_port.distrust", "%s does not trust you enough to ask for help",
                    npc.getDisplayName());
        }
        QuestLog log = log(player);
        long open = log.active().stream().filter(q -> q.faction().equals(faction.get())).count();
        if (open >= pool.get().maxActive()) {
            return fail("quest.lotr_port.too_many", "You already have %s quests for this faction",
                    pool.get().maxActive());
        }
        Optional<QuestPool.Entry> entry = pool.get().pick(npc.getRandom());
        if (entry.isEmpty()) {
            return fail("quest.lotr_port.none", "%s has nothing for you to do", npc.getDisplayName());
        }
        QuestPool.Entry e = entry.get();
        int count = e.min() + (e.max() > e.min() ? npc.getRandom().nextInt(e.max() - e.min() + 1) : 0);
        int coins = Math.max(1, Math.round(count * e.reward() * pool.get().coinsPerTarget()));
        float alignment = Math.round(count * e.reward() * pool.get().alignmentPerTarget() * 10f) / 10f;
        ActiveQuest quest = new ActiveQuest(npc.getUUID(), npc.getDisplayName().getString(), faction.get(), e.kind(),
                e.target(), count, 0, coins, alignment, player.server.overworld().getGameTime());
        save(player, log.with(quest));
        player.sendSystemMessage(Component.translatableWithFallback("quest.lotr_port.given", "%s: %s (reward: %s"
                + " coins, %s alignment)", npc.getDisplayName(), describe(quest), coins, alignment));
        return new Offer(Optional.of(quest), Component.empty());
    }

    private static Offer fail(String key, String fallback, Object... args) {
        return new Offer(Optional.empty(), Component.translatableWithFallback(key, fallback, args));
    }

    /** Hands in the quest at {@code index} if it is done; otherwise tells the player how far along it is. */
    public static boolean handIn(ServerPlayer player, int index) {
        QuestLog log = log(player);
        ActiveQuest quest = log.active().get(index);
        boolean done;
        if (quest.kind() == QuestPool.Kind.COLLECT) {
            Item item = BuiltInRegistries.ITEM.get(quest.target());
            done = item != Items.AIR && count(player.getInventory(), item) >= quest.count();
            if (done) {
                take(player.getInventory(), item, quest.count());
            }
        } else {
            done = quest.killsDone();
        }
        if (!done) {
            player.sendSystemMessage(Component.translatableWithFallback("quest.lotr_port.progress",
                    "%s: %s (%s of %s)", quest.giverName(), describe(quest), progress(player, quest),
                    quest.count()));
            return false;
        }
        save(player, log.without(index, true));
        Coins.give(player, quest.coins());
        Alignment.add(player, quest.faction(), quest.alignment());
        player.sendSystemMessage(Component.translatableWithFallback("quest.lotr_port.complete",
                "%s: thank you! (%s coins, %s alignment)", quest.giverName(), quest.coins(), quest.alignment()));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player.getUUID());
        tag.putUUID("giver", quest.giver());
        tag.putString("faction", quest.faction().toString());
        tag.putString("kind", quest.kind().name().toLowerCase());
        tag.putString("target", quest.target().toString());
        tag.putInt("count", quest.count());
        HistoryLog.get(player.server).append(new HistoryEntry(player.server.overworld().getGameTime(),
                COMPLETED_HISTORY, player.level().dimension(), Optional.of(player.blockPosition()), tag));
        return true;
    }

    /** Drops an open quest without reward. */
    public static boolean abandon(ServerPlayer player, int index) {
        QuestLog log = log(player);
        if (index < 0 || index >= log.active().size()) {
            return false;
        }
        save(player, log.without(index, false));
        return true;
    }

    private static int indexFrom(QuestLog log, UUID giver) {
        for (int i = 0; i < log.active().size(); i++) {
            if (log.active().get(i).giver().equals(giver)) {
                return i;
            }
        }
        return -1;
    }

    public static int progress(ServerPlayer player, ActiveQuest quest) {
        if (quest.kind() == QuestPool.Kind.COLLECT) {
            return Math.min(quest.count(), count(player.getInventory(), BuiltInRegistries.ITEM.get(quest.target())));
        }
        return quest.progress();
    }

    public static Component describe(ActiveQuest quest) {
        ResourceLocation t = quest.target();
        return switch (quest.kind()) {
            case COLLECT -> Component.translatableWithFallback("quest.lotr_port.collect", "bring me %s %s",
                    quest.count(), BuiltInRegistries.ITEM.get(t).getDescription());
            case KILL_FACTION -> Component.translatableWithFallback("quest.lotr_port.kill_faction",
                    "kill %s of %s", quest.count(), Component.translatable("faction." + t.getNamespace() + "."
                            + t.getPath()));
            case KILL_NPC -> Component.translatableWithFallback("quest.lotr_port.kill_npc", "kill %s %s",
                    quest.count(), Component.translatable("npc." + t.getNamespace() + "." + t.getPath()));
        };
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void take(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int n = Math.min(amount, s.getCount());
                s.shrink(n);
                amount -= n;
            }
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            killed(player, event.getEntity());
        }
    }

    /** Counts a kill toward the player's kill quests. */
    public static void killed(ServerPlayer player, LivingEntity victim) {
        QuestLog log = log(player);
        Optional<ResourceLocation> faction = FactionKills.factionOf(victim);
        ResourceLocation npcType = victim instanceof LotrNpc npc ? npc.typeId() : null;
        for (int i = 0; i < log.active().size(); i++) {
            ActiveQuest q = log.active().get(i);
            boolean counts = switch (q.kind()) {
                case KILL_FACTION -> faction.isPresent() && faction.get().equals(q.target());
                case KILL_NPC -> q.target().equals(npcType);
                case COLLECT -> false;
            };
            if (!counts || q.killsDone()) {
                continue;
            }
            ActiveQuest next = q.withProgress(q.progress() + 1);
            log = log.replace(i, next);
            player.displayClientMessage(next.killsDone()
                    ? Component.translatableWithFallback("quest.lotr_port.return", "Quest done: return to %s",
                            q.giverName())
                    : Component.translatableWithFallback("quest.lotr_port.kills", "%s: %s of %s",
                            describe(q), next.progress(), q.count()), true);
        }
        save(player, log);
    }
}
