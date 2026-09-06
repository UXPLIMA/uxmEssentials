package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.jspecify.annotations.Nullable;

/**
 * An in-memory {@link TwoFactorRepository} for the use-case tests. It mirrors the real jOOQ store's contract, one
 * row per player, PIN and TOTP enrol independently, verify re-checks the stored value, but keeps the PIN as
 * plaintext (the round-trip through the real hasher is proven separately in the persistence-adapter test), so the
 * use-case tests can assert the orchestration without a database or crypto.
 */
final class FakeTwoFactorRepository implements TwoFactorRepository {

    private record Row(
            @Nullable TwoFactorSecret secret, @Nullable String pin, Instant enrolledAt, long lastTotpStep) {}

    private final Map<UUID, Row> rows = new HashMap<>();
    private final Instant enrolledAt;

    FakeTwoFactorRepository(Instant enrolledAt) {
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "enrolledAt");
    }

    @Override
    public Optional<TwoFactorRegistration> find(UUID playerId) {
        Row row = rows.get(playerId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new TwoFactorRegistration(
                playerId, row.secret(), row.pin() != null, row.enrolledAt(), row.lastTotpStep()));
    }

    @Override
    public void enableTotp(UUID playerId, TwoFactorSecret secret) {
        Row existing = rows.get(playerId);
        rows.put(playerId, new Row(secret, existing == null ? null : existing.pin(), stamp(existing), step(existing)));
    }

    @Override
    public void setPin(UUID playerId, String plaintextPin) {
        Row existing = rows.get(playerId);
        rows.put(
                playerId,
                new Row(existing == null ? null : existing.secret(), plaintextPin, stamp(existing), step(existing)));
    }

    @Override
    public boolean verifyPin(UUID playerId, String candidate) {
        Row row = rows.get(playerId);
        return row != null && row.pin() != null && row.pin().equals(candidate);
    }

    @Override
    public void clearTotp(UUID playerId) {
        clear(playerId, row -> new Row(null, row.pin(), row.enrolledAt(), row.lastTotpStep()));
    }

    @Override
    public void clearPin(UUID playerId) {
        clear(playerId, row -> new Row(row.secret(), null, row.enrolledAt(), row.lastTotpStep()));
    }

    @Override
    public void recordTotpStep(UUID playerId, long step) {
        Row row = rows.get(playerId);
        if (row != null && step > row.lastTotpStep()) {
            rows.put(playerId, new Row(row.secret(), row.pin(), row.enrolledAt(), step));
        }
    }

    @Override
    public void delete(UUID playerId) {
        rows.remove(playerId);
    }

    /** Apply {@code without} to the stored row, dropping it entirely once neither factor is left, as the store does. */
    private void clear(UUID playerId, java.util.function.UnaryOperator<Row> without) {
        Row row = rows.get(playerId);
        if (row == null) {
            return;
        }
        Row stripped = without.apply(row);
        if (stripped.secret() == null && stripped.pin() == null) {
            rows.remove(playerId);
        } else {
            rows.put(playerId, stripped);
        }
    }

    private Instant stamp(@Nullable Row existing) {
        return existing == null ? enrolledAt : existing.enrolledAt();
    }

    private static long step(@Nullable Row existing) {
        return existing == null ? 0L : existing.lastTotpStep();
    }
}
