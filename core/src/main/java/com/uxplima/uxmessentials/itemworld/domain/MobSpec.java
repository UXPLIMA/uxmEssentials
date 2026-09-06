package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A validated {@code /spawnmob <type> [amount]} request: a normalised entity-type id and a count clamped to a
 * spawn cap. {@code /spawnmob} is an abusable verb. A careless {@code /spawnmob zombie 10000} can wedge a
 * region, so the count cap is a domain rule and the use case audit-logs the spawn.
 *
 * <p>The domain does not know Bukkit {@code EntityType}; it normalises the id to lowercase and the adapter
 * resolves it, mapping an unresolved id to {@link ItemWorldError#UNKNOWN_MOB}. The mount/ridden chaining and
 * baby/variant modifiers live in the adapter's request building; the domain owns the
 * id-shape and the count cap that protect the server.
 *
 * @param typeId the normalised entity-type id
 * @param amount the validated spawn count (always {@code 1..cap})
 */
public record MobSpec(String typeId, int amount) {

    /** The spawn-count ceiling applied when an operator leaves {@code spawnmob-cap} unset. */
    public static final int DEFAULT_CAP = 64;

    public MobSpec {
        Objects.requireNonNull(typeId, "typeId");
        if (typeId.isBlank()) {
            throw new IllegalArgumentException("mob type id must not be blank");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("spawn amount must be positive: " + amount);
        }
    }

    /**
     * Validate a spawn request: a non-blank type id and a count within {@code [1, cap]}. Returns empty for a
     * blank id or an out-of-range count; a use case maps the former to {@link ItemWorldError#UNKNOWN_MOB} and
     * the latter to {@link ItemWorldError#AMOUNT_OUT_OF_RANGE}.
     */
    public static Optional<MobSpec> of(String rawType, int requested, int cap) {
        if (rawType == null || rawType.isBlank()) {
            return Optional.empty();
        }
        int ceiling = cap < 1 ? DEFAULT_CAP : cap;
        if (requested < 1 || requested > ceiling) {
            return Optional.empty();
        }
        return Optional.of(new MobSpec(rawType.trim().toLowerCase(Locale.ROOT), requested));
    }
}
