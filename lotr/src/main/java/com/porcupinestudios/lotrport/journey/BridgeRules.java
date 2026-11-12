package com.porcupinestudios.lotrport.journey;

import java.util.List;
import java.util.Optional;

/**
 * Whether a marked line of blocks is a bridge (plain rules). Every column along it must have a deck a party can stand
 * on with room above; at least one must have water under its deck in a river; both ends must be on dry land. The
 * marker reads the columns from the world; these rules decide.
 */
public final class BridgeRules {
    private BridgeRules() {}

    /**
     * One column along the line.
     *
     * @param deck a solid top at the marked height (or one block off it, for slabs and steps)
     * @param headroom two blocks of air above the deck
     * @param water water under the deck
     * @param river that water is a river (not a lake or the sea)
     */
    public record Column(boolean deck, boolean headroom, boolean water, boolean river) {}

    /** The reason it isn't a bridge (a lang key's last part), or empty if it is. */
    public static Optional<String> check(List<Column> columns, int maxLength) {
        if (columns.size() < 3) {
            return Optional.of("too_short");
        }
        if (columns.size() > maxLength) {
            return Optional.of("too_long");
        }
        if (columns.get(0).water() || columns.get(columns.size() - 1).water()) {
            return Optional.of("ends_in_water");
        }
        boolean crosses = false;
        for (Column c : columns) {
            if (!c.deck()) {
                return Optional.of("gap");
            }
            if (!c.headroom()) {
                return Optional.of("blocked");
            }
            crosses |= c.water() && c.river();
        }
        return crosses ? Optional.empty() : Optional.of("no_river");
    }
}
