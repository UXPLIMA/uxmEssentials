package com.uxplima.uxmessentials.regions.domain;

/**
 * Signals that a region mutation ({@code create} / {@code setFlag}) could not be applied: WorldGuard rejected the
 * request or a reflective call into its API failed. Unlike the reads, a mutation must not fail silently: a swallowed
 * write would leave the operator believing a change landed that did not, so the adapter wraps the underlying failure
 * in this domain exception and the command surface turns it into an operator-facing "could not apply" message.
 *
 * <p>Pure Java: it names no {@code com.sk89q} type. The adapter that catches a reflective failure wraps it here so
 * the cause is preserved without leaking a WorldGuard type across the port.
 */
public final class RegionServiceException extends RuntimeException {

    public RegionServiceException(String message) {
        super(message);
    }

    public RegionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
