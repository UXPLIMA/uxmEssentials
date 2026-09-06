package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins {@link TotpCode} to the RFC 6238 specification. The parameterised cases are the Appendix B reference
 * vectors for the SHA1 variant: the seed is the ASCII "12345678901234567890" (Base32
 * {@code GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ}), and each timestamp maps to the vector's code, truncated to the
 * 6-digit surface every authenticator app shows. Passing these is the proof our maths matches Google
 * Authenticator, Aegis and the rest byte-for-byte. The remaining cases pin the ± window tolerance and that a wrong
 * code is refused.
 */
class TotpCodeTest {

    /** The RFC 6238 Appendix B SHA1 seed, Base32-encoded (ASCII "12345678901234567890"). */
    private static final TwoFactorSecret RFC_SECRET = new TwoFactorSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

    @ParameterizedTest
    @CsvSource({
        "59,287082",
        "1111111109,081804",
        "1111111111,050471",
        "1234567890,005924",
        "2000000000,279037",
        "20000000000,353130"
    })
    void matchesTheRfc6238ReferenceVectors(long epochSeconds, String expectedCode) {
        assertThat(TotpCode.generate(RFC_SECRET, Instant.ofEpochSecond(epochSeconds)))
                .isEqualTo(expectedCode);
    }

    @Test
    void verifiesACodeWithinTheCurrentStep() {
        Instant now = Instant.ofEpochSecond(30);
        String code = TotpCode.generate(RFC_SECRET, now);

        assertThat(TotpCode.verify(RFC_SECRET, code, now, 0)).isTrue();
    }

    @Test
    void acceptsThePreviousStepWithinAOneStepWindow() {
        // Generated in step 1 (epoch 30); presented in step 2 (epoch 60). A ±1 window looks back one step.
        String code = TotpCode.generate(RFC_SECRET, Instant.ofEpochSecond(30));

        assertThat(TotpCode.verify(RFC_SECRET, code, Instant.ofEpochSecond(60), 1))
                .isTrue();
    }

    @Test
    void rejectsThePreviousStepWithNoWindow() {
        String code = TotpCode.generate(RFC_SECRET, Instant.ofEpochSecond(30));

        assertThat(TotpCode.verify(RFC_SECRET, code, Instant.ofEpochSecond(60), 0))
                .isFalse();
    }

    @Test
    void rejectsACodeTwoStepsAwayEvenWithinAOneStepWindow() {
        String code = TotpCode.generate(RFC_SECRET, Instant.ofEpochSecond(30));

        assertThat(TotpCode.verify(RFC_SECRET, code, Instant.ofEpochSecond(120), 1))
                .isFalse();
    }

    @Test
    void rejectsAWrongCode() {
        Instant now = Instant.ofEpochSecond(59);

        assertThat(TotpCode.verify(RFC_SECRET, "000000", now, 1)).isFalse();
    }

    @Test
    void toleratesSpacesInACandidateCode() {
        Instant now = Instant.ofEpochSecond(59);

        assertThat(TotpCode.verify(RFC_SECRET, "287 082", now, 1)).isTrue();
    }
}
