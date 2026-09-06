package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.util.Base64;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one codec for a fully-serialized item stored as a click-action payload. A {@code GIVE} action (or an NPC
 * equipment slot) may carry a complete item (every component, enchantment, name, lore) rather than a bare
 * material, encoded as a {@code b64:}-prefixed base64 of {@link ItemStack#serializeAsBytes()}. Both the producer
 * ({@code /hologram action … give hand}, {@code /npc …}) and the consumers (the runner's give-resolver, the
 * command value-check, the NPC equipment ACL) go through here, so the {@code b64:} marker lives in exactly one
 * place and the encode/decode pair can never drift apart.
 */
@NullMarked
public final class SerializedItems {

    /** The stored-token prefix marking a full base64-serialized item, as opposed to a plain material name. */
    public static final String PREFIX = "b64:";

    private SerializedItems() {}

    /** Whether {@code token} is a serialized-item token (vs a material name); a {@code null} token is not. */
    public static boolean isSerialized(@Nullable String token) {
        return token != null && token.startsWith(PREFIX);
    }

    /** Encode {@code item} (with all its NBT) into the {@code b64:<base64>} stored token. */
    public static String encode(ItemStack item) {
        return PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /**
     * Decode a {@code b64:} token back into its item, fail-soft: a token without the prefix, or whose base64/NBT
     * is corrupt, yields {@link Optional#empty()} so the caller skips the give rather than aborting the chain.
     */
    public static Optional<ItemStack> decode(String token) {
        if (!isSerialized(token)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    ItemStack.deserializeBytes(Base64.getDecoder().decode(token.substring(PREFIX.length()))));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
