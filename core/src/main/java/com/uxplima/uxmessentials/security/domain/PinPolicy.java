package com.uxplima.uxmessentials.security.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The rules a PIN second factor must satisfy: a numeric-only string within a configured length range, and not one of
 * the sequences everybody tries first. It is a pure value object so the format check is unit-testable without a
 * database or a player, and so the same policy governs both enrolment ({@code /pin set}) and entry through the
 * keypad GUI.
 *
 * <p>The blocked list is the part length rules cannot do: {@code 1234} and {@code 0000} are perfectly well-formed
 * four-digit PINs, and are also the first two guesses anybody would make. Refusing them at enrolment costs the
 * honest player one retry and costs an attacker the cheapest attack there is. The list is operator-configured, so a
 * server can add its own (its name spelled on a phone keypad, its founding year) or empty it entirely.
 *
 * <p>The policy deliberately checks <b>format only</b> (length, digits and the blocked list) and never touches the
 * stored hash: a PIN is one-way hashed the moment it passes this check, so this class sees the plaintext for exactly
 * as long as it takes to measure it. Numeric-only is intentional; the join-verification factor is a keypad, so a PIN
 * must be enterable there.
 *
 * @param minLength the fewest digits a PIN may have (inclusive)
 * @param maxLength the most digits a PIN may have (inclusive)
 * @param blocked the PINs refused outright regardless of length, empty to refuse none
 */
public record PinPolicy(int minLength, int maxLength, Set<String> blocked) {

    public PinPolicy {
        if (minLength < 1) {
            throw new IllegalArgumentException("pin min-length must be at least 1: " + minLength);
        }
        if (maxLength < minLength) {
            throw new IllegalArgumentException(
                    "pin max-length (" + maxLength + ") must be at least min-length (" + minLength + ")");
        }
        Objects.requireNonNull(blocked, "blocked");
        blocked = Set.copyOf(blocked);
    }

    /** A policy with no blocked list, for the callers that only care about the length range. */
    public PinPolicy(int minLength, int maxLength) {
        this(minLength, maxLength, Set.of());
    }

    /** Check {@code candidate} against this policy, returning the typed reason it passed or failed. */
    public PinValidation validate(String candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!isAllDigits(candidate)) {
            return PinValidation.NOT_NUMERIC;
        }
        if (candidate.length() < minLength) {
            return PinValidation.TOO_SHORT;
        }
        if (candidate.length() > maxLength) {
            return PinValidation.TOO_LONG;
        }
        if (blocked.contains(candidate)) {
            return PinValidation.BLOCKED;
        }
        return PinValidation.OK;
    }

    private static boolean isAllDigits(String candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (!Character.isDigit(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
