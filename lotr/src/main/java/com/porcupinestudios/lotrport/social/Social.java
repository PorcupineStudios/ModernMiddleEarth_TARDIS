package com.porcupinestudios.lotrport.social;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who players let see them (on the map), and player groups: LOTR's fellowships, replaced by colony membership plus
 * these small named groups. Saved in the overworld's data storage. {@link #canSee} is the one check the map uses;
 * the server filters every position before sending it, so a hidden player's position never reaches a client.
 */
public final class Social extends SavedData {
    public static final String FILE = "lotr_port_social";
    public static final int DATA_VERSION = 1;
    private static final SavedData.Factory<Social> FACTORY = new SavedData.Factory<>(Social::new, Social::load, null);

    /**
     * One player's choice of who sees their map icon.
     *
     * @param everyone every player
     * @param colonies members of any colony this player belongs to
     * @param groups members of this player's groups
     * @param players these players, whatever else is set
     */
    public record Prefs(boolean everyone, boolean colonies, boolean groups, List<UUID> players) {
        public static final Prefs DEFAULT = new Prefs(false, true, true, List.of());
        public static final Codec<Prefs> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("everyone", false).forGetter(Prefs::everyone),
                Codec.BOOL.optionalFieldOf("colonies", true).forGetter(Prefs::colonies),
                Codec.BOOL.optionalFieldOf("groups", true).forGetter(Prefs::groups),
                UUIDUtil.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(Prefs::players))
                .apply(i, Prefs::new));

        public Prefs {
            players = List.copyOf(players);
        }

        public Prefs withEveryone(boolean v) {
            return new Prefs(v, colonies, groups, players);
        }

        public Prefs withColonies(boolean v) {
            return new Prefs(everyone, v, groups, players);
        }

        public Prefs withGroups(boolean v) {
            return new Prefs(everyone, colonies, v, players);
        }

        public Prefs withPlayer(UUID p, boolean add) {
            Set<UUID> next = new LinkedHashSet<>(players);
            if (add) {
                next.add(p);
            } else {
                next.remove(p);
            }
            return new Prefs(everyone, colonies, groups, new ArrayList<>(next));
        }
    }

    /** A named group of players; its leader may add and remove members. */
    public record Group(UUID id, String name, UUID leader, List<UUID> members) {
        public static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Group::id),
                Codec.STRING.fieldOf("name").forGetter(Group::name),
                UUIDUtil.CODEC.fieldOf("leader").forGetter(Group::leader),
                UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Group::members))
                .apply(i, Group::new));

        public Group {
            members = List.copyOf(members);
        }

        public boolean has(UUID p) {
            return leader.equals(p) || members.contains(p);
        }

        Group with(UUID p, boolean add) {
            Set<UUID> next = new LinkedHashSet<>(members);
            if (add) {
                next.add(p);
            } else {
                next.remove(p);
            }
            return new Group(id, name, leader, new ArrayList<>(next));
        }
    }

    private final Map<UUID, Prefs> prefs = new LinkedHashMap<>();
    private final Map<UUID, Group> groups = new LinkedHashMap<>();

    public static Social get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Prefs prefs(UUID player) {
        return prefs.getOrDefault(player, Prefs.DEFAULT);
    }

    public void setPrefs(UUID player, Prefs p) {
        prefs.put(player, p);
        setDirty();
    }

    public List<Group> groupsOf(UUID player) {
        return groups.values().stream().filter(g -> g.has(player)).toList();
    }

    public Optional<Group> group(UUID leader, String name) {
        return groups.values().stream().filter(g -> g.leader().equals(leader) && g.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** A new group led by {@code leader}; empty if they already lead one by that name. */
    public Optional<Group> createGroup(UUID leader, String name) {
        String clean = name.strip();
        if (clean.isEmpty() || clean.length() > 24 || group(leader, clean).isPresent()) {
            return Optional.empty();
        }
        Group g = new Group(UUID.randomUUID(), clean, leader, List.of());
        groups.put(g.id(), g);
        setDirty();
        return Optional.of(g);
    }

    /** The leader adds or removes a member. */
    public boolean setMember(UUID leader, String groupName, UUID player, boolean add) {
        Optional<Group> g = group(leader, groupName);
        if (g.isEmpty() || player.equals(leader)) {
            return false;
        }
        groups.put(g.get().id(), g.get().with(player, add));
        setDirty();
        return true;
    }

    /** A member leaves a group; the leader leaving disbands it. */
    public void leave(UUID player, UUID groupId) {
        Group g = groups.get(groupId);
        if (g == null) {
            return;
        }
        if (g.leader().equals(player)) {
            groups.remove(groupId);
        } else {
            groups.put(groupId, g.with(player, false));
        }
        setDirty();
    }

    /** Whether {@code viewer} may see {@code target}'s icon on the map. */
    public boolean canSee(MinecraftServer server, UUID viewer, UUID target) {
        if (viewer.equals(target)) {
            return true;
        }
        Prefs p = prefs(target);
        if (p.everyone() || p.players().contains(viewer)) {
            return true;
        }
        if (p.groups()) {
            for (Group g : groups.values()) {
                if (g.has(viewer) && g.has(target)) {
                    return true;
                }
            }
        }
        if (p.colonies()) {
            for (Colony c : Colonies.get(server).all()) {
                if (Permissions.isMember(viewer, c) && Permissions.isMember(target, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("prefs", Codec.unboundedMap(UUIDUtil.STRING_CODEC, Prefs.CODEC).encodeStart(NbtOps.INSTANCE,
                prefs).getOrThrow());
        tag.put("groups", Group.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(groups.values()))
                .getOrThrow());
        return tag;
    }

    public static Social load(CompoundTag tag, HolderLookup.Provider registries) {
        Social out = new Social();
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Prefs.CODEC).parse(NbtOps.INSTANCE, tag.get("prefs"))
                .resultOrPartial(e -> LotrPort.LOG.error("[social] {}", e)).ifPresent(out.prefs::putAll);
        Group.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("groups"))
                .resultOrPartial(e -> LotrPort.LOG.error("[social] {}", e))
                .ifPresent(l -> l.forEach(g -> out.groups.put(g.id(), g)));
        return out;
    }
}
