package com.uxplima.uxmessentials.poses.application.port;

import java.util.Objects;

/**
 * An opaque token for one spawned seat entity, minted by {@link SeatPort#spawnSeat} and threaded back to
 * {@link SeatPort#mount} and {@link SeatPort#removeSeat}. It is also what a {@link
 * com.uxplima.uxmessentials.poses.domain.PoseSession} stores as its {@code seatHandle}, so ending a pose can
 * remove exactly the seat it started. The domain never interprets the id: only the adapter that minted it does.
 *
 * @param id the adapter's stable identifier for the seat entity
 */
public record SeatHandle(String id) {

    public SeatHandle {
        Objects.requireNonNull(id, "id");
    }

    /** A handle wrapping {@code id}. */
    public static SeatHandle of(String id) {
        return new SeatHandle(id);
    }
}
