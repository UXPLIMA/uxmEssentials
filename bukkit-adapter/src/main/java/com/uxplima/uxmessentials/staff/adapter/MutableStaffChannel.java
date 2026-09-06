package com.uxplima.uxmessentials.staff.adapter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import org.jspecify.annotations.NullMarked;

/**
 * A rebindable {@link StaffChannel} holder: it forwards to {@link StaffChannel#NONE} until the messaging-backed
 * channel is bound in, mirroring the messaging context's {@code MutableMutePolicy}. When the messaging module
 * is disabled the binding never happens and staff chat stays {@code NONE}, an empty audience and a no-op send
 *, so {@code /staffchat} degrades rather than fails.
 */
@NullMarked
public final class MutableStaffChannel implements StaffChannel {

    private final AtomicReference<StaffChannel> delegate = new AtomicReference<>(StaffChannel.NONE);

    @Override
    public void send(PlayerRef from, List<PlayerRef> recipients, String message) {
        Objects.requireNonNull(delegate.get(), "delegate").send(from, recipients, message);
    }

    @Override
    public List<PlayerRef> onlineStaff() {
        return Objects.requireNonNull(delegate.get(), "delegate").onlineStaff();
    }

    /** Rebind to the real messaging-provided channel; called once when the messaging context is enabled. */
    public void bind(StaffChannel real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
