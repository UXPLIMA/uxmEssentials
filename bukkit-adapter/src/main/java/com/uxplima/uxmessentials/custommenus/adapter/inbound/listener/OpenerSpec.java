package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;

import org.jspecify.annotations.Nullable;

/**
 * One operator-configured menu opener: an item that opens the custom menu {@link #menu()} when a player
 * right-clicks it, optionally handed to players on join. Parsed by {@code OpenerLoader} from a single entry of
 * {@code menus/openers.conf} and consumed by the two opener listeners.
 *
 * <p>The {@link Item} descriptor carries the material and the (MiniMessage) name/lore the opener item is built
 * from; {@link #slot()} is the inventory slot the item is placed in on give (a slot outside {@code 0..40}, or one
 * already occupied, falls back to a free slot). {@link #giveOnJoin()} decides whether, and how often, a joining
 * player is handed the item.
 *
 * <p>Immutable and validated at construction: {@code menu} must be non-blank (it is the id the opener item is
 * tagged with and reopens). The loader is responsible for rejecting an unknown material or an unknown menu id
 * before it ever constructs a spec, so a live {@link OpenerSpec} always names a real, buildable item.
 */
public record OpenerSpec(String menu, Item item, int slot, GiveOnJoin giveOnJoin) {

    public OpenerSpec {
        menu = Objects.requireNonNull(menu, "menu").strip();
        if (menu.isBlank()) {
            throw new IllegalArgumentException("menu opener menu id must not be blank");
        }
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(giveOnJoin, "giveOnJoin");
    }

    /**
     * The item descriptor an opener is built from: the {@code material} and the raw MiniMessage {@code name} and
     * {@code lore} the display text is rendered from. The material must be a real, non-air item: the loader parses
     * the config token to a {@link Material} and skips the whole opener when it does not resolve, so this record is
     * only ever handed a valid one. A blank name or empty lore simply leaves that part of the item untouched.
     */
    public record Item(Material material, String name, List<String> lore) {
        public Item {
            Objects.requireNonNull(material, "material");
            if (material.isAir()) {
                throw new IllegalArgumentException("opener item material must not be air");
            }
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }

    /**
     * When a joining player is handed the opener item. {@link #NEVER} is the default. The opener only ever opens
     * from an item a player already holds. {@link #ALWAYS} hands the item out on every join; {@link #FIRST} hands
     * it out only once, tracked by a per-opener PDC flag so a relog does not duplicate it.
     */
    public enum GiveOnJoin {
        NEVER,
        FIRST,
        ALWAYS;

        /**
         * Map a config token to its {@link GiveOnJoin}, or empty when the token is not recognised. Matched
         * case-insensitively with a few natural synonyms; the loader turns an empty result into {@link #NEVER}
         * plus a warning, so this stays a pure lookup.
         */
        public static Optional<GiveOnJoin> parse(@Nullable String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return switch (raw.strip().toLowerCase(Locale.ROOT)) {
                case "never", "no", "off", "none" -> Optional.of(NEVER);
                case "first", "first-join", "firstjoin", "once" -> Optional.of(FIRST);
                case "always", "every", "each", "join" -> Optional.of(ALWAYS);
                default -> Optional.empty();
            };
        }
    }
}
