package com.uxplima.uxmessentials.persistence.security;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Security_2fa.SECURITY_2FA;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PasswordHasher;
import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

/**
 * The jOOQ-backed {@link TwoFactorRepository} over the generated {@code SECURITY_2FA} table. One row per player
 * carries at most one PIN and one TOTP secret; the two enrol independently, so each writer upserts only its own
 * column and never disturbs the other or the first-enrolment stamp.
 *
 * <p>The crypto boundary lives entirely here. The <b>PIN</b> is hashed through the shared {@link PasswordHasher}
 * (the same PBKDF2 primitive the player-warp password uses) and serialised as {@code algorithm:salt:hash} into the
 * single {@code pin_hash} column. The plaintext never reaches the DB and {@link #verifyPin} re-hashes off a value
 * read outside the connection scope, exactly as the warp-password store does. The <b>TOTP secret</b> is encrypted
 * through {@link TotpSecretCipher} on write and decrypted on read, because verification needs the shared secret
 * back. Every statement is typed jOOQ DSL; no SQL is string-concatenated, and no secret is ever logged.
 *
 * <p>{@code last_totp_step} carries the time-step of the last authenticator code the player got in with, so the
 * same code cannot be presented twice. Its write only ever moves the value forward, in one statement, so two
 * verifications racing cannot walk it backwards and reopen a step that was already spent.
 */
public final class JooqTwoFactorRepository extends JooqRepository implements TwoFactorRepository {

    /** Separates the three parts of a serialised {@link PasswordHash}; Base32/Base64 fields never contain it. */
    private static final String PIN_HASH_DELIMITER = ":";

    private final PasswordHasher hasher;
    private final TotpSecretCipher cipher;
    private final Clock clock;

    public JooqTwoFactorRepository(DSLContext dsl, PasswordHasher hasher, TotpSecretCipher cipher, Clock clock) {
        super(dsl);
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<TwoFactorRegistration> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return read(dsl -> dsl.selectFrom(SECURITY_2FA)
                .where(SECURITY_2FA.UUID.eq(playerId.toString()))
                .fetchOptional()
                .map(row -> toRegistration(playerId, row)));
    }

