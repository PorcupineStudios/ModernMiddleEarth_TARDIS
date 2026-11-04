package com.porcupinestudios.tardisrb.door;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The door lock, as in the original TARDIS mod (Aaron, 2026-11-04): sneak-using the doors cycles it through
 * {@link State}. The original's key holders are the crew here (there is no key item yet). Stored in the record's
 * module data {@code tardis_rb:lock}; a TARDIS with none is {@link State#CREW}, which is how doors worked before.
 */
public final class DoorLock {
    public static final String MODULE = "tardis_rb:lock";

    /** In the original's order: open for all, owner only, owner and key holders (crew), locked. */
    public enum State {
        OPEN, OWNER, CREW, LOCKED;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private DoorLock() {}

    public static State state(TardisRecord tardis) {
        String s = tardis.moduleData(MODULE).getString("state");
        for (State st : State.values()) {
            if (st.key().equals(s)) {
                return st;
            }
        }
        return State.CREW;
    }

    static void set(TardisRecord tardis, State state) {
        CompoundTag tag = tardis.moduleData(MODULE);
        tag.putString("state", state.key());
        tardis.setModuleData(MODULE, tag);
    }

    private static boolean isOwner(TardisRecord tardis, UUID player) {
        return tardis.owner().map(player::equals).orElse(false);
    }

    /** May this player open or close the doors? Inside, anyone may unless it is locked; outside, by the lock. */
    public static boolean mayUse(TardisRecord tardis, ServerPlayer player, boolean inside) {
        State state = state(tardis);
        if (inside) {
            return state != State.LOCKED;
        }
        return switch (state) {
            case OPEN -> true;
            case OWNER -> isOwner(tardis, player.getUUID());
            case CREW -> tardis.isCrew(player.getUUID());
            case LOCKED -> false;
        };
    }

    /**
     * Sneak-use: moves the lock to the next state and tells the player, with the original's rules. Outside you need
     * to be crew (the original's key), can't undo "locked" or someone else's "owner only", and can't lock it (only
     * from inside); only the owner can set "owner only". Returns false, with a message, if this player can't change it.
     */
    public static boolean cycle(TardisRecord tardis, ServerPlayer player, boolean inside) {
        State state = state(tardis);
        boolean owner = isOwner(tardis, player.getUUID());
        boolean allowed = tardis.isCrew(player.getUUID()) && (inside || state != State.LOCKED)
                && (inside || owner || state != State.OWNER);
        if (!allowed) {
            player.displayClientMessage(Component.translatableWithFallback("tardis_rb.lock.denied",
                    "You can't change this lock"), true);
            return false;
        }
        State[] all = State.values();
        State next = state;
        for (int i = 0; i < all.length; i++) {
            next = all[(next.ordinal() + 1) % all.length];
            boolean skip = (next == State.LOCKED && !inside) || (next == State.OWNER && !owner);
            if (!skip) {
                break;
            }
        }
        set(tardis, next);
        player.displayClientMessage(message(next), true);
        return true;
    }

    public static Component message(State state) {
        return switch (state) {
            case OPEN -> Component.translatableWithFallback("tardis_rb.lock.open", "The door will open for all");
            case OWNER -> Component.translatableWithFallback("tardis_rb.lock.owner",
                    "The door will only open for its owner");
            case CREW -> Component.translatableWithFallback("tardis_rb.lock.crew",
                    "The door will open for its owner and crew");
            case LOCKED -> Component.translatableWithFallback("tardis_rb.lock.locked", "The door will not open");
        };
    }
}
