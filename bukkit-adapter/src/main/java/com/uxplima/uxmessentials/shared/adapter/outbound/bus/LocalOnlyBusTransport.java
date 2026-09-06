package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;

/**
 * The no-op {@link BusTransport} the bus degrades to when the configured transport cannot be wired, most
 * notably when {@code network.transport=redis} is selected but the {@code uxmEssentials-redis} companion jar is
 * not deployed, so its factory class is absent at runtime. It delivers no frames and reports unhealthy, which is
 * exactly the {@link BusTransport} degradation contract: the bus runs local-only with no behavioural change to
 * the single-server path, and {@code /uxmess doctor} shows the transport as down.
 *
 * <p>It never invokes the {@code onFrame} sink (no peer ever sends a frame), {@link #send} discards every frame,
 * and {@link #healthy} is always false so the doctor reflects the missing transport rather than a phantom-live
 * one.
 */
@NullMarked
final class LocalOnlyBusTransport implements BusTransport {

    @Override
    public void start(Consumer<byte[]> onFrame) {
        // Nothing to start: no wire to peers exists, so no inbound frame is ever delivered to onFrame.
    }

    @Override
    public void stop() {
        // Nothing to release.
    }

    @Override
    public void send(byte[] frame) {
        // Fire-and-forget with no path to peers; the frame is discarded, leaving the local path untouched.
    }

    @Override
    public boolean healthy() {
        return false;
    }
}
