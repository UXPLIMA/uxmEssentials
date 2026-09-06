package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PasswordHasher;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PlayerWarpPasswordStore} over the {@code password_algorithm/salt/hash} columns of the V70
 * {@code player_warps} table. It is the only writer of those columns, {@link JooqPlayerWarpRepository} never
 * touches them, so a warp save can never wipe a set password. The plaintext is hashed here through the injected
 * {@link PasswordHasher} and only the resulting {@link PasswordHash} is ever written; the plaintext never leaves a
 * store method and is never logged. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqPlayerWarpPasswordStore extends JooqRepository implements PlayerWarpPasswordStore {

    private final PasswordHasher hasher;

    public JooqPlayerWarpPasswordStore(DSLContext dsl, PasswordHasher hasher) {
        super(dsl);
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    @Override
    public void set(PlayerWarpId warp, String plaintext) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(plaintext, "plaintext");
        // Hash before touching the DB, so the plaintext stays local to this method and only the digest is written.
        PasswordHash hash = hasher.hash(plaintext);
        write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.PASSWORD_ALGORITHM, hash.algorithm())
                .set(PLAYER_WARPS.PASSWORD_SALT, hash.salt())
                .set(PLAYER_WARPS.PASSWORD_HASH, hash.hash())
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .execute());
    }

    @Override
    public void clear(PlayerWarpId warp) {
        Objects.requireNonNull(warp, "warp");
        write(dsl -> dsl.update(PLAYER_WARPS)
                .setNull(PLAYER_WARPS.PASSWORD_ALGORITHM)
                .setNull(PLAYER_WARPS.PASSWORD_SALT)
                .setNull(PLAYER_WARPS.PASSWORD_HASH)
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .execute());
    }

    @Override
    public boolean matches(PlayerWarpId warp, String plaintext) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(plaintext, "plaintext");
        // Read the digest under the pooled connection, then verify off it: a constant-time PBKDF2 compare should
        // not hold the connection open. An absent row or an unset password yields no digest and thus no match.
        Optional<PasswordHash> stored = read(dsl -> dsl.select(
                        PLAYER_WARPS.PASSWORD_ALGORITHM, PLAYER_WARPS.PASSWORD_SALT, PLAYER_WARPS.PASSWORD_HASH)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .fetchOptional()
                .flatMap(JooqPlayerWarpPasswordStore::toPasswordHash));
        return stored.map(hash -> hasher.verify(plaintext, hash)).orElse(false);
    }

    private static Optional<PasswordHash> toPasswordHash(Record row) {
        String algorithm = row.get(PLAYER_WARPS.PASSWORD_ALGORITHM);
        // A NULL algorithm marks a warp with no password set; the salt and hash are written and cleared with it.
        if (algorithm == null) {
            return Optional.empty();
        }
        return Optional.of(
                new PasswordHash(algorithm, row.get(PLAYER_WARPS.PASSWORD_SALT), row.get(PLAYER_WARPS.PASSWORD_HASH)));
    }
}
