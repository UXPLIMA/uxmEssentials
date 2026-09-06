package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.jspecify.annotations.NullMarked;

/**
 * In-memory {@link RequestRegistry}: the in-flight {@code tpa} handshakes, keyed by request id with a
 * per-target pending index. Nothing is persisted. A {@code tpa} request is session state the teleport
 * module drops on {@code stop()} via {@link #clear()}.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code byId} is a {@link ConcurrentHashMap} keyed by request
 * id; the per-target queues it holds are guarded by the registry's own monitor only for the compound
 * store/remove operations that must stay consistent with the index, so a {@code /tpa} and a concurrent
 * {@code /tpaccept} never observe a half-updated pending list.
 */
@NullMarked
public final class InMemoryRequestRegistry implements RequestRegistry {

    private final ConcurrentHashMap<RequestId, TeleportRequest> byId = new ConcurrentHashMap<>();
    private final boolean singleRequestDisplace;
    private final Object lock = new Object();

    public InMemoryRequestRegistry(boolean singleRequestDisplace) {
        this.singleRequestDisplace = singleRequestDisplace;
    }

    @Override
    public void store(TeleportRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (lock) {
            if (singleRequestDisplace) {
                displacePendingFor(request.target());
            }
            byId.put(request.id(), request);
        }
    }

    @Override
    public Optional<TeleportRequest> byId(RequestId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public List<TeleportRequest> pendingFor(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        synchronized (lock) {
            List<TeleportRequest> pending = new ArrayList<>();
            for (TeleportRequest request : byId.values()) {
                if (request.target().equals(target)) {
                    pending.add(request);
                }
            }
            pending.sort(java.util.Comparator.comparing(TeleportRequest::expiresAt));
            return List.copyOf(pending);
        }
    }

    @Override
    public Optional<TeleportRequest> outgoing(PlayerRef requester) {
        Objects.requireNonNull(requester, "requester");
        synchronized (lock) {
            return byId.values().stream()
                    .filter(request -> request.requester().equals(requester))
                    .min(java.util.Comparator.comparing(TeleportRequest::expiresAt));
        }
    }

    @Override
    public void remove(RequestId id) {
        byId.remove(Objects.requireNonNull(id, "id"));
    }

    /** A read-only snapshot of every in-flight request, for the adapter's TTL expiry sweep. */
    public List<TeleportRequest> snapshot() {
        return List.copyOf(byId.values());
    }

    /** Drop every in-flight request on module stop. */
    public void clear() {
        byId.clear();
    }

    private void displacePendingFor(PlayerRef target) {
        byId.values().removeIf(request -> request.target().equals(target));
    }
}
