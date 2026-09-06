package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.uxmlib.item.Pdc;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The per-villager state the trade features persist on the villager entity itself: the instant it last restocked its
 * trades (the restock timer's clock) and whether trading is disabled for this individual villager (the Phase-2 trade
 * manager's hook). Both are the sanctioned use for transient per-holder state. Stamped in the villager's PDC under a
 * single pre-created key each, so they survive a chunk reload and a server restart but never touch the database.
 *
 * <p>The last-restock is stored as epoch milliseconds ({@link PersistentDataType#LONG}); an absent stamp reads as
 * {@link Instant#EPOCH}, which the {@link com.uxplima.uxmessentials.villagers.domain.RestockPolicy} treats as always
 * due, so a freshly-seen villager restocks on the first sweep. The disable flag and the Phase-3 protection ("saver")
 * flag both ride the shared {@code (byte)} boolean convention through {@link PdcFlag}. The Phase-4 follow-owner flag
 * stores the owner's UUID as a {@link PersistentDataType#STRING}, so the pairing survives a chunk reload and can be
 * inspected; an absent or unparseable value reads as "not following".
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Reads and writes go through the live {@link Villager} on its own region thread
 * (the restock sweep's per-region hop, or the interact-event thread). Every {@link NamespacedKey} is built once as a
 * constant through the same {@code key} helper the rest of the context uses, never on a hot path.
 */
@NullMarked
public final class PdcVillagerFlags {

    private static final NamespacedKey RESTOCK_AT = key("villagers_restock");
    private static final NamespacedKey DISABLED = key("villagers_disabled");
    private static final NamespacedKey PROTECTED = key("villagers_protected");
    private static final NamespacedKey FOLLOW_OWNER = key("villagers_follow");

    /** The instant {@code villager} last restocked, or {@link Instant#EPOCH} when it never has. */
    public Instant lastRestock(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        long millis = Pdc.getOrDefault(villager.getPersistentDataContainer(), RESTOCK_AT, PersistentDataType.LONG, 0L);
        return Instant.ofEpochMilli(millis);
    }

    /** Record {@code when} as {@code villager}'s most recent restock. */
    public void stampRestock(Villager villager, Instant when) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(when, "when");
        Pdc.set(villager.getPersistentDataContainer(), RESTOCK_AT, PersistentDataType.LONG, when.toEpochMilli());
    }

    /** Whether trading is disabled for this individual {@code villager} (an absent flag reads as enabled). */
    public boolean tradesDisabled(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return PdcFlag.getOrDefault(villager.getPersistentDataContainer(), DISABLED, false);
    }

    /** Set whether trading is disabled for this individual {@code villager}. */
    public void setTradesDisabled(Villager villager, boolean disabled) {
        Objects.requireNonNull(villager, "villager");
        PdcFlag.set(villager.getPersistentDataContainer(), DISABLED, disabled);
    }

    /** Whether this individual {@code villager} carries the protection ("saver") mark (an absent flag reads false). */
    public boolean isProtected(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return PdcFlag.getOrDefault(villager.getPersistentDataContainer(), PROTECTED, false);
    }

    /** Set whether this individual {@code villager} is protected from death and despawn. */
    public void setProtected(Villager villager, boolean protectedMark) {
        Objects.requireNonNull(villager, "villager");
        PdcFlag.set(villager.getPersistentDataContainer(), PROTECTED, protectedMark);
    }

    /** The UUID of the player {@code villager} is following, or {@code null} when it is not following anyone. */
    public @Nullable UUID followOwner(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return Pdc.get(villager.getPersistentDataContainer(), FOLLOW_OWNER, PersistentDataType.STRING)
                .map(PdcVillagerFlags::parseOwner)
                .orElse(null);
    }

    /** Record that {@code villager} is now following {@code owner}. */
    public void setFollowOwner(Villager villager, UUID owner) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(owner, "owner");
        Pdc.set(villager.getPersistentDataContainer(), FOLLOW_OWNER, PersistentDataType.STRING, owner.toString());
    }

    /** Clear {@code villager}'s follow-owner mark so it stops following. */
    public void clearFollowOwner(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        villager.getPersistentDataContainer().remove(FOLLOW_OWNER);
    }

    // A hand-edited or corrupted owner value is treated as "not following" rather than propagating the parse error.
    private static @Nullable UUID parseOwner(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("uxmessentials:" + value), value + " key");
    }
}
