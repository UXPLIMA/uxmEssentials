package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the TOTP enrolment pair: {@link BeginTotpEnrollment} hands back a challenge and stores nothing durable, and
 * {@link ConfirmTotpEnrollment} enables the factor only when a code from the pending secret verifies, the
 * "confirm before enabling" property. The confirming code is generated from the same secret the challenge exposes,
 * so the test never hard-codes a secret.
 */
class TotpEnrollmentTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final int WINDOW = 1;

    private final PlayerRef player = new PlayerRef(UUID.randomUUID(), "Steve");

    private FakeTwoFactorRepository repository;
    private PendingTotpEnrollments pending;
    private BeginTotpEnrollment begin;
    private ConfirmTotpEnrollment confirm;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(NOW);
        pending = new PendingTotpEnrollments();
        begin = new BeginTotpEnrollment(new SecretGenerator(), pending, "uxmEssentials");
        confirm = new ConfirmTotpEnrollment(repository, pending, WINDOW);
    }

    @Test
    void setupReturnsAChallengeAndPersistsNothingUntilConfirmed() {
        EnrollmentChallenge challenge = begin.begin(player);

        assertThat(challenge.otpauthUri()).contains("otpauth://totp/uxmEssentials:Steve");
        assertThat(pending.pending(player.uuid())).contains(challenge.secret());
        // Not yet a real factor: setup alone must never enrol.
        assertThat(repository.find(player.uuid())).isEmpty();
    }

    @Test
    void confirmingWithoutASetupIsRejected() {
        assertThat(confirm.confirm(player.uuid(), "000000", NOW)).isEqualTo(TotpConfirmResult.NO_PENDING);
    }

    @Test
    void confirmingWithAWrongCodeDoesNotEnableTheFactor() {
        begin.begin(player);

        assertThat(confirm.confirm(player.uuid(), "000000", NOW)).isEqualTo(TotpConfirmResult.INVALID_CODE);
        assertThat(repository.find(player.uuid())).isEmpty();
        // The pending enrolment survives a wrong attempt so the player can try again.
        assertThat(pending.pending(player.uuid())).isPresent();
    }

    @Test
    void confirmingWithAValidCodeEnablesAndStoresTheSecret() {
        EnrollmentChallenge challenge = begin.begin(player);
        String code = TotpCode.generate(challenge.secret(), NOW);

        assertThat(confirm.confirm(player.uuid(), code, NOW)).isEqualTo(TotpConfirmResult.ENABLED);
        assertThat(repository.find(player.uuid()).orElseThrow().totpSecret()).contains(challenge.secret());
        // The pending enrolment is consumed once confirmed.
        assertThat(pending.pending(player.uuid())).isEmpty();
    }
}
