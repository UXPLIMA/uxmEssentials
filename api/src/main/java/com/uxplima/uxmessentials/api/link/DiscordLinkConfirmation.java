package com.uxplima.uxmessentials.api.link;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The host plugin's account-linking confirmation seam, consumed by the optional Discord bridge through
 * Bukkit's {@code ServicesManager}. There is no compile-time link between the two jars (docs/01-architecture,
 * docs/09-deployment Path C). The host registers an implementation backed by its {@code ConfirmLink} use case
 * over the jOOQ link store; the bridge looks it up once its gateway is ready and, when present, registers the
 * {@code /link} slash command that redeems a code through it. When the host exposes no implementation the
 * bridge stays connected but linking is dormant, exactly like the notification source.
 *
 * <p>This contract lives in {@code :api} (the only module both jars depend on) and carries no domain type
 * the code and the Discord id cross the seam as plain strings, the outcome as a small enum plus an optional
 * resolved player name, so {@code :api} stays a pure-contract module with no persistence or Bukkit dependency.
 * Implementations run on the bridge's calling thread (JDA's own pool, off any server tick); they must not block
 * a tick and must validate their inputs.
 */
@NullMarked
public interface DiscordLinkConfirmation {

    /**
     * Redeem a link code presented in Discord, binding the presenting Discord account to the player who issued
     * the code. Never throws for a modelled outcome. A bad or expired code, or an already-bound Discord
     * account, is reported through the returned {@link Outcome}, not an exception.
     *
     * @param code the code the Discord user typed, in any case (the host normalises it)
     * @param discordId the presenting Discord user's snowflake id
     * @return the outcome, carrying the bound player's name on success
     */
    Outcome confirm(String code, String discordId);

    /** Why a confirmation succeeded or failed, mapped by the bridge to an ephemeral Discord reply. */
    enum Result {
        /** The code was redeemed; {@link Outcome#playerName()} carries the bound player. */
        LINKED,
        /** No outstanding code matched the input. */
        NO_CODE,
        /** The code's window had already closed. */
        EXPIRED,
        /** The presenting Discord account is already bound to a different player. */
        ALREADY_LINKED,
        /** The host rejected the input as malformed (not a valid code or id shape). */
        INVALID
    }

    /**
     * A confirmation result. {@link #playerName()} is present only when {@link #result()} is {@link
     * Result#LINKED}.
     *
     * @param result the outcome kind
     * @param playerName the bound player's name when linked, otherwise {@code null}
     */
    record Outcome(Result result, @Nullable String playerName) {

        public Outcome {
            Objects.requireNonNull(result, "result");
        }

        /** A success outcome carrying the bound player's name. */
        public static Outcome linked(String playerName) {
            return new Outcome(Result.LINKED, Objects.requireNonNull(playerName, "playerName"));
        }

        /** A failure outcome of the given non-success result. */
        public static Outcome failure(Result result) {
            if (result == Result.LINKED) {
                throw new IllegalArgumentException("LINKED is not a failure result");
            }
            return new Outcome(result, null);
        }

        /** The bound player's name when this is a {@link Result#LINKED} outcome. */
        public Optional<String> playerNameIfLinked() {
            return Optional.ofNullable(playerName);
        }
    }
}