    @Override
    public void enableTotp(UUID playerId, TwoFactorSecret secret) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(secret, "secret");
        String encrypted = cipher.encrypt(secret.value());
        write(dsl -> {
            dsl.insertInto(SECURITY_2FA)
                    .set(SECURITY_2FA.UUID, playerId.toString())
                    .set(SECURITY_2FA.TOTP_SECRET_ENC, encrypted)
                    .set(SECURITY_2FA.ENROLLED_AT, clock.instant().toEpochMilli())
                    .onConflict(SECURITY_2FA.UUID)
                    .doUpdate()
                    // Only the TOTP column: an existing PIN and the first-enrolment stamp are left untouched.
                    .set(SECURITY_2FA.TOTP_SECRET_ENC, encrypted)
                    .execute();
            return null;
        });
    }

    @Override
    public void setPin(UUID playerId, String plaintextPin) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(plaintextPin, "plaintextPin");
        // Hash before touching the DB, so the plaintext stays local to this method and only the digest is written.
        String serialized = serialize(hasher.hash(plaintextPin));
        write(dsl -> {
            dsl.insertInto(SECURITY_2FA)
                    .set(SECURITY_2FA.UUID, playerId.toString())
                    .set(SECURITY_2FA.PIN_HASH, serialized)
                    .set(SECURITY_2FA.ENROLLED_AT, clock.instant().toEpochMilli())
                    .onConflict(SECURITY_2FA.UUID)
                    .doUpdate()
                    // Only the PIN column: an existing TOTP secret and the first-enrolment stamp are left untouched.
                    .set(SECURITY_2FA.PIN_HASH, serialized)
                    .execute();
            return null;
        });
    }

    @Override
    public boolean verifyPin(UUID playerId, String candidate) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(candidate, "candidate");
        // Read the serialised digest under the pooled connection, then verify off it: a constant-time PBKDF2 compare
        // should not hold the connection open. No row or no PIN yields no digest and thus no match.
        Optional<String> stored = read(dsl -> Optional.ofNullable(dsl.select(SECURITY_2FA.PIN_HASH)
                .from(SECURITY_2FA)
                .where(SECURITY_2FA.UUID.eq(playerId.toString()))
                .fetchOne(SECURITY_2FA.PIN_HASH)));
        return stored.map(serialized -> hasher.verify(candidate, deserialize(serialized)))
                .orElse(false);
    }

    @Override
    public void clearTotp(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        clearColumn(playerId, SECURITY_2FA.TOTP_SECRET_ENC);
    }

    @Override
    public void clearPin(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        clearColumn(playerId, SECURITY_2FA.PIN_HASH);
    }

    @Override
    public void recordTotpStep(UUID playerId, long step) {
        Objects.requireNonNull(playerId, "playerId");
        write(dsl -> dsl.update(SECURITY_2FA)
                .set(SECURITY_2FA.LAST_TOTP_STEP, step)
                .where(SECURITY_2FA.UUID.eq(playerId.toString()))
                // Only ever forward: a late write from a slower verification cannot reopen an already-spent step.
                .and(SECURITY_2FA.LAST_TOTP_STEP.isNull().or(SECURITY_2FA.LAST_TOTP_STEP.lt(step)))
                .execute());
    }

    @Override
    public void delete(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        write(dsl -> dsl.deleteFrom(SECURITY_2FA)
                .where(SECURITY_2FA.UUID.eq(playerId.toString()))
                .execute());
    }

    /**
     * Null out one factor column and, in the same transaction, drop the row when that leaves both columns empty. The
     * sweep matters: a row carrying neither factor would still answer {@link #find} with a registration, and the
     * application reads a present registration as "this player is enrolled".
     */
    private void clearColumn(UUID playerId, Field<String> column) {
        String id = playerId.toString();
        write(dsl -> {
            dsl.update(SECURITY_2FA)
                    .setNull(column)
                    .where(SECURITY_2FA.UUID.eq(id))
                    .execute();
            dsl.deleteFrom(SECURITY_2FA)
                    .where(SECURITY_2FA.UUID.eq(id))
                    .and(SECURITY_2FA.PIN_HASH.isNull())
                    .and(SECURITY_2FA.TOTP_SECRET_ENC.isNull())
                    .execute();
            return null;
        });
    }

    /** Rebuild a registration from a queried row, decrypting the TOTP secret and flagging whether a PIN is set. */
    private TwoFactorRegistration toRegistration(UUID playerId, Record row) {
        String encrypted = row.get(SECURITY_2FA.TOTP_SECRET_ENC);
        TwoFactorSecret secret = encrypted == null ? null : new TwoFactorSecret(cipher.decrypt(encrypted));
        boolean pinSet = row.get(SECURITY_2FA.PIN_HASH) != null;
        Instant enrolledAt = Instant.ofEpochMilli(row.get(SECURITY_2FA.ENROLLED_AT));
        Long lastStep = row.get(SECURITY_2FA.LAST_TOTP_STEP);
        return new TwoFactorRegistration(playerId, secret, pinSet, enrolledAt, lastStep == null ? 0L : lastStep);
    }

    private static String serialize(PasswordHash hash) {
        return hash.algorithm() + PIN_HASH_DELIMITER + hash.salt() + PIN_HASH_DELIMITER + hash.hash();
    }

    private static PasswordHash deserialize(String serialized) {
        String[] parts = serialized.split(PIN_HASH_DELIMITER, 3);
        if (parts.length != 3) {
            throw new IllegalStateException("stored pin_hash is not in the expected algorithm:salt:hash form");
        }
        return new PasswordHash(parts[0], parts[1], parts[2]);
    }
}
