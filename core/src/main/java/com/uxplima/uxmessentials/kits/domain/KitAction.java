package com.uxplima.uxmessentials.kits.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One operator-authored effect run on a kit claim or deny: its {@link KitActionType type} and an opaque
 * {@code value} payload, the message, title spec, sound spec, command line, particle key, or tick count the
 * adapter interprets. The value is kept as a raw string so the kernel never imports a Bukkit type, mirroring
 * how {@link KitItem#data()} carries an opaque serialized stack and {@link KitRequirement} carries opaque
 * operands; resolving the value into a real effect is the {@code KitActionRunner} adapter's job.
 *
 * <p>{@code beforeItems} orders the action relative to the item grant: a {@code true} action runs before the
 * kit's items are placed in the inventory, a {@code false} action after, so a {@code clear inventory} command
 * can run first and a celebratory firework last. {@code countAsItem} marks an action that effectively hands the
 * player an item (e.g. a {@code crate give} console command) so a future inventory-space pre-check can reserve a
 * slot for it; the kernel only carries the flag today, the granter's space check (item M3) consumes it later.
 *
 * @param type the kind of effect to run
 * @param value the opaque payload the adapter interprets for this type
 * @param beforeItems whether this action runs before (true) or after (false) the item grant
 * @param countAsItem whether this action grants an item that should count against inventory space
 */
public record KitAction(KitActionType type, String value, boolean beforeItems, boolean countAsItem) {

    public KitAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    /** An after-items action of {@code type} carrying {@code value}, neither before-items nor counted as an item. */
    public static KitAction of(KitActionType type, String value) {
        return new KitAction(type, value, false, false);
    }

    /**
     * Parse one {@code claim-actions}/{@code deny-actions} entry, a {@code type}, its {@code value}, and the two
     * optional flags, into an action, or empty when the type token names no known {@link KitActionType} so the
     * codec can skip a malformed entry without failing the whole kit. The value is taken verbatim (it is opaque
     * operator data); a {@code WAIT_TICKS} entry with a non-blank value is accepted here and validated by the
     * runner, which skips a non-numeric delay rather than throwing on the claim path.
     */
    public static Optional<KitAction> parse(String type, String value, boolean beforeItems, boolean countAsItem) {
        Objects.requireNonNull(value, "value");
        return KitActionType.parse(type).map(t -> new KitAction(t, value, beforeItems, countAsItem));
    }
}
