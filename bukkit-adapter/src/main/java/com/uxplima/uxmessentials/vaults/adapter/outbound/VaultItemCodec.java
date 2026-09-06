package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.PayloadLimits;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption layer between a live vault inventory's {@code ItemStack[]} and the domain's opaque
 * {@link VaultContents} payload. The architecture persistence invariant (docs/01-architecture.md) makes the
 * {@code ItemStack[]} contents the one part of a vault that serializes; this codec is the only place the
 * vaults context touches an {@code ItemStack}, and the {@code :core} layer never sees one.
 *
 * <p>Slot positions matter for a stored container (a chest's layout is meaningful), so the payload is a
 * slot-indexed stream: a header of the slot count, then for each occupied slot its index and the item's own
 * serialized bytes (Paper's {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])},
 * which round-trips the full item: components, enchantments, custom name). Empty slots write nothing, so a
 * sparsely filled vault stays compact. {@link #encode} of an all-empty array yields {@link VaultContents#empty()},
 * so no blob is written for a vault holding nothing.
 */
@NullMarked
public final class VaultItemCodec {

    private VaultItemCodec() {}

    /** Serialize {@code contents} (a vault inventory's slots) into the opaque {@link VaultContents} payload. */
    public static VaultContents encode(@Nullable ItemStack[] contents) {
        Objects.requireNonNull(contents, "contents");
        if (isEmpty(contents)) {
            return VaultContents.empty();
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (int slot = 0; slot < contents.length; slot++) {
                writeSlot(out, slot, contents[slot]);
            }
            out.writeInt(-1); // end-of-stream sentinel
            return VaultContents.of(bytes.toByteArray());
        } catch (IOException io) {
            throw new UncheckedIOException("vault contents could not be serialized", io);
        }
    }

    /**
     * Deserialize {@code contents} back into a slot-indexed {@code ItemStack[]} of length {@code size}. A slot
     * the payload does not mention stays {@code null} (empty); a slot beyond {@code size} (the size quota
     * shrank since the vault was saved) is dropped rather than overflowing the array.
     */
    public static ItemStack[] decode(VaultContents contents, int size) {
        Objects.requireNonNull(contents, "contents");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
        ItemStack[] slots = new ItemStack[size];
        contents.payload().ifPresent(payload -> readInto(payload, slots));
        return slots;
    }

    /**
     * Deserialize {@code contents} into a {@code ItemStack[]} sized to the stored slot count (the array length
     * written by {@link #encode}), so every saved slot is visible: nothing is truncated. The overflow rescue
     * needs this: when the size quota has shrunk below the stored slot count, the items in the now-out-of-range
     * slots must be seen to be handed back rather than silently dropped by a size-bounded {@link #decode}. An
     * empty payload yields a zero-length array.
     */
    public static ItemStack[] decodeAll(VaultContents contents) {
        Objects.requireNonNull(contents, "contents");
        return contents.payload().map(VaultItemCodec::readAll).orElseGet(() -> new ItemStack[0]);
    }

    private static void writeSlot(DataOutputStream out, int slot, @Nullable ItemStack stack) throws IOException {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        byte[] item = stack.serializeAsBytes();
        out.writeInt(slot);
        out.writeInt(item.length);
        out.write(item);
    }

    private static void readInto(byte[] payload, ItemStack[] slots) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            in.readInt(); // stored array length, informational. The live size governs the target array
            int slot;
            while ((slot = in.readInt()) != -1) {
                byte[] item = PayloadLimits.readItemBytes(in);
                if (slot >= 0 && slot < slots.length) {
                    slots[slot] = ItemStack.deserializeBytes(item);
                }
            }
        } catch (IOException io) {
            throw new UncheckedIOException("vault contents could not be deserialized", io);
        }
    }

    private static ItemStack[] readAll(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            // The array length encode() wrote (the true stored slot count), clamped: a corrupt row must not be
            // able to size an array from a number nothing ever wrote.
            int stored = PayloadLimits.slots(in.readInt());
            ItemStack[] slots = new ItemStack[stored];
            int slot;
            while ((slot = in.readInt()) != -1) {
                byte[] item = PayloadLimits.readItemBytes(in);
                if (slot >= 0 && slot < slots.length) {
                    slots[slot] = ItemStack.deserializeBytes(item);
                }
            }
            return slots;
        } catch (IOException io) {
            throw new UncheckedIOException("vault contents could not be deserialized", io);
        }
    }

    private static boolean isEmpty(@Nullable ItemStack[] contents) {
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
