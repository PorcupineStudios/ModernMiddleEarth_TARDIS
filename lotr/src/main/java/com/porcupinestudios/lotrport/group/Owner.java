package com.porcupinestudios.lotrport.group;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Who a group belongs to: a player, an AI ruler or (later) a realm. */
public record Owner(Kind kind, UUID id) {
    public enum Kind {
        PLAYER, AI_RULER, REALM;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind parse(String key) {
            for (Kind k : values()) {
                if (k.key().equals(key)) {
                    return k;
                }
            }
            return PLAYER;
        }
    }

    public static Owner player(UUID id) {
        return new Owner(Kind.PLAYER, id);
    }

    public static Owner aiRuler(UUID id) {
        return new Owner(Kind.AI_RULER, id);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind.key());
        t.putUUID("id", id);
        return t;
    }

    static Owner load(CompoundTag t) {
        return new Owner(Kind.parse(t.getString("kind")), t.getUUID("id"));
    }
}
