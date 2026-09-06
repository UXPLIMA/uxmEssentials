package com.uxplima.uxmessentials.security.application;

/** The outcome of {@code /2fa confirm}: the enrolment was enabled, there was nothing pending, or the code was wrong. */
public enum TotpConfirmResult {

    /** The code matched the pending secret, which is now persisted as the player's TOTP factor. */
    ENABLED,

    /** There was no pending enrolment: the player must run {@code /2fa setup} first. */
    NO_PENDING,

    /** The submitted code did not verify against the pending secret; nothing was enabled. */
    INVALID_CODE
}
