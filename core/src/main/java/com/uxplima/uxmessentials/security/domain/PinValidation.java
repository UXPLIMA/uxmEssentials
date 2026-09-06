package com.uxplima.uxmessentials.security.domain;

/**
 * The outcome of checking a candidate PIN against a {@link PinPolicy}. It is a typed verdict rather than a bare
 * boolean so the caller can tell the player <em>why</em> a PIN was refused. Too short, too long, or containing a
 * non-digit, and the enrolment use case maps each to its own message key.
 */
public enum PinValidation {

    /** The PIN satisfies the policy and may be hashed and stored. */
    OK,

    /** The PIN has fewer digits than the policy's minimum. */
    TOO_SHORT,

    /** The PIN has more digits than the policy's maximum. */
    TOO_LONG,

    /** The PIN contains a character that is not a digit. */
    NOT_NUMERIC,

    /**
     * The PIN is on the blocked list: a sequence so common that it is the first thing anybody guessing would try.
     * Length rules cannot catch these, because {@code 1234} is a perfectly well-formed four-digit PIN.
     */
    BLOCKED
}
