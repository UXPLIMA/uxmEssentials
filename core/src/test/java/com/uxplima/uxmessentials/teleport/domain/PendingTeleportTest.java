package com.uxplima.uxmessentials.teleport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport.Outcome;
import org.junit.jupiter.api.Test;

/**
 * The pure move-cancels-warmup decision the teleport context owns: leaving the origin block cancels a
 * move-armed warmup, sub-block jitter never does, the per-axis toggles gate rotation/damage/interact
 * cancels, a configured move threshold tolerates a small drift across a block edge, and the cancelled flag
 * is one-way so the cancel-vs-fire race resolves to a single loser.
 */
class PendingTeleportTest {

    private static final PlayerRef MOVER = new PlayerRef(UUID.randomUUID(), "Mover");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position ORIGIN = Position.of(WORLD, 10.5, 64.0, 10.5);

    private static WarmupCancelToggles toggles(
            boolean move, boolean rotate, boolean damage, boolean interact, double threshold) {
        return new WarmupCancelToggles(move, rotate, damage, interact, threshold);
    }

    private static PendingTeleport armed(WarmupCancelToggles toggles) {
        return PendingTeleport.arm(MOVER, TeleportKind.REQUEST, ORIGIN, Destination.at(ORIGIN), toggles);
    }

    @Test
    void leavingTheOriginBlockCancelsAMoveArmedWarmup() {
        PendingTeleport warmup = armed(WarmupCancelToggles.defaults());

        Outcome outcome = warmup.onMovement(Position.of(WORLD, 12.0, 64.0, 10.5));

        assertThat(outcome.didCancel()).isTrue();
        assertThat(outcome.cancel()).contains(WarmupCancelReason.MOVED);
        assertThat(outcome.state().isCancelled()).isTrue();
    }

    @Test
    void subBlockJitterDoesNotCancel() {
        PendingTeleport warmup = armed(WarmupCancelToggles.defaults());

        Outcome outcome = warmup.onMovement(Position.of(WORLD, 10.9, 64.0, 10.1));

        assertThat(outcome.didCancel()).isFalse();
        assertThat(outcome.state().isCancelled()).isFalse();
    }

    @Test
    void moveCancelOffMeansMovingDoesNotCancel() {
        PendingTeleport warmup = armed(toggles(false, false, false, false, 0.0));

        Outcome outcome = warmup.onMovement(Position.of(WORLD, 50.0, 64.0, 50.0));

        assertThat(outcome.didCancel()).isFalse();
    }

    @Test
    void damageCancelsOnlyWhenTheToggleArmsIt() {
        assertThat(armed(WarmupCancelToggles.defaults())
                        .onAction(WarmupCancelReason.DAMAGED)
                        .didCancel())
                .isFalse();
        assertThat(armed(toggles(true, false, true, false, 0.0))
                        .onAction(WarmupCancelReason.DAMAGED)
                        .didCancel())
                .isTrue();
    }

    @Test
    void interactCancelsOnlyWhenTheToggleArmsIt() {
        assertThat(armed(WarmupCancelToggles.defaults())
                        .onAction(WarmupCancelReason.INTERACTED)
                        .didCancel())
                .isFalse();
        assertThat(armed(toggles(true, false, false, true, 0.0))
                        .onAction(WarmupCancelReason.INTERACTED)
                        .didCancel())
                .isTrue();
    }

    @Test
    void rotationCancelsOnlyWhenTheToggleArmsIt() {
        assertThat(armed(toggles(true, true, false, false, 0.0))
                        .onAction(WarmupCancelReason.ROTATED)
                        .didCancel())
                .isTrue();
    }

    @Test
    void aDriftWithinTheMoveThresholdDoesNotCancel() {
        PendingTeleport warmup = armed(toggles(true, false, false, false, 3.0));

        // crosses the origin block edge but stays within the 3-block threshold
        Outcome outcome = warmup.onMovement(Position.of(WORLD, 12.5, 64.0, 10.5));

        assertThat(outcome.didCancel()).isFalse();
    }

    @Test
    void aDriftBeyondTheMoveThresholdCancels() {
        PendingTeleport warmup = armed(toggles(true, false, false, false, 3.0));

        Outcome outcome = warmup.onMovement(Position.of(WORLD, 20.5, 64.0, 10.5));

        assertThat(outcome.didCancel()).isTrue();
        assertThat(outcome.cancel()).contains(WarmupCancelReason.MOVED);
    }

    @Test
    void anExplicitAbortAlwaysCancels() {
        Outcome outcome = armed(toggles(false, false, false, false, 0.0)).onAction(WarmupCancelReason.ABORTED);

        assertThat(outcome.didCancel()).isTrue();
    }

    @Test
    void cancellationIsOneWay() {
        PendingTeleport cancelled = armed(WarmupCancelToggles.defaults())
                .onMovement(Position.of(WORLD, 99.0, 64.0, 99.0))
                .state();

        Outcome second = cancelled.onMovement(Position.of(WORLD, 100.0, 64.0, 100.0));

        assertThat(second.didCancel()).isFalse(); // already cancelled. No second cancel fires
        assertThat(second.state().isCancelled()).isTrue();
    }
}
