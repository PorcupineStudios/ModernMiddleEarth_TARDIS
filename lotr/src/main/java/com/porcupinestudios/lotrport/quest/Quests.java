package com.porcupinestudios.lotrport.quest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.net.QuestNet;
import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mini-quests, as LOTR's: an NPC whose type has a quest pool offers a player (with enough alignment) a task on LOTR's
 * quest page: bring items or kill enemies. Taken quests are kept in the Red Book ({@link QuestNet#sendBook}); the
 * player hands one in to the same NPC for coins and alignment with its faction. Talking to the giver again shows
 * progress above its head. Kills count wherever they happen.
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

    /** The result of asking for a quest: the quest, or why there is none. */
    public record Offer(Optional<ActiveQuest> quest, Component problem) {}

    /** A quest shown to a player on the offer page, waiting for their answer. */
    private record Pending(UUID npc, ActiveQuest quest, long time) {}

    /** Offers waiting for an answer, by player. An answer after {@link #ANSWER_TICKS} or far from the giver fails. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final long ANSWER_TICKS = 20 * 60 * 5;

    /**
     * Right-click on an NPC. Hands in or shows a quest this NPC gave, or offers a new one on LOTR's quest page if it
     * gives quests (and isn't a trader or captain, who keep their own screens). The NPC answers above its head.
     * Returns false when the NPC has nothing quest-related to say.
     */
    public static boolean interact(ServerPlayer player, LotrNpc npc) {
        int index = indexFrom(log(player), npc.getUUID());
        if (index >= 0) {
            handIn(player, index, npc);
            return true;
        }
        NpcType type = npc.npcType();
        if (type == null || type.quests().isEmpty() || type.trader() || type.captain()) {
            return false;
        }
        Offer offer = roll(player, npc);
        if (offer.quest().isEmpty()) {
            npc.say(offer.problem(), player);
            return true;
        }
        ActiveQuest q = offer.quest().get();
        PENDING.put(player.getUUID(), new Pending(npc.getUUID(), q, player.server.overworld().getGameTime()));
        PacketDistributor.sendToPlayer(player, new QuestNet.Offer(npc.getId(), q.giverName(),
                factionName(q.faction()), describe(q), q.coins(), q.alignment()));
        return true;
    }

    /** The player's answer to the quest offer page. */
    public static void answer(ServerPlayer player, boolean accept) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || !accept) {
            return;
        }
        long age = player.server.overworld().getGameTime() - pending.time();
        Entity giver = player.serverLevel().getEntity(pending.npc());
        if (age > ANSWER_TICKS || !(giver instanceof LotrNpc npc) || !npc.isAlive()
                || npc.distanceTo(player) > LotrNpc.DEAL_DISTANCE * 2) {
            player.displayClientMessage(Component.translatableWithFallback("quest.lotr_port.gone",
                    "The quest giver is no longer here"), true);
            return;
        }
        Offer again = check(player, npc);
        if (again.problem() != null) {
            npc.say(again.problem(), player);
            return;
        }
        giveQuest(player, npc, pending.quest());
    }

    /** Gives the player a new quest from this NPC's pool straight away, if they may take one (tests, commands). */
    public static Offer offer(ServerPlayer player, LotrNpc npc) {
        Offer offer = roll(player, npc);
        offer.quest().ifPresent(q -> giveQuest(player, npc, q));
        return offer;
    }

    private static void giveQuest(ServerPlayer player, LotrNpc npc, ActiveQuest quest) {
        save(player, log(player).with(quest));
        npc.say(Component.translatableWithFallback("quest.lotr_port.given", "Please %s", describe(quest)), player);
        player.displayClientMessage(Component.translatableWithFallback("quest.lotr_port.written",
                "Quest written in your Red Book"), true);
    }

    /** Whether the player may take another quest from this NPC: a problem, or null when they may. */
    private static Offer check(ServerPlayer player, LotrNpc npc) {
        NpcType type = npc.npcType();
        Optional<QuestPool> pool = type == null ? Optional.empty() : type.quests().flatMap(QuestData::pool);
        Optional<ResourceLocation> faction = npc.faction();
        if (pool.isEmpty() || faction.isEmpty()) {
            return fail("quest.lotr_port.none", "I have nothing for you to do");
        }
        if (Alignment.get(player, faction.get()) < pool.get().minAlignment()) {
            return fail("quest.lotr_port.distrust", "I don't trust you enough to ask for your help");
        }
        long open = log(player).active().stream().filter(q -> q.faction().equals(faction.get())).count();
        if (open >= pool.get().maxActive()) {
            return fail("quest.lotr_port.too_many", "You already have %s quests for my people",
                    pool.get().maxActive());
        }
        return new Offer(Optional.empty(), null);
    }

    /** Picks a quest from this NPC's pool for the player without giving it. */
    public static Offer roll(ServerPlayer player, LotrNpc npc) {
        Offer problem = check(player, npc);
        if (problem.problem() != null) {
            return problem;
        }
        QuestPool pool = npc.npcType().quests().flatMap(QuestData::pool).orElseThrow();
        ResourceLocation faction = npc.faction().orElseThrow();
        Optional<QuestPool.Entry> entry = pool.pick(npc.getRandom());
        if (entry.isEmpty()) {
            return fail("quest.lotr_port.none", "I have nothing for you to do");
        }
        QuestPool.Entry e = entry.get();
        int count = e.min() + (e.max() > e.min() ? npc.getRandom().nextInt(e.max() - e.min() + 1) : 0);
        int coins = Math.max(1, Math.round(count * e.reward() * pool.coinsPerTarget()));
        float alignment = Math.round(count * e.reward() * pool.alignmentPerTarget() * 10f) / 10f;
        ActiveQuest quest = new ActiveQuest(npc.getUUID(), npc.getDisplayName().getString(), faction, e.kind(),
                e.target(), count, 0, coins, alignment, player.server.overworld().getGameTime());
        return new Offer(Optional.of(quest), Component.empty());
    }

    private static Offer fail(String key, String fallback, Object... args) {
        return new Offer(Optional.empty(), Component.translatableWithFallback(key, fallback, args));
    }

    /** Hands in the quest at {@code index} if it is done; otherwise says how far along it is. */
    public static boolean handIn(ServerPlayer player, int index) {
        return handIn(player, index, null);
    }

    /** As {@link #handIn(ServerPlayer, int)}; the giver ({@code npc}, if given) answers above its head. */
    public static boolean handIn(ServerPlayer player, int index, @Nullable LotrNpc npc) {
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
            Component line = Component.translatableWithFallback("quest.lotr_port.progress",
                    "You haven't finished yet: %s (%s of %s)", describe(quest), progress(player, quest),
                    quest.count());
            if (npc != null) {
                npc.say(line, player);
            } else {
                player.displayClientMessage(line, true);
            }
            return false;
        }
        save(player, log.without(index, true));
        Coins.give(player, quest.coins());
        Alignment.add(player, quest.faction(), quest.alignment());
        if (npc != null) {
            npc.say(Component.translatableWithFallback("quest.lotr_port.thanks", "Thank you, %s!",
                    player.getGameProfile().getName()), player);
        }
        player.displayClientMessage(Component.translatableWithFallback("quest.lotr_port.complete",
                "Quest complete: %s coins, %s alignment", quest.coins(), quest.alignment()), true);
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

    /** A faction's name. */
    public static Component factionName(ResourceLocation faction) {
        return Component.translatable("faction." + faction.getNamespace() + "." + faction.getPath());
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
