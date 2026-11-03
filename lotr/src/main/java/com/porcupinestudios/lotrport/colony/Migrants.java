package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Selective migration (Aaron, 2026-11-03): migrants come to the Hall and offer to settle; the owner accepts or turns
 * each one away, by right-clicking them or with the [Accept] / [Deny] chat links ({@code /lotr_colony accept|deny}).
 * A waiting migrant is marked {@code applicant:<colony id>} in the colony attachment, so it is not a citizen. Those
 * not answered within {@code applicant_days} leave; one that was turned away while unloaded is removed when it loads.
 */
public final class Migrants {
    private static final String MARK = "applicant:";

    private Migrants() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Migrants::onJoin);
        NeoForge.EVENT_BUS.addListener(Migrants::onDeath);
    }

    /** The colony this entity is waiting to join, if it is an applicant. */
    public static Optional<UUID> applicantOf(Entity entity) {
        String id = entity.getData(LotrRegistries.COLONY);
        if (!id.startsWith(MARK)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id.substring(MARK.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A migrant of {@code type} arrives at the Hall and waits for an answer. */
    static Optional<LotrNpc> offer(Colonies colonies, ServerLevel level, Colony colony, ResourceLocation type) {
        Optional<LotrNpc> spawned = Colonies.spawnAtHall(level, colony, type);
        spawned.ifPresent(npc -> {
            npc.setPersistenceRequired();
            npc.setData(LotrRegistries.COLONY, MARK + colony.id);
            Colony.Applicant a = new Colony.Applicant(npc.getUUID(), npc.typeId(), npc.personName(), 0);
            colony.applicants.add(a);
            colonies.changed();
            Colonies.log(level.getServer(), Colonies.APPLICANT_HISTORY, colony, npc.blockPosition(), tag(colony, a));
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(colony.founder);
            if (owner != null) {
                tell(owner, colony, a);
            }
        });
        return spawned;
    }

    /** Ages every applicant by a colony day; those who waited longer than {@code applicant_days} leave. */
    static void age(Colonies colonies, ServerLevel level, Colony colony) {
        int max = ColonyData.settings().applicantDays();
        for (Colony.Applicant a : new ArrayList<>(colony.applicants)) {
            Colony.Applicant older = new Colony.Applicant(a.entity(), a.type(), a.name(), a.days() + 1);
            colony.applicants.set(colony.applicants.indexOf(a), older);
            if (older.days() > max) {
                leave(colonies, level.getServer(), colony, older, "waited");
            }
        }
        colonies.changed();
    }

    /** The result of an answer: done, or why not. */
    public record Answer(boolean ok, Component message) {}

    /** The owner takes the migrant in: it becomes a citizen, if there is a free home. */
    public static Answer accept(ServerPlayer player, UUID migrant) {
        Colonies colonies = Colonies.get(player.server);
        Optional<Colony> found = waitingAt(colonies, player, migrant);
        if (found.isEmpty()) {
            return fail("colony.lotr_port.migrant.none", "Nobody by that name is waiting at your Hall");
        }
        Colony colony = found.get();
        ServerLevel level = player.server.getLevel(colony.dimension);
        Entity body = level == null ? null : level.getEntity(migrant);
        if (!(body instanceof LotrNpc npc) || !npc.isAlive()) {
            return fail("colony.lotr_port.migrant.away", "Go to your Hall to take them in");
        }
        if (colony.citizens.size() >= Colonies.homes(level, colony)) {
            return fail("colony.lotr_port.migrant.no_home", "There is no free bed for them");
        }
        colony.applicants.removeIf(a -> a.entity().equals(migrant));
        npc.setData(LotrRegistries.COLONY, colony.id.toString());
        colonies.join(player.server, colony, npc, false);
        return new Answer(true, Component.translatableWithFallback("colony.lotr_port.migrant.accepted",
                "%s settles in %s", npc.getDisplayName(), colony.name));
    }

    /** The owner turns the migrant away: it leaves. */
    public static Answer deny(ServerPlayer player, UUID migrant) {
        Colonies colonies = Colonies.get(player.server);
        Optional<Colony> found = waitingAt(colonies, player, migrant);
        if (found.isEmpty()) {
            return fail("colony.lotr_port.migrant.none", "Nobody by that name is waiting at your Hall");
        }
        Colony colony = found.get();
        Colony.Applicant a = colony.applicants.stream().filter(x -> x.entity().equals(migrant)).findFirst()
                .orElseThrow();
        leave(colonies, player.server, colony, a, "denied");
        return new Answer(true, Component.translatableWithFallback("colony.lotr_port.migrant.denied",
                "%s leaves %s", a.name(), colony.name));
    }

    /** Right-click on an applicant: its owner gets the offer again, anyone else learns who they wait for. */
    public static boolean interact(ServerPlayer player, LotrNpc npc) {
        Optional<UUID> id = applicantOf(npc);
        if (id.isEmpty()) {
            return false;
        }
        Optional<Colony> colony = Colonies.get(player.server).get(id.get());
        Optional<Colony.Applicant> a = colony.flatMap(c -> c.applicants.stream()
                .filter(x -> x.entity().equals(npc.getUUID())).findFirst());
        if (colony.isEmpty() || a.isEmpty()) {
            return false;
        }
        if (colony.get().founder.equals(player.getUUID())) {
            tell(player, colony.get(), a.get());
        } else {
            player.displayClientMessage(Component.translatableWithFallback("colony.lotr_port.migrant.waiting",
                    "%s is waiting to settle in %s", npc.getDisplayName(), colony.get().name), true);
        }
        return true;
    }

    /** Sends the owner the offer with [Accept] and [Deny] links. */
    public static void tell(ServerPlayer owner, Colony colony, Colony.Applicant a) {
        MutableComponent who = Component.literal(a.name().isEmpty() ? "A migrant" : a.name()).append(" (")
                .append(Component.translatable("npc." + a.type().getNamespace() + "." + a.type().getPath()))
                .append(")");
        owner.sendSystemMessage(Component.translatableWithFallback("colony.lotr_port.migrant.offer",
                "%s offers to settle in %s ", who, colony.name).withStyle(ChatFormatting.GOLD)
                .append(link("colony.lotr_port.migrant.accept", "[Accept]", ChatFormatting.GREEN,
                        "/lotr_colony accept " + a.entity()))
                .append(" ")
                .append(link("colony.lotr_port.migrant.deny", "[Deny]", ChatFormatting.RED,
                        "/lotr_colony deny " + a.entity())));
    }

    private static MutableComponent link(String key, String fallback, ChatFormatting color, String command) {
        return Component.translatableWithFallback(key, fallback).withStyle(s -> s.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static Optional<Colony> waitingAt(Colonies colonies, ServerPlayer player, UUID migrant) {
        return colonies.ownedBy(player.getUUID()).stream()
                .filter(c -> c.applicants.stream().anyMatch(a -> a.entity().equals(migrant)))
                .findFirst();
    }

    private static void leave(Colonies colonies, MinecraftServer server, Colony colony, Colony.Applicant a,
            String why) {
        colony.applicants.removeIf(x -> x.entity().equals(a.entity()));
        colonies.changed();
        ServerLevel level = server.getLevel(colony.dimension);
        Entity body = level == null ? null : level.getEntity(a.entity());
        if (body != null) {
            body.discard();
        }
        CompoundTag tag = tag(colony, a);
        tag.putString("why", why);
        Colonies.log(server, Colonies.DENIED_HISTORY, colony, body != null ? body.blockPosition() : colony.hall, tag);
    }

    private static CompoundTag tag(Colony colony, Colony.Applicant a) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        tag.putUUID("entity", a.entity());
        tag.putString("type", a.type().toString());
        return tag;
    }

    private static Answer fail(String key, String fallback) {
        return new Answer(false, Component.translatableWithFallback(key, fallback));
    }

    /** An applicant that loads after it left (turned away or waited out while unloaded) is removed. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity e = event.getEntity();
        Optional<UUID> id = applicantOf(e);
        if (id.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean waiting = Colonies.get(level.getServer()).get(id.get())
                .map(c -> c.applicants.stream().anyMatch(a -> a.entity().equals(e.getUUID()))).orElse(false);
        if (!waiting) {
            event.setCanceled(true);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        Entity dead = event.getEntity();
        if (dead.level().isClientSide) {
            return;
        }
        applicantOf(dead).ifPresent(id -> {
            Colonies colonies = Colonies.get(dead.getServer());
            colonies.get(id).ifPresent(c -> {
                if (c.applicants.removeIf(a -> a.entity().equals(dead.getUUID()))) {
                    colonies.changed();
                }
            });
        });
    }
}
