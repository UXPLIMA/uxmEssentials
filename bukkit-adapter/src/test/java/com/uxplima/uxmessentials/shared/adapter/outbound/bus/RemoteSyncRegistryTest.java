package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage of {@link RemoteSyncRegistry#dispatch}: every registered listener is invoked for an inbound
 * frame, and, the property the registry exists to guarantee, a listener that throws is reported to the
 * failure sink without starving the listeners registered after it. This is the one seam every context's
 * cross-server sync rides through, so without that isolation a single context's broken cache-invalidation would
 * swallow every other context's sync on the same frame.
 */
class RemoteSyncRegistryTest {

    private static final NetworkMessage FRAME =
            new HomeChanged("lobby-2", UUID.fromString("00000000-0000-0000-0000-0000000000aa"));

    @Test
    void dispatchInvokesEveryListenerAndReportsNoFailureWhenTheyAllSucceed() {
        RemoteSyncRegistry registry = new RemoteSyncRegistry();
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        registry.register(first);
        registry.register(second);

        List<Failure> failures = new ArrayList<>();
        registry.dispatch(FRAME, capture(failures));

        assertThat(first.applied).containsExactly(FRAME);
        assertThat(second.applied).containsExactly(FRAME);
        assertThat(failures).isEmpty();
    }

    @Test
    void aThrowingListenerIsReportedButTheOthersStillRun() {
        RemoteSyncRegistry registry = new RemoteSyncRegistry();
        RuntimeException boom = new IllegalStateException("listener blew up");
        RecordingListener before = new RecordingListener();
        RecordingListener after = new RecordingListener();
        registry.register(before);
        registry.register(message -> {
            throw boom;
        });
        registry.register(after);

        List<Failure> failures = new ArrayList<>();
        registry.dispatch(FRAME, capture(failures));

        // The listener before the throw ran, and the one after it still ran: the throw did not break the loop.
        assertThat(before.applied).containsExactly(FRAME);
        assertThat(after.applied).containsExactly(FRAME);
        // The failure surfaced exactly once, carrying the offending frame and the original exception.
        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.message()).isEqualTo(FRAME);
            assertThat(failure.cause()).isSameAs(boom);
        });
    }

    private static RemoteSyncRegistry.FailureSink capture(List<Failure> sink) {
        return (message, failure) -> sink.add(new Failure(message, failure));
    }

    /** One reported listener failure, so the test can assert which frame failed and with what exception. */
    private record Failure(NetworkMessage message, RuntimeException cause) {}

    /** Records every frame the registry delivered: the stand-in for a context's cache-invalidation listener. */
    private static final class RecordingListener implements RemoteSyncListener {

        private final List<NetworkMessage> applied = new ArrayList<>();

        @Override
        public void onRemoteChange(NetworkMessage message) {
            applied.add(message);
        }
    }
}
