package dev.bibo.aqua.form;

/**
 * The two-press selection state machine: first number picks a <b>group</b>, the next picks an
 * <b>ability</b> inside it, and F drops back to picking a group.
 *
 * <p>Pure logic on purpose. This is the part that has to be exactly right — an earlier attempt to
 * infer the same behaviour from how far the hotbar slot had moved was ambiguous by construction and
 * produced a different result depending on which slot you happened to start from. Here a press is
 * just a number, the current state is explicit, and the outcome is a value that can be asserted
 * without a server.
 */
public final class Selection {

    /** What a press did. */
    public enum Kind {
        /** A group was chosen; the player is now picking an ability inside it. */
        GROUP,
        /** An ability was chosen. */
        ABILITY,
        /** The number does not name anything in the current state — nothing changed. */
        REJECTED
    }

    /** Outcome of one press: what happened, and the resulting indices (-1 = none). */
    public record Press(Kind kind, int group, int ability) {
        public boolean ok() {
            return kind != Kind.REJECTED;
        }
    }

    /** No group chosen yet — the next press picks one. */
    public static final int NO_GROUP = -1;

    private Selection() {}

    /**
     * Decide what pressing {@code number} (1-based, as printed on the hotbar) does.
     *
     * @param currentGroup    the group already chosen, or {@link #NO_GROUP}
     * @param number          the key pressed, 1-based
     * @param groupCount      how many groups exist
     * @param abilityCount    how many abilities the current group has (ignored when choosing a group)
     */
    public static Press press(int currentGroup, int number, int groupCount, int abilityCount) {
        if (currentGroup == NO_GROUP) {
            if (number < 1 || number > groupCount) return new Press(Kind.REJECTED, currentGroup, -1);
            return new Press(Kind.GROUP, number - 1, 0);
        }
        if (number < 1 || number > abilityCount) return new Press(Kind.REJECTED, currentGroup, -1);
        return new Press(Kind.ABILITY, currentGroup, number - 1);
    }
}
