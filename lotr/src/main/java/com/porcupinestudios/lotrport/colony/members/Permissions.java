package com.porcupinestudios.lotrport.colony.members;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The one place that answers "may this player do that in this colony": {@link #can}. The colony's owner may do
 * everything; members get their role's permissions; anyone else none. Roles are data ({@code colony_role/}).
 */
public final class Permissions {
    public static final ResourceLocation RULER = LotrPort.id("ruler");
    public static final ResourceLocation GUEST = LotrPort.id("guest");
    public static final ResourceLocation ROLE_HISTORY = LotrPort.id("colony_role");
    private static final CodecJsonLoader<RoleDef> ROLES = new CodecJsonLoader<>("colony_role", RoleDef.CODEC);
    private static final RoleDef OWNER = new RoleDef("Ruler", Integer.MAX_VALUE, List.of("*"));

    private Permissions() {}

    public static void register() {
        Members.init();
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(ROLES));
    }

    public static Map<ResourceLocation, RoleDef> roles() {
        return ROLES.entries();
    }

    /** Roles a member may be given, highest first (the ruler role is the owner's alone). */
    public static List<ResourceLocation> assignable() {
        return ROLES.entries().entrySet().stream().filter(e -> !e.getKey().equals(RULER))
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, RoleDef> e) -> -e.getValue().rank())
                        .thenComparing(e -> e.getKey().toString()))
                .map(Map.Entry::getKey).toList();
    }

    public static List<String> loadProblems() {
        return ROLES.lastErrors();
    }

    /** The player's role in the colony: the owner's, a member's, or none. */
    public static Optional<RoleDef> role(UUID player, Colony colony) {
        if (colony.founder().equals(player)) {
            return Optional.of(OWNER);
        }
        return colony.get(Members.TYPE).flatMap(m -> m.role(player)).map(id -> ROLES.entries().get(id));
    }

    public static Optional<ResourceLocation> roleId(UUID player, Colony colony) {
        if (colony.founder().equals(player)) {
            return Optional.of(RULER);
        }
        return colony.get(Members.TYPE).flatMap(m -> m.role(player));
    }

    public static boolean can(UUID player, Colony colony, String perm) {
        return role(player, colony).map(r -> r.grants(perm)).orElse(false);
    }

    public static boolean isMember(UUID player, Colony colony) {
        return roleId(player, colony).isPresent();
    }

    /** The result of a change: done, or why not. */
    public record Change(boolean ok, Component message) {}

    /**
     * {@code by} gives {@code player} a role (or removes them with an empty one). Needs {@link Perm#MEMBERS}, and
     * both the old and the new role must rank below {@code by}'s own. History {@code colony_role}.
     */
    public static Change setRole(MinecraftServer server, UUID by, Colony colony, UUID player,
            Optional<ResourceLocation> role) {
        if (!can(by, colony, Perm.MEMBERS)) {
            return fail("colony.lotr_port.members.no_right", "You can't change who belongs to %s", colony.name());
        }
        if (colony.founder().equals(player)) {
            return fail("colony.lotr_port.members.ruler", "The ruler's place can't be changed here");
        }
        int mine = role(by, colony).map(RoleDef::rank).orElse(-1);
        int theirs = role(player, colony).map(RoleDef::rank).orElse(-1);
        RoleDef next = role.map(id -> ROLES.entries().get(id)).orElse(null);
        if (role.isPresent() && (next == null || role.get().equals(RULER))) {
            return fail("colony.lotr_port.members.unknown_role", "No such role");
        }
        if (theirs >= mine || (next != null && next.rank() >= mine)) {
            return fail("colony.lotr_port.members.rank", "You can only change roles below your own");
        }
        Colonies colonies = Colonies.get(server);
        Members members = colony.get(Members.TYPE).orElse(Members.NONE).with(player, role);
        colonies.set(colony, Members.TYPE, members);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id());
        tag.putUUID("player", player);
        tag.putUUID("by", by);
        tag.putString("role", role.map(ResourceLocation::toString).orElse(""));
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), ROLE_HISTORY,
                colony.dimension(), Optional.of(colony.hall()), tag));
        return new Change(true, Component.translatableWithFallback("colony.lotr_port.members.set",
                "Role changed"));
    }

    private static Change fail(String key, String fallback, Object... args) {
        return new Change(false, Component.translatableWithFallback(key, fallback, args));
    }
}
