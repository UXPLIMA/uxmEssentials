package com.uxplima.uxmessentials.moderation.application.port;

/**
 * Resolves the named jails an operator configures in {@code moderation.conf}. The jail location itself is not
 * stored on a sanction row, only the jail's name is, so a {@code /jail <player> <jail>} validates the name
 * against this directory before writing the sanction, and the join/{@code /jail} listeners ask the
 * {@link Sanctions} port to teleport into the resolved location.
 *
 * <p>This is a config seam, not DB-backed: it reflects the live {@code moderation.conf} and swaps atomically
 * on reload, so a jail renamed in config takes effect without a restart.
 */
public interface JailDirectory {

    /** True when {@code jail} is a configured jail name. */
    boolean exists(String jail);

    /** Whether {@code jail} counts down on online time only (the default) or wall-clock, per its config. */
    boolean isWallClock(String jail);

    /** The configured jail names, sorted, for {@code /jails} to list back to an operator. */
    java.util.List<String> names();

    /** Non-blocking config-only jail names for tab-completion; the DB-backed union lives in {@link #names()}. */
    default java.util.List<String> peekNames() {
        return names();
    }
}
