package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the client-visible rendering of a free pose, the lie-down body pose behind {@code /lay},
 * {@code /bellyflop}, and the yaw rotation behind {@code /spin}. It sits alongside
 * {@link SeatPort}: the seat entity holds the player still, this port makes them <em>look</em> posed. The seat and
 * the render are separate concerns so the one-session invariant, the anchor, and the cleanup all stay in one place
 * while the visual is swapped independently. Crawl is the one free pose with no seat. It is held by a client-only
 * hold through the {@code CrawlView}, which owns its pose as well, so a crawl never reaches this port.
 *
 * <p>{@link #applyPose} is called once when a pose begins, after the player is anchored on their seat, and
 * {@link #clearPose} once when it ends (through {@code StopPose}, on every exit, command, sneak, damage, teleport,
 * quit). A player who was never given a free pose, a plain block-sit or a player-sit, is a safe no-op on
 * {@code clearPose}, so {@code StopPose} may call it unconditionally without inspecting the pose type.
 *
 * <p>The adapter runs every packet and every rotation through the Folia-aware scheduler so the work stays on the
 * owning region thread; the port itself is fire-and-forget, mirroring the {@link SeatPort} contract.
 */
public interface PosePort {

    /**
     * Render {@code who} in {@code pose}: a {@link PoseType#LAY} or {@link PoseType#BELLYFLOP} lie-down, or a
     * {@link PoseType#SPIN} rotation. Only the free poses are valid here; the seat/player sits are
     * rendered by the client already and never reach this port. Called after the player is mounted on their anchor
     * seat.
     */
    void applyPose(PlayerRef who, PoseType pose);

    /**
     * Undo whatever {@link #applyPose} did for {@code who}, reset the lie-down body pose and stop any running spin.
     * A no-op when the player holds no free pose (a plain sit never applied one), so {@code StopPose} can call it on
     * every exit without branching on the pose type.
     */
    void clearPose(PlayerRef who);
}
