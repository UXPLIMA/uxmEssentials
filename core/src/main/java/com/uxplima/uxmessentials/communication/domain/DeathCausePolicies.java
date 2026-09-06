package com.uxplima.uxmessentials.communication.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The death channel's per-cause policy table: a {@link MessagePolicy} for each {@link DeathCause} an operator has
 * authored a template for, over a default policy that applies to every other cause. It is pure operator content and
 * answers one question, "which {@link MessagePolicy} renders this death?", through {@link #policyFor(DeathCause)},
 * which returns the cause's own policy when one is configured and the default otherwise.
 *
 * <p>This keeps the pre-per-cause behaviour intact: a file that authors only the single {@code death} block yields
 * a table with an empty per-cause map, so every death, whatever its cause, resolves through the default policy
 * exactly as before. A cause the adapter classifies as {@link DeathCause#OTHER} is never expected to carry its own
 * entry, so it too falls through to the default.
 *
 * @param byCause the per-cause overrides, keyed by the death's cause; a cause absent here takes the default
 * @param defaultPolicy the policy applied to any cause with no override (and to the whole channel pre-migration)
 */
public record DeathCausePolicies(Map<DeathCause, MessagePolicy> byCause, MessagePolicy defaultPolicy) {

    public DeathCausePolicies {
        Objects.requireNonNull(byCause, "byCause");
        Objects.requireNonNull(defaultPolicy, "defaultPolicy");
        // Copy into an EnumMap so lookups are branch-free and the table cannot be mutated through the accessor.
        byCause = byCause.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(byCause));
    }

    /** A table with no per-cause overrides: every death takes {@code defaultPolicy}, the single-policy behaviour. */
    public static DeathCausePolicies ofDefault(MessagePolicy defaultPolicy) {
        return new DeathCausePolicies(Map.of(), defaultPolicy);
    }

    /** The policy that renders a death of {@code cause}: the cause's own override when configured, else the default. */
    public MessagePolicy policyFor(DeathCause cause) {
        Objects.requireNonNull(cause, "cause");
        return byCause.getOrDefault(cause, defaultPolicy);
    }
}
