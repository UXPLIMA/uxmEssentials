package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The pure selection contract of {@link DeathCausePolicies}: {@link DeathCausePolicies#policyFor(DeathCause)} hands
 * back the cause's own {@link MessagePolicy} when one is configured, pinning that PVP, FALL, and MOB each pick their
 * distinct template, and falls through to the default for a cause with no override, which is the pre-per-cause
 * single-policy behaviour a file that authors only the default death block still gets.
 */
class DeathCausePoliciesTest {

    private static final MessagePolicy DEFAULT =
            MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("<gray>{player} died"));
    private static final MessagePolicy PVP = MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("{player} was slain"));
    private static final MessagePolicy FALL = MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("{player} fell"));
    private static final MessagePolicy MOB = MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("{player} was mauled"));

    @Test
    void policyForReturnsTheCauseOverrideWhenOneIsConfigured() {
        DeathCausePolicies policies = new DeathCausePolicies(
                Map.of(DeathCause.PVP, PVP, DeathCause.FALL, FALL, DeathCause.MOB, MOB), DEFAULT);

        assertThat(policies.policyFor(DeathCause.PVP)).isEqualTo(PVP);
        assertThat(policies.policyFor(DeathCause.FALL)).isEqualTo(FALL);
        assertThat(policies.policyFor(DeathCause.MOB)).isEqualTo(MOB);
    }

    @Test
    void policyForFallsThroughToTheDefaultForAnUnmappedCause() {
        DeathCausePolicies policies = new DeathCausePolicies(Map.of(DeathCause.PVP, PVP), DEFAULT);

        // A cause with no per-cause entry, and the OTHER catch-all, take the default policy.
        assertThat(policies.policyFor(DeathCause.VOID)).isEqualTo(DEFAULT);
        assertThat(policies.policyFor(DeathCause.OTHER)).isEqualTo(DEFAULT);
    }

    @Test
    void ofDefaultAppliesTheDefaultToEveryCause() {
        DeathCausePolicies policies = DeathCausePolicies.ofDefault(DEFAULT);

        assertThat(policies.byCause()).isEmpty();
        assertThat(policies.policyFor(DeathCause.PVP)).isEqualTo(DEFAULT);
        assertThat(policies.policyFor(DeathCause.FALL)).isEqualTo(DEFAULT);
    }

    @Test
    void theTableCopiesItsInputSoLaterMutationDoesNotLeakIn() {
        var mutable = new java.util.EnumMap<DeathCause, MessagePolicy>(DeathCause.class);
        mutable.put(DeathCause.PVP, PVP);
        DeathCausePolicies policies = new DeathCausePolicies(mutable, DEFAULT);

        mutable.put(DeathCause.FALL, FALL);

        assertThat(policies.policyFor(DeathCause.FALL)).isEqualTo(DEFAULT);
    }
}
