package com.uxplima.uxmessentials.kits.application.port;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.kits.domain.KitRequirement;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow placeholder seam the kits context owns so a kit's claim {@link KitRequirement requirements} can be
 * evaluated <em>without</em> a hard dependency on any placeholder engine. This is the entire surface kits needs
 *, resolve a single condition against a player, expressed in kits' own terms; the adapter supplies an
 * implementation that resolves the {@code %placeholder%} operands through PlaceholderAPI and compares the
 * resolved values, and the kits context never imports a PlaceholderAPI type.
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into the claim gate, mirroring
 * {@link KitEconomy}. When no evaluator is present (PlaceholderAPI absent), a kit with no requirements is
 * claimable, but a kit that <em>declares</em> requirements <strong>fails closed</strong>: it cannot be
 * claimed, because the conditions cannot be checked. This matches the competitor behaviour where an
 * unresolvable condition denies the claim rather than silently passing it.
 *
 * <p>Threading: a PlaceholderAPI lookup is a main-thread (or owning-region-thread) operation. The gate runs on
 * the claim path, which is already on the recipient's owning thread; an adapter must never resolve a
 * placeholder off that thread.
 */
public interface RequirementEvaluator {

    /** True when {@code who} satisfies the single condition {@code requirement}; an unresolvable side fails closed. */
    boolean passes(PlayerRef who, KitRequirement requirement);

    /**
     * True when {@code who} satisfies every requirement in {@code requirements} (vacuously true for an empty
     * list). The default short-circuits on the first failing condition so an expensive later placeholder is not
     * resolved once the claim is already denied.
     */
    default boolean passesAll(PlayerRef who, List<KitRequirement> requirements) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requirements, "requirements");
        for (KitRequirement requirement : requirements) {
            if (!passes(who, requirement)) {
                return false;
            }
        }
        return true;
    }
}
