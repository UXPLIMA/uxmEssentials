package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code teleport_*} placeholders. It is an adapter over the
 * teleport context's read sides. The shared {@code Cooldowns} gate, the in-flight warmup tracker, the
 * {@code /back} location store, the {@code tpa} request registry and the {@code /tptoggle} flags, wired
 * during bootstrap. When the teleport module is disabled the seam is absent and every placeholder degrades
 * to the {@code "-"} default.
 *
 * <p>Every read is the same one the matching command consults, so a placeholder agrees with what the player
 * actually experiences: {@link #cooldownRemaining} is the shared {@code tp} cooldown {@code /home}/{@code
 * /warp}/{@code /rtp} gate against, {@link #backLocation} is what {@code /back} returns to, and {@link
 * #incomingRequests}/{@link #hasOutgoingRequest} mirror {@code /tpalist} and {@code /tpcancel}.
 */
public interface TeleportPlaceholders {

    /** The remaining wait on {@code who}'s shared teleport cooldown, or empty when it is ready. */
    Optional<Duration> cooldownRemaining(PlayerRef who);

    /** The remaining time on {@code who}'s in-flight warmup, or empty when none is running. */
    Optional<Duration> warmupRemaining(PlayerRef who);

    /** {@code who}'s current {@code /back} return point, or empty when none has been captured. */
    Optional<BackView> backLocation(PlayerRef who);

    /** The number of {@code tpa} requests currently pending {@code who}'s reply. */
    int incomingRequests(PlayerRef who);

    /** True when {@code who} has an outgoing {@code tpa} request still pending a reply. */
    boolean hasOutgoingRequest(PlayerRef who);

    /** True when {@code who} is accepting incoming {@code tpa} requests ({@code /tptoggle} on). */
    boolean acceptingRequests(PlayerRef who);

    /**
     * A flattened view of a {@code /back} return point, the world name and block coordinates, so the
     * resolver renders the placeholders without importing a teleport domain type.
     *
     * @param world the world the return point lives in
     * @param blockX the block x coordinate
     * @param blockY the block y coordinate
     * @param blockZ the block z coordinate
     */
    record BackView(String world, int blockX, int blockY, int blockZ) {}
}
