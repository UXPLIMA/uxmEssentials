package com.uxplima.uxmessentials.vaults.domain;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The opaque, serialized payload of a vault's items at the domain boundary. The architecture persistence
 * invariant (docs/01-architecture.md §"No opaque JSON-blob columns") splits the vault's queryable facts
 * owner, index, size, last-touched, all first-class columns. From the {@code ItemStack[]} contents, which are
 * intrinsically opaque payload and the one part allowed to serialize. The domain therefore never sees a Bukkit
 * {@code ItemStack}: the adapter encodes the live inventory into these bytes on save and decodes them back into
 * items on open, and the persistence layer base64-encodes the same bytes into the {@code contents} TEXT column.
 *
 * <p>An {@link #empty()} payload models a freshly allocated vault with nothing stored. No blob is written for
 * it. The bytes are defensively copied in and out so the value object stays immutable.
 */
public final class VaultContents {

    private static final VaultContents EMPTY = new VaultContents(new byte[0]);

    private final byte[] payload;

    private VaultContents(byte[] payload) {
        this.payload = payload;
    }

    /** A vault with no stored items: nothing to serialize. */
    public static VaultContents empty() {
        return EMPTY;
    }

    /** Wrap an already-serialized item payload (the adapter's encoded inventory bytes). */
    public static VaultContents of(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        return payload.length == 0 ? EMPTY : new VaultContents(payload.clone());
    }

    /** True when the vault holds no serialized items. */
    public boolean isEmpty() {
        return payload.length == 0;
    }

    /** A copy of the serialized payload, or empty when the vault holds nothing. */
    public Optional<byte[]> payload() {
        return isEmpty() ? Optional.empty() : Optional.of(payload.clone());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VaultContents contents && Arrays.equals(payload, contents.payload);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(payload);
    }
}
