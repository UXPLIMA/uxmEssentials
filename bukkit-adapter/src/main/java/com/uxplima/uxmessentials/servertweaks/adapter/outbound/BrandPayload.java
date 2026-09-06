package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Encodes a server brand into the byte payload the {@code minecraft:brand} plugin-message channel carries: a single
 * Minecraft-protocol string, i.e. a VarInt length prefix followed by the UTF-8 bytes of the brand. This is the exact
 * shape the vanilla client reads to populate the "server brand" line on the F3 debug screen, so re-sending it after
 * join overrides the brand the server announced during the configuration phase.
 *
 * <p>Pure byte manipulation, no Bukkit types, so the encoding is unit-testable on its own.
 */
final class BrandPayload {

    /** VarInt continuation bit: set on every byte except the last of a multi-byte value. */
    private static final int CONTINUATION_BIT = 0x80;

    /** The seven value bits each VarInt byte carries. */
    private static final int SEGMENT_BITS = 0x7F;

    private BrandPayload() {}

    /** Build the {@code minecraft:brand} payload for {@code brand}: its length as a VarInt, then its UTF-8 bytes. */
    static byte[] encode(String brand) {
        Objects.requireNonNull(brand, "brand");
        byte[] utf8 = brand.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(utf8.length + 5);
        writeVarInt(buffer, utf8.length);
        buffer.write(utf8, 0, utf8.length);
        return buffer.toByteArray();
    }

    /** Write {@code value} as a little-endian base-128 VarInt (the protocol's length/int encoding). */
    private static void writeVarInt(ByteArrayOutputStream buffer, int value) {
        int remaining = value;
        while ((remaining & ~SEGMENT_BITS) != 0) {
            buffer.write((remaining & SEGMENT_BITS) | CONTINUATION_BIT);
            remaining >>>= 7;
        }
        buffer.write(remaining);
    }
}
