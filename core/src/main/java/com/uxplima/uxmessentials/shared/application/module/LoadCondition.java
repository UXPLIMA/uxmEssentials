package com.uxplima.uxmessentials.shared.application.module;

import java.util.Optional;

/**
 * A capability gate independent of the operator's enable switch.
 *
 * <p>{@link FeatureModule#enabled} answers "did the operator switch this on?"; a {@code
 * LoadCondition} answers "can this module run in this environment?", a soft dependency present, a
 * required runtime flavour, and so on. When the condition is unmet the module is skipped with a
 * human-readable reason rather than half-wired, the same zero-state path a disabled module takes.
 */
@FunctionalInterface
public interface LoadCondition {

    /** Empty when the module may load; a human-readable reason otherwise. */
    Optional<String> unmetReason(ModuleContext probe);
}
