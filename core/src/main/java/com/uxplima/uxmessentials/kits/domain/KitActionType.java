package com.uxplima.uxmessentials.kits.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of effect a {@link KitAction} can run on a kit claim or deny. Each constant carries the kebab-case
 * token an operator writes for it in a kit's {@code claim-actions}/{@code deny-actions} block ({@code SOUND} ↔
 * {@code sound}, {@code CONSOLE_COMMAND} ↔ {@code console-command}); {@link #parse(String)} maps a written token
 * back to the constant so the codec never branches on the symbol. The kernel only models <em>which</em> effect
 * to run, the {@code KitActionRunner} adapter owns how each one is performed on the server.
 *
 * <p>{@link #PARTICLE} is included so the legacy single-{@code particles} field maps cleanly into one action,
 * and {@link #WAIT_TICKS} carries the inter-action delay the runner's sequencer honours (the remaining actions
 * are re-scheduled after the stated tick count through the Scheduler port, never a {@code BukkitRunnable}).
 */
public enum KitActionType {

    /** Send a private MiniMessage line to the player (operator-authored text). */
    MESSAGE("message"),

    /** Send a MiniMessage line to every online player. */
    BROADCAST("broadcast"),

    /** Show a MiniMessage line on the player's action bar. */
    ACTIONBAR("actionbar"),

    /** Show a title/subtitle to the player; value carries the fade/stay/fade timings and the two lines. */
    TITLE("title"),

    /** Play a sound to the player; value is a {@code KEY;volume;pitch} spec. */
    SOUND("sound"),

    /** Spawn a particle effect at the player; value is the particle key (the legacy {@code particles} target). */
    PARTICLE("particle"),

    /** Launch a firework near the player; value is a {@code colors:..,.. type:.. power:..} spec. */
    FIREWORK("firework"),

    /** Run a command from the console; value is the command line with claim tokens substituted. */
    CONSOLE_COMMAND("console-command"),

    /** Run a command as the player; value is the command line with claim tokens substituted. */
    PLAYER_COMMAND("player-command"),

    /**
     * Empty the recipient's inventory. Carries no value. Operators put it in a kit's {@code claim-actions}
     * block with {@code before-items: true} so a PvP or loadout kit wipes the inventory before its items are
     * granted, dealing a clean set rather than topping up whatever the player was holding.
     */
    CLEAR_INVENTORY("clear-inventory"),

    /** Pause the action sequence; value is the whole-tick delay before the remaining actions run. */
    WAIT_TICKS("wait-ticks");

    private final String token;

    KitActionType(String token) {
        this.token = token;
    }

    /** The kebab-case token this type is written as in a kit's action block. */
    public String token() {
        return token;
    }

    /**
     * Parse an operator-written action type token into its constant, tolerating surrounding spaces, case, and
     * the {@code _} an operator might use in place of the canonical {@code -} ({@code console_command} reads the
     * same as {@code console-command}). Returns empty when the token names no known type so the codec can skip a
     * malformed entry rather than fail the whole kit.
     */
    public static Optional<KitActionType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        for (KitActionType type : values()) {
            if (type.token.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
