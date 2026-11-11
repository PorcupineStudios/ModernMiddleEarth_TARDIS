package com.porcupinestudios.lotrport.group;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * One person's place in a group.
 *
 * @param role what they do there ({@link GroupKinds}' role ids; roles become data files later)
 * @param joined game time they joined
 * @param flags free markers a system may set, such as {@code rare} for a citizen won by a recruitment quest
 */
public record Member(PersonId person, ResourceLocation role, long joined, Set<String> flags) {
    public Member {
        flags = Set.copyOf(flags);
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("person", person.value());
        t.putString("role", role.toString());
        t.putLong("joined", joined);
        if (!flags.isEmpty()) {
            ListTag list = new ListTag();
            flags.stream().sorted().forEach(f -> list.add(StringTag.valueOf(f)));
            t.put("flags", list);
        }
        return t;
    }

    static Optional<Member> load(CompoundTag t) {
        ResourceLocation role = ResourceLocation.tryParse(t.getString("role"));
        if (role == null || !t.hasUUID("person")) {
            return Optional.empty();
        }
        Set<String> flags = new LinkedHashSet<>();
        for (Tag f : t.getList("flags", Tag.TAG_STRING)) {
            flags.add(f.getAsString());
        }
        return Optional.of(new Member(PersonId.of(t.getUUID("person")), role, t.getLong("joined"), flags));
    }
}
