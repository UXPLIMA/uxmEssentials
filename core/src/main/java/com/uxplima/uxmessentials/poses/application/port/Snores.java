package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the sleeping-pose snore. The soft, resource-pack-free sound a laying player emits on a loop so
 * a lie-down reads as sleeping to those nearby. It is a per-player toggle, not a one-shot: {@link #startSnoring} adds
 * the player to the snoring set when a {@code /lay} begins (and the {@code snore} config is on), and
 * {@link #stopSnoring} drops them when the pose ends. The adapter owns the actual interval loop and plays the sound
 * on each snorer's own region thread through the scheduler.
 *
 * <p>Only {@code /lay} snores; {@code /bellyflop} and {@code /spin} never call {@link #startSnoring}. As with the
 * other poses ports, {@link #stopSnoring} on a player who was not snoring is a safe no-op, so {@code StopPose} can
 * call it on every exit without inspecting the pose type.
 */
public interface Snores {

    /** Start {@code who} snoring: they are added to the loop that emits the snore sound near them at an interval. */
    void startSnoring(PlayerRef who);

    /** Stop {@code who} snoring; a no-op when they were not. */
    void stopSnoring(PlayerRef who);
}
