package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.SocietyEvent;
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
        NeoForge.EVENT_BUS.addListener(Migrants::onDied);
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
            MinecraftServer server = level.getServer();
            Person a = Society.recordFor(npc);
            Persons persons = Persons.get(server);
            persons.add(server, a, Optional.empty(), Optional.of(Society.where(npc)));
            persons.set(server, a, CoreComponents.APPLICANT, new CoreComponents.Applicant(0));
            persons.setOwner(server, a, colony.asOwner(), "applied");
            Colonies.log(level.getServer(), Colonies.APPLICANT_HISTORY, colony, npc.blockPosition(), tag(colony, a));
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(colony.founder);
            if (owner != null) {
                tell(owner, colony, a);
            }
        });
        return spawned;
    }

    /** Ages every applicant by {@code days} colony days; those who waited longer than {@code applicant_days} leave. */
    static void age(MinecraftServer server, Colony colony, int days) {
        int max = ColonyData.settings().applicantDays();
        Persons persons = Persons.get(server);
        for (Person a : Society.applicants(server, colony)) {
            CoreComponents.Applicant was = a.get(CoreComponents.APPLICANT).orElseThrow();
            CoreComponents.Applicant older = new CoreComponents.Applicant(was.days() + days);
            persons.setQuiet(a, CoreComponents.APPLICANT, older);
            if (older.days() > max) {
                leave(server, colony, a, "waited");
            }
        }
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
        if (Society.citizenCount(player.server, colony) >= Colonies.homes(level, colony)) {
            return fail("colony.lotr_port.migrant.no_home", "There is no free bed for them");
        }
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
        Person a = Persons.get(player.server).get(migrant).orElseThrow();
        leave(player.server, colony, a, "denied");
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
        Optional<Person> a = Persons.get(player.server).get(npc.getUUID())
                .filter(p -> p.alive() && p.has(CoreComponents.APPLICANT));
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
    public static void tell(ServerPlayer owner, Colony colony, Person a) {
        MutableComponent who = Component.literal(a.name().isEmpty() ? "A migrant" : a.name()).append(" (")
                .append(Component.translatable("npc." + a.type().getNamespace() + "." + a.type().getPath()))
                .append(")");
        owner.sendSystemMessage(Component.translatableWithFallback("colony.lotr_port.migrant.offer",
                "%s offers to settle in %s ", who, colony.name).withStyle(ChatFormatting.GOLD)
                .append(link("colony.lotr_port.migrant.accept", "[Accept]", ChatFormatting.GREEN,
                        "/lotr_colony accept " + a.id()))
                .append(" ")
                .append(link("colony.lotr_port.migrant.deny", "[Deny]", ChatFormatting.RED,
                        "/lotr_colony deny " + a.id())));
    }

    private static MutableComponent link(String key, String fallback, ChatFormatting color, String command) {
        return Component.translatableWithFallback(key, fallback).withStyle(s -> s.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static Optional<Colony> waitingAt(Colonies colonies, ServerPlayer player, UUID migrant) {
        return Persons.get(player.server).get(migrant)
                .filter(p -> p.alive() && p.has(CoreComponents.APPLICANT))
                .flatMap(p -> Society.colonyOf(player.server, p))
                .filter(c -> c.founder().equals(player.getUUID()));
    }

    private static void leave(MinecraftServer server, Colony colony, Person a, String why) {
        Persons.get(server).remove(server, a.id());
        ServerLevel level = server.getLevel(colony.dimension);
        Entity body = level == null ? null : level.getEntity(a.id());
        if (body != null) {
            body.discard();
        }
        CompoundTag tag = tag(colony, a);
        tag.putString("why", why);
        Colonies.log(server, Colonies.DENIED_HISTORY, colony, body != null ? body.blockPosition() : colony.hall, tag);
    }

    private static CompoundTag tag(Colony colony, Person a) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        tag.putUUID("entity", a.id());
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
        boolean waiting = Persons.get(level.getServer()).get(e.getUUID())
                .filter(p -> p.alive() && p.has(CoreComponents.APPLICANT))
                .flatMap(p -> p.owner().colonyId()).map(id.get()::equals).orElse(false);
        if (!waiting) {
            event.setCanceled(true);
        }
    }

    /** An applicant who dies is forgotten (it never joined). */
    public static void onDied(SocietyEvent.Died event) {
        if (event.person().has(CoreComponents.APPLICANT) && !event.person().has(CoreComponents.CITIZEN)) {
            Persons.get(event.server()).remove(event.server(), event.person().id());
        }
    }
}
