package com.uxplima.uxmessentials.persistence.vaults;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.Vaults;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.VaultsRecord;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code vaults} row and the domain {@link Vault}. The queryable facts
 * owner uuid (canonical 36-char text), the one-based index, the size in rows, and the last-touched epoch
 * millis. Are first-class columns; only the {@code ItemStack[]} contents are opaque payload, stored as the
 * base64 text of the adapter's already-serialized item bytes (the architecture persistence invariant,
 * docs/01-architecture.md). This class is the single place that translation lives, so the row shape stays
 * identical on every backend.
 *
 * <p>A null {@code contents} cell is a freshly allocated, empty vault; it round-trips to
 * {@link VaultContents#empty()} and back to a null cell, so no blob is written for a vault that holds nothing.
 */
final class VaultRows {

    private VaultRows() {}

    /** Rebuild a {@link Vault} from a queried row. */
    static Vault toVault(Record row) {
        Vaults vaults = Vaults.VAULTS;
        VaultId id = new VaultId(UUID.fromString(row.get(vaults.OWNER)), row.get(vaults.IDX));
        VaultSize size = new VaultSize(row.get(vaults.SIZE));
        VaultContents contents = decode(row.get(vaults.CONTENTS));
        Instant lastTouched = Instant.ofEpochMilli(row.get(vaults.LAST_TOUCHED));
        return Vault.of(id, size, contents, row.get(vaults.DISPLAY_NAME), row.get(vaults.ICON), lastTouched);
    }

    /** Populate a {@link VaultsRecord} from a domain {@link Vault} for an upsert. */
    static void apply(VaultsRecord record, Vault vault) {
        record.setOwner(vault.owner().toString())
                .setIdx(vault.index())
                .setSize((short) vault.size().rows())
                .setLastTouched(vault.lastTouched().toEpochMilli())
                .setContents(encode(vault.contents()))
                .setDisplayName(vault.displayName())
                .setIcon(vault.iconMaterial());
    }

    /** Base64 the opaque payload, or {@code null} for an empty vault so no blob is written. */
    static @Nullable String encode(VaultContents contents) {
        return contents.payload().map(Base64.getEncoder()::encodeToString).orElse(null);
    }

    /** Decode a stored base64 cell back into opaque contents; a null or blank cell is an empty vault. */
    static VaultContents decode(@Nullable String cell) {
        if (cell == null || cell.isBlank()) {
            return VaultContents.empty();
        }
        return VaultContents.of(Base64.getDecoder().decode(cell));
    }
}
