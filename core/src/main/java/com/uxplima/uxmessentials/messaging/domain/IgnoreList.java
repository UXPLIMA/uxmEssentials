package com.uxplima.uxmessentials.messaging.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The set of players one owner ignores, with the {@link IgnoreScope} of each block, a persistent aggregate
 * loaded per owner. It is the single authority on the ignore-aware delivery rule: {@link #blocks} answers
 * whether traffic of a given kind from a sender is suppressed, and the {@code /msg} resolution and the async
 * chat-mute path both consult it. The list is immutable between operations, {@link #ignore} and
 * {@link #unignore} return a new list rather than mutating in place, so a concurrent reader of one snapshot
 * never sees a half-applied change.
 *
 * <p>Entries are keyed by the ignored player's uuid (the {@link PlayerRef} name is informational), so a
 * second {@code /ignore} of the same player re-scopes the existing entry rather than adding a duplicate,
 * mirroring the {@code (owner, ignored)} primary key the persistent table enforces. Insertion order is
 * preserved for a stable {@code /ignore} listing.
 */
public final class IgnoreList {

    private final PlayerRef owner;
    private final Map<UUID, IgnoreEntry> entries;

    private IgnoreList(PlayerRef owner, Map<UUID, IgnoreEntry> entries) {
        this.owner = owner;
        this.entries = entries;
    }

    /** An empty ignore list for {@code owner}. */
    public static IgnoreList empty(PlayerRef owner) {
        return new IgnoreList(Objects.requireNonNull(owner, "owner"), new LinkedHashMap<>());
    }

    /** Rebuild an owner's list from stored entries, last write of a duplicated key winning. */
    public static IgnoreList of(PlayerRef owner, List<IgnoreEntry> stored) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(stored, "stored");
        Map<UUID, IgnoreEntry> map = new LinkedHashMap<>();
        for (IgnoreEntry entry : stored) {
            map.put(entry.ignored().uuid(), entry);
        }
        return new IgnoreList(owner, map);
    }

    /** The owner this list belongs to. */
    public PlayerRef owner() {
        return owner;
    }

    /** A copy with {@code ignored} added (or re-scoped) under {@code scope}. */
    public IgnoreList ignore(PlayerRef ignored, IgnoreScope scope) {
        Objects.requireNonNull(ignored, "ignored");
        Objects.requireNonNull(scope, "scope");
        Map<UUID, IgnoreEntry> next = new LinkedHashMap<>(entries);
        next.put(ignored.uuid(), new IgnoreEntry(ignored, scope));
        return new IgnoreList(owner, next);
    }

    /** A copy with {@code ignored} removed; unchanged when the owner did not ignore them. */
    public IgnoreList unignore(PlayerRef ignored) {
        Objects.requireNonNull(ignored, "ignored");
        if (!entries.containsKey(ignored.uuid())) {
            return this;
        }
        Map<UUID, IgnoreEntry> next = new LinkedHashMap<>(entries);
        next.remove(ignored.uuid());
        return new IgnoreList(owner, next);
    }

    /** True when the owner has any ignore entry for {@code who}, of any scope. */
    public boolean ignores(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return entries.containsKey(who.uuid());
    }

    /** The scope under which the owner ignores {@code who}, if at all. */
    public Optional<IgnoreScope> scopeFor(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(entries.get(who.uuid())).map(IgnoreEntry::scope);
    }

    /**
     * True when traffic of {@code channel} from {@code sender} is suppressed for this owner, the
     * ignore-aware delivery rule. An owner who does not ignore the sender, or ignores them under a scope
     * that does not cover this channel, is not blocked.
     */
    public boolean blocks(PlayerRef sender, IgnoreChannel channel) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(channel, "channel");
        IgnoreEntry entry = entries.get(sender.uuid());
        if (entry == null) {
            return false;
        }
        return channel.blockedBy(entry.scope());
    }

    /** The entries in insertion order, for the {@code /ignore} listing. */
    public List<IgnoreEntry> entries() {
        return List.copyOf(entries.values());
    }

    /** How many players the owner ignores. */
    public int size() {
        return entries.size();
    }
}
