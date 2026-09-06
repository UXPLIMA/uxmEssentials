package com.uxplima.uxmessentials.vaults.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.event.VaultContentsChanged;
import org.jspecify.annotations.Nullable;

/**
 * The vaults aggregate: one owner's numbered, DB-persisted item container. It carries the queryable facts the
 * {@code vaults} row stores as first-class columns. The owner, the one-based {@link VaultId#index()}, the
 * {@link VaultSize} in rows, an optional player-chosen display name and icon material name, and the
 * {@code lastTouched} instant: plus the opaque {@link VaultContents} the adapter (de)serialises. The aggregate
 * never sees a Bukkit {@code ItemStack}; it holds the already-serialized payload, which is the only part the
 * architecture invariant lets serialize (docs/01-architecture.md). The icon is a plain material <em>name</em>
 * (e.g. {@code "ENDER_CHEST"}); the adapter resolves it to a real {@code Material} at the boundary, so the
 * domain stays Bukkit-free.
 *
 * <p>A vault is the authority for its stored items, not the live GUI: a save resolves the owning vault from the
 * inventory holder, replaces its contents through {@link #withContents}, and writes the result through the
 * repository. {@link #withContents} is a pure transition. It returns a new {@code Vault} and the
 * {@link VaultContentsChanged} event it raised, so the aggregate never reaches for a repository or a clock.
 */
public final class Vault {

    private static final int MAX_NAME_LENGTH = 256;

    private final VaultId id;
    private final VaultSize size;
    private final VaultContents contents;
    private final @Nullable String displayName;
    private final @Nullable String iconMaterial;
    private final Instant lastTouched;

    private Vault(
            VaultId id,
            VaultSize size,
            VaultContents contents,
            @Nullable String displayName,
            @Nullable String iconMaterial,
            Instant lastTouched) {
        this.id = id;
        this.size = size;
        this.contents = contents;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.lastTouched = lastTouched;
    }

    /**
     * Rebuild a vault from its persisted row. {@code displayName} and {@code iconMaterial} are the optional
     * player-chosen presentation columns, {@code null} when the player has not set them, and the icon, when
     * present, is a material <em>name</em> the adapter resolves to a Bukkit {@code Material}.
     */
    public static Vault of(
            VaultId id,
            VaultSize size,
            VaultContents contents,
            @Nullable String displayName,
            @Nullable String iconMaterial,
            Instant lastTouched) {
        return new Vault(
                Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(size, "size"),
                Objects.requireNonNull(contents, "contents"),
                displayName,
                iconMaterial,
                Objects.requireNonNull(lastTouched, "lastTouched"));
    }

    /**
     * Allocate a brand-new, empty vault at {@code id} with the owner's resolved {@code size}. A freshly
     * allocated vault holds no items, so its contents are {@link VaultContents#empty()} and no blob is written
     * until the player stores something and closes it. It carries no display name or icon until the player
     * sets one.
     */
    public static Vault allocate(VaultId id, VaultSize size, Instant at) {
        return of(id, size, VaultContents.empty(), null, null, Objects.requireNonNull(at, "at"));
    }

    /** This vault's identity (owner + index). */
    public VaultId id() {
        return id;
    }

    /** The owner's uuid, for the repository key and the audit line. */
    public java.util.UUID owner() {
        return id.owner();
    }

    /** The one-based index the player addresses this vault by. */
    public int index() {
        return id.index();
    }

    /** The vault's height in rows. */
    public VaultSize size() {
        return size;
    }

    /** The opaque serialized item payload. */
    public VaultContents contents() {
        return contents;
    }

    /** The player-chosen display name, or {@code null} when none has been set. */
    public @Nullable String displayName() {
        return displayName;
    }

    /** The player-chosen icon material name (e.g. {@code "ENDER_CHEST"}), or {@code null} when none has been set. */
    public @Nullable String iconMaterial() {
        return iconMaterial;
    }

    /** When the vault was last saved, surfaced by the admin audit and usable for housekeeping. */
    public Instant lastTouched() {
        return lastTouched;
    }

    /**
     * Open this vault at {@code size}: a re-open after the owner's size quota changed adopts the new height
     * without dropping stored rows (the repository keeps the contents; a wider GUI simply exposes more slots,
     * a narrower one is clamped by the caller before it ever calls this). Returns {@code this} when the size is
     * unchanged so an open does not churn an identical aggregate.
     */
    public Vault openedAt(VaultSize newSize) {
        Objects.requireNonNull(newSize, "newSize");
        return newSize.equals(size) ? this : new Vault(id, newSize, contents, displayName, iconMaterial, lastTouched);
    }

    /**
     * Set or clear this vault's display name, preserving its size, contents, icon and last-touched. A
     * {@code null} name clears it; a non-null name must satisfy the domain sanity cap of {@value #MAX_NAME_LENGTH}
     * characters: anything longer is a programming error and throws. The configurable, friendlier clamp the
     * player sees is the adapter's job; this guard only stops an absurd name from ever reaching the row.
     */
    public Vault renamedTo(@Nullable String name) {
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("vault name exceeds " + MAX_NAME_LENGTH + " chars: " + name.length());
        }
        return new Vault(id, size, contents, name, iconMaterial, lastTouched);
    }

    /**
     * Set or clear this vault's icon, preserving its size, contents, display name and last-touched. The icon is
     * a material <em>name</em> the adapter resolves to a real {@code Material}; a {@code null} name clears it.
     */
    public Vault iconSet(@Nullable String materialName) {
        return new Vault(id, size, contents, displayName, materialName, lastTouched);
    }

    /**
     * Replace the stored items with {@code newContents} as of {@code at} (an {@code InventoryClose} save).
     * Returns the new vault and the {@link VaultContentsChanged} event the application layer publishes after a
     * successful write.
     */
    public Change withContents(PlayerRef owner, VaultContents newContents, Instant at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(newContents, "newContents");
        Objects.requireNonNull(at, "at");
        Vault saved = new Vault(id, size, newContents, displayName, iconMaterial, at);
        return new Change(saved, new VaultContentsChanged(owner, index(), at));
    }

    /**
     * The outcome of a contents save: the resulting vault and the event to publish.
     *
     * @param result the vault after the save
     * @param event the contents-changed event to publish
     */
    public record Change(Vault result, VaultContentsChanged event) {

        public Change {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(event, "event");
        }
    }
}
