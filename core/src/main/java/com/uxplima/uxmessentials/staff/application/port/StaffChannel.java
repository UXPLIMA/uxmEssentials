package com.uxplima.uxmessentials.staff.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled seam to the messaging context: enumerate the online staff audience and fan a staff-chat
 * message out to them. Staff mode does not own the staff-audience resolution or the broadcast machinery; it
 * orchestrates the existing messaging module's {@code StaffAudience} and broadcaster through this port, the
 * same way {@code HelpOp} fans a help request out to staff.
 *
 * <p>The coupling is soft: when the messaging module is disabled (or has not yet landed) the wiring binds
 * {@link #NONE}, so {@link #onlineStaff()} is empty and {@link #send} is a no-op: staff chat degrades
 * rather than fails: mirroring messaging's own {@code MutePolicy.NEVER}.
 */
public interface StaffChannel {

    /** A no-op channel with no audience: the binding when messaging is disabled. */
    StaffChannel NONE = new StaffChannel() {
        @Override
        public void send(PlayerRef from, List<PlayerRef> recipients, String message) {
            // No messaging module to fan out through; staff chat degrades to silence.
        }

        @Override
        public List<PlayerRef> onlineStaff() {
            return List.of();
        }
    };

    /** Deliver {@code message} from {@code from} to each of {@code recipients} on the staff channel. */
    void send(PlayerRef from, List<PlayerRef> recipients, String message);

    /** Every online player holding the staff-chat node, the staff-chat audience. */
    List<PlayerRef> onlineStaff();
}
