package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;
import org.junit.jupiter.api.Test;

/**
 * Guards the hard invariant that a player-warp password is only ever stored and handled as a salted digest,
 * never as recoverable plaintext. The rebuild folded the old plaintext {@code password} column into
 * {@code password_algorithm} / {@code password_salt} / {@code password_hash}; this test locks that so a later
 * migration cannot quietly re-introduce a plaintext column, and locks {@link PasswordHash#toString()}'s
 * redaction so a digest cannot leak into a log line (a {@code PasswordHash} rides on the aggregate through
 * every debug/trace path).
 *
 * <p>The schema check reads the authoritative table definition. The {@code CREATE TABLE player_warps} block in
 * the V70 rebuild migration. Rather than scanning every migration, because the pre-rebuild {@code password}
 * column legitimately exists in the historical V17 migration (on the table V70 renames to {@code _v1_legacy}
 * and the data migration then drops); asserting on V70's create is precise and cannot false-positive on that
 * history.
 */
class NoPlaintextWarpPasswordDriftTest {

    private static final String MIGRATION = "/db/migration/V70__player_warps_rebuild.sql";
    private static final String CREATE_MARKER = "CREATE TABLE player_warps (";

    // A column literally named `password` followed by a type, the plaintext shape. `password_algorithm`,
    // `password_salt`, `password_hash` never match because the underscore is not whitespace.
    private static final Pattern BARE_PASSWORD_COLUMN = Pattern.compile("(?im)^\\s*password\\s+[a-z]");

    @Test
    void theRebuiltPlayerWarpsTableStoresNoPlaintextPasswordColumn() {
        String createBlock = playerWarpsCreateBlock();

        assertThat(createBlock)
                .as("the rebuilt player_warps table keeps only the hashed password columns")
                .contains("password_algorithm")
                .contains("password_salt")
                .contains("password_hash");
        assertThat(BARE_PASSWORD_COLUMN.matcher(createBlock).find())
                .as("no plaintext `password` column may be re-introduced on player_warps")
                .isFalse();
    }

    @Test
    void passwordHashToStringRedactsTheSaltAndDigest() {
        String salt = "c2FsdC12YWx1ZS1zZWNyZXQ=";
        String digest = "aGFzaC1kaWdlc3Qtc2VjcmV0LXZhbHVl";

        String rendered = new PasswordHash("PBKDF2WithHmacSHA256", salt, digest).toString();

        assertThat(rendered)
                .as("toString must not leak the salt or the digest")
                .doesNotContain(salt)
                .doesNotContain(digest);
        assertThat(rendered)
                .as("toString still identifies the type and algorithm for a useful log line")
                .contains("PasswordHash")
                .contains("PBKDF2WithHmacSHA256");
    }

    private static String playerWarpsCreateBlock() {
        String sql = readMigration();
        int start = sql.indexOf(CREATE_MARKER);
        assertThat(start).as("V70 must declare CREATE TABLE player_warps").isNotNegative();
        int end = sql.indexOf(");", start);
        assertThat(end).as("the player_warps create must be terminated").isNotNegative();
        return sql.substring(start, end);
    }

    private static String readMigration() {
        try (InputStream in = NoPlaintextWarpPasswordDriftTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("V70 migration must be on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + MIGRATION, e);
        }
    }
}
