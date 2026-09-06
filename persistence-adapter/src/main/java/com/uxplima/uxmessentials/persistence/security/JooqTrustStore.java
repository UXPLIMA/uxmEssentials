package com.uxplima.uxmessentials.persistence.security;

import static com.uxplima.uxmessentials.persistence.jooq.tables.SecurityTrusted.SECURITY_TRUSTED;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link TrustStore} over the generated {@code SECURITY_TRUSTED} table. One row per player-and-device
 * carries the epoch-millis instant the trust expires; a device is the one-way {@code ip_hash} the adapter computes
 * from the connecting address, so no raw IP ever reaches the DB. Every statement is typed jOOQ DSL: no SQL is
 * string-concatenated.
 *
 * <p>{@link #isTrusted} answers true only for a row whose {@code until} is still in the future, so an expired trust is
 * simply ignored (it need not be swept eagerly); {@link #trust} upserts on the {@code (uuid, ip_hash)} key, sliding
 * the expiry forward when the same device re-verifies.
 */
public final class JooqTrustStore extends JooqRepository implements TrustStore {

    public JooqTrustStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public boolean isTrusted(UUID playerId, String ipHash, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ipHash, "ipHash");
        Objects.requireNonNull(now, "now");
        return read(dsl -> dsl.fetchExists(dsl.selectOne()
                .from(SECURITY_TRUSTED)
                .where(SECURITY_TRUSTED.UUID.eq(playerId.toString()))
                .and(SECURITY_TRUSTED.IP_HASH.eq(ipHash))
                .and(SECURITY_TRUSTED.UNTIL.gt(now.toEpochMilli()))));
    }

    @Override
    public void trust(UUID playerId, String ipHash, Instant until) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ipHash, "ipHash");
        Objects.requireNonNull(until, "until");
        long expiry = until.toEpochMilli();
        write(dsl -> {
            dsl.insertInto(SECURITY_TRUSTED)
                    .set(SECURITY_TRUSTED.UUID, playerId.toString())
                    .set(SECURITY_TRUSTED.IP_HASH, ipHash)
                    .set(SECURITY_TRUSTED.UNTIL, expiry)
                    .onConflict(SECURITY_TRUSTED.UUID, SECURITY_TRUSTED.IP_HASH)
                    .doUpdate()
                    .set(SECURITY_TRUSTED.UNTIL, expiry)
                    .execute();
            return null;
        });
    }

    @Override
    public void revoke(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        write(dsl -> dsl.deleteFrom(SECURITY_TRUSTED)
                .where(SECURITY_TRUSTED.UUID.eq(playerId.toString()))
                .execute());
    }
}
