package com.uxplima.uxmessentials.shared.domain.action;

/**
 * The kinds of effect a {@link ClickAction} produces when its {@link ClickTrigger} matches a click. Each type
 * interprets the action's raw string {@code value} its own way, a command line, a MiniMessage source, a sound
 * key, or a target server name, and the adapter's runner dispatches accordingly. The domain only names the
 * types and carries the value; how each one runs against Bukkit is an adapter concern.
 *
 * <p>The types split into two roles the adapter's sequencer treats differently. <em>Effect</em> types
 * ({@link #RUN_CONSOLE}, {@link #RUN_PLAYER}, {@link #RUN_PLAYER_AS_OP}, {@link #MESSAGE}, {@link #ACTIONBAR},
 * {@link #TITLE}, {@link #SOUND}, {@link #CONNECT}, {@link #GIVE}) do something visible and are fail-soft. One bad effect is
 * logged and skipped, the chain continues. <em>Gate</em> types ({@link #CHANCE}, {@link #PERMISSION},
 * {@link #CONDITION}, {@link #COST}) decide whether the rest of the chain runs at all: a failed gate stops the
 * remaining actions (a malformed gate spec is the exception: it is logged and skipped, never aborting).
 * {@link #DELAY} is neither: it pauses the chain and resumes the tail later. Whether a type gates or merely
 * effects is the runner's concern; the domain only names it.
 */
public enum ClickActionType {

    /** Run the value as a command from the server console. */
    RUN_CONSOLE,

    /** Run the value as a command performed by the clicking player. */
    RUN_PLAYER,

    /**
     * Run the value as a command performed by the clicking player with operator permissions for that one dispatch.
     * The adapter temporarily grants op, performs the command, and restores the prior op state even if it throws,
     * so a normal player can trigger a privileged command without ever holding op.
     */
    RUN_PLAYER_AS_OP,

    /** Send the value to the player as a chat message. */
    MESSAGE,

    /** Show the value to the player on their action bar. */
    ACTIONBAR,

    /**
     * Show the value to the player as a title. The value is {@code title|subtitle|fadeIn|stay|fadeOut}: the
     * subtitle and the three tick counts are each optional, so {@code title}, {@code title|subtitle} and the full
     * form all parse; an absent or non-numeric timing tail falls back to the vanilla defaults (10/70/20 ticks).
     */
    TITLE,

    /** Play the value as a sound to the player; {@code KEY[:volume[:pitch]]}. */
    SOUND,

    /** Send the player to the value-named server through the proxy's BungeeCord connect channel. */
    CONNECT,

    /**
     * Pause the chain, then resume the remaining actions after the value's whole-tick count. The pause goes
     * through the scheduler; a viewer who disconnects during the wait aborts the rest silently.
     */
    DELAY,

    /**
     * Mark a random-pick group: the value is a positive count {@code n} naming the immediately-following {@code n}
     * actions as the group, of which exactly one, chosen uniformly at random, runs; the rest of the group is
     * skipped and the chain continues after it. A count past the end of the chain clamps to the actions that
     * remain; a non-positive count skips the marker (a no-op).
     */
    RANDOM,

    /**
     * Roll a random gate. The value is a percent (0 to 100); on a failed roll the rest of the chain is aborted, so
     * the actions after it are the "win" branch of a random reward.
     */
    CHANCE,

    /** Gate the rest of the chain on a permission node: when the viewer lacks the value-named node, abort. */
    PERMISSION,

    /**
     * Gate the rest of the chain on a comparison. The value is {@code <left> <op> <right>} with {@code op} one of
     * {@code == != > < >= <=}; both sides may embed placeholders resolved per-viewer. A false comparison aborts;
     * a malformed spec is skipped (the gate is ignored, the chain continues).
     */
    CONDITION,

    /**
     * Charge the viewer the value's amount through the economy seam. Insufficient funds abort the rest of the
     * chain (and tell the viewer); a successful debit continues. With no economy provider the gate is skipped.
     */
    COST,

    /**
     * Give the viewer an item. The value is either {@code <material>[:amount]} (default amount 1) or a serialized
     * full item ({@code b64:…}) carrying all of its NBT. Overflow drops at the viewer's feet.
     */
    GIVE
}
