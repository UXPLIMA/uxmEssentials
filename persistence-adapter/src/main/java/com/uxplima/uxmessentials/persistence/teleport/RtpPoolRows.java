package com.uxplima.uxmessentials.persistence.teleport;

import java.time.Instant;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.RtpPoolRecord;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;

/**
 * The anti-corruption mapping between an {@code rtp_pool} row and a domain {@link RtpColumn}. A row stores the
 * world uid, the block {@code x}/{@code z}, the nullable biome, and the validation instant as epoch milliseconds;
 * the column value carries them plus the world it was loaded for.
 *
 * <p>The world name is not stored. A pool load is always scoped to a world the caller already holds (it drives the
 * per-world query), so {@link #toColumn} rebuilds the column against that live {@link WorldRef} rather than
 * reconstructing a possibly-stale name from the row. This is the single place the {@code rtp_pool} translation lives.
 */
final class RtpPoolRows {

    private RtpPoolRows() {}

    /** Rebuild an {@link RtpColumn} from a row, using the live {@code world} the load was scoped to. */
    static RtpColumn toColumn(WorldRef world, RtpPoolRecord row) {
        String biome = row.getBiome();
        return new RtpColumn(
                world,
                row.getX(),
                row.getZ(),
                biome == null ? null : BiomeName.of(biome),
                Instant.ofEpochMilli(row.getValidatedAt()));
    }

    /** Populate a fresh {@link RtpPoolRecord} for an insert, stamping the application-assigned {@code id}. */
    static void apply(RtpPoolRecord record, long id, RtpColumn column) {
        record.setId(id)
                .setWorldUuid(column.world().uid().toString())
                .setX(column.x())
                .setZ(column.z())
                .setBiome(column.biome() == null ? null : column.biome().value())
                .setValidatedAt(column.validatedAt().toEpochMilli());
    }
}
