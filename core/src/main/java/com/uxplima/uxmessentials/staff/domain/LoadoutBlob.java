package com.uxplima.uxmessentials.staff.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * One opaque, serialized region of a {@link SavedLoadout}. A player's main inventory, armour, off-hand, or
 * active potion effects, each encoded to bytes by the adapter. Mirrors the vaults context's
 * {@code VaultContents}: the domain never sees a Bukkit {@code ItemStack} or {@code PotionEffect}, only these
 * bytes, which the adapter encodes on capture and decodes on restore (and the persistence layer base64s into
 * a TEXT column).
 *
 * <p>This is a value object rather than a raw {@code byte[]} record component so {@link SavedLoadout} can be a
 * record without tripping the array-record-component rule, and so the bytes are defensively copied in and out
 * to keep the value immutable.
 */
public final class LoadoutBlob {

    private static final LoadoutBlob EMPTY = new LoadoutBlob(new byte[0]);

    private final byte[] bytes;

    private LoadoutBlob(byte[] bytes) {
        this.bytes = bytes;
    }

    /** An empty region: nothing serialized. */
    public static LoadoutBlob empty() {
        return EMPTY;
    }

    /** Wrap an already-serialized region payload, defensively copying it. */
    public static LoadoutBlob of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return bytes.length == 0 ? EMPTY : new LoadoutBlob(bytes.clone());
    }

    /** True when this region holds nothing. */
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    /** A fresh copy of the serialized bytes, so the stored value stays immutable. */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LoadoutBlob that && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        // The bytes are opaque; print only the length so logs stay readable.
        return "LoadoutBlob[" + bytes.length + " bytes]";
    }
}
