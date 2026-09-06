package com.uxplima.uxmessentials.kits.domain;

import java.util.Objects;

/**
 * One stack a kit grants, modelled as an opaque platform-neutral payload plus its amount. The {@code data}
 * string is the serialized item form the adapter round-trips through Bukkit's item codec, the domain never
 * parses or inspects it, it only carries it, so the {@code :core} layer stays free of any {@code ItemStack}
 * type while a kit still describes concrete items.
 *
 * <p>An item is a value object: equality is by its serialized data and amount, which is what lets a kit
 * definition compare equal across a config reload. The amount is the quantity to give and must be positive;
 * a zero or negative amount is rejected at construction so a malformed kit entry never reaches a claim.
 *
 * @param data the adapter-owned serialized item payload (the kernel treats it as opaque)
 * @param amount how many of the item the kit grants, always {@code >= 1}
 */
public record KitItem(String data, int amount, java.util.Optional<Integer> slot) {

    public KitItem {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(slot, "slot");
        if (data.isBlank()) {
            throw new IllegalArgumentException("kit item data must not be blank");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("kit item amount must be >= 1: " + amount);
        }
        if (slot.isPresent() && (slot.get() < 0 || slot.get() > 40)) {
            throw new IllegalArgumentException("kit item slot must be between 0 and 40: " + slot.get());
        }
    }

    /** A kit item granting {@code amount} of the stack described by {@code data}. */
    public KitItem(String data, int amount) {
        this(data, amount, java.util.Optional.empty());
    }

    /** A kit item granting {@code amount} of the stack described by {@code data} at a specific slot. */
    public static KitItem of(String data, int amount) {
        return new KitItem(data, amount, java.util.Optional.empty());
    }

    /** A kit item granting {@code amount} of the stack described by {@code data} at a specific slot. */
    public static KitItem of(String data, int amount, java.util.Optional<Integer> slot) {
        return new KitItem(data, amount, slot);
    }
}
