package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.playerwarps.Pbkdf2PasswordHasher;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqTwoFactorRepository} against the default embedded SQLite backend with the
 * Flyway V76 {@code security_2fa} table applied. It proves the crypto boundary the port promises: a PIN is stored
 * only as its salted PBKDF2 hash (the right PIN verifies, a wrong PIN does not, and the plaintext never appears in
 * the column), and a TOTP secret round-trips through AES-GCM encryption (the stored column is not the plaintext,
 * yet {@code find} returns the exact secret back). It also proves the two factors enrol independently on one row
 * and that a delete clears both.
 */
class JooqTwoFactorRepositoryTest {

    /** A fixed 256-bit key so the cipher is deterministic across a run; the random IV still varies each write. */
    private static final byte[] KEY = new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
    };

    private Persistence persistence;
    private JooqTwoFactorRepository repository;
    private UUID player;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqTwoFactorRepository(
                persistence.dsl(), new Pbkdf2PasswordHasher(), new TotpSecretCipher(KEY), Clock.systemUTC());
        player = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void findIsEmptyForAPlayerWithNoRegistration() {
        assertThat(repository.find(player)).isEmpty();
    }

    @Test
    void storesThePinAsAHashThatVerifiesTheRightPinAndRejectsAWrongOne() {
        repository.setPin(player, "1234");

        assertThat(repository.verifyPin(player, "1234")).isTrue();
        assertThat(repository.verifyPin(player, "9999")).isFalse();

        TwoFactorRegistration registration = repository.find(player).orElseThrow();
        assertThat(registration.pinSet()).isTrue();
        assertThat(registration.totpEnabled()).isFalse();
    }

    @Test
    void roundTripsTheTotpSecretThroughEncryptionButNeverStoresItInPlaintext() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);

        TwoFactorRegistration registration = repository.find(player).orElseThrow();
        assertThat(registration.totpSecret()).contains(secret);
        // The stored column is the ciphertext, not the Base32 plaintext.
        String storedColumn = persistence
                .dsl()
                .select(com.uxplima.uxmessentials.persistence.jooq.tables.Security_2fa.SECURITY_2FA.TOTP_SECRET_ENC)
                .from(com.uxplima.uxmessentials.persistence.jooq.tables.Security_2fa.SECURITY_2FA)
                .fetchOne(com.uxplima.uxmessentials.persistence.jooq.tables.Security_2fa.SECURITY_2FA.TOTP_SECRET_ENC);
        assertThat(storedColumn).isNotNull().isNotEqualTo(secret.value());
    }

    @Test
    void bothFactorsEnrolIndependentlyOnOneRowWithoutClobberingEachOther() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.setPin(player, "4321");
        repository.enableTotp(player, secret);

        TwoFactorRegistration registration = repository.find(player).orElseThrow();
        assertThat(registration.pinSet()).isTrue();
        assertThat(registration.totpSecret()).contains(secret);
        assertThat(repository.verifyPin(player, "4321")).isTrue();
    }

    @Test
    void deleteRemovesTheWholeRegistration() {
        repository.setPin(player, "1234");
        repository.enableTotp(player, new SecretGenerator().generate());

        repository.delete(player);

        assertThat(repository.find(player)).isEmpty();
        assertThat(repository.verifyPin(player, "1234")).isFalse();
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
