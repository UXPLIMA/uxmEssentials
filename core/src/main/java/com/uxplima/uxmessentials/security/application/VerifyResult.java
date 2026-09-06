package com.uxplima.uxmessentials.security.application;

/**
 * The outcome of checking a submitted factor against a player's two-factor registration during join verification. It
 * is a typed verdict rather than a bare boolean so the keypad adapter can react distinctly: unfreeze on
 * {@link #SUCCESS}, count a failure and re-prompt on {@link #INVALID}, and treat {@link #NOT_ENROLLED} as nothing to
 * verify (the join flow only prompts an enrolled player, so this last case is the defensive tail).
 */
public enum VerifyResult {

    /** The submitted value matched the player's TOTP code or their PIN: verification passes. */
    SUCCESS,

    /** The player is enrolled but the submitted value matched neither factor: a failed attempt. */
    INVALID,

    /** The player holds no factor, so there is nothing to verify. */
    NOT_ENROLLED
}
