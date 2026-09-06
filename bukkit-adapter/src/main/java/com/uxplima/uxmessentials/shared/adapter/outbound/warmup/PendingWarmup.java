package com.uxplima.uxmessentials.shared.adapter.outbound.warmup;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One in-flight warmup's handle. The owning context calls {@link #cancel()} from its move/damage
 * listener; the countdown calls {@link #complete()} on the player's region thread when it elapses.
 * Both transitions are guarded by a single {@link AtomicReference} state machine so the move-cancel
 * and the warmup-fire can never both run their callback, whichever {@code compareAndSet}s first wins,
 * and the loser is a no-op.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code state} is an {@link AtomicReference} mutated only by
 * {@code compareAndSet}; reads ({@link #isComplete()}, {@link #isCancelled()}) are safe from any
 * thread.
 */
@NullMarked
final class PendingWarmup implements WarmupHandle {

    private enum State {
        PENDING,
        COMPLETED,
        CANCELLED
    }

    private final Runnable onComplete;
    private final Runnable onCancel;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

    PendingWarmup(PlayerRef who, Runnable onComplete, Runnable onCancel) {
        Objects.requireNonNull(who, "who");
        this.onComplete = Objects.requireNonNull(onComplete, "onComplete");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
    }

    /** Run the completion callback exactly once if the warmup has not been cancelled. */
    void complete() {
        if (state.compareAndSet(State.PENDING, State.COMPLETED)) {
            onComplete.run();
        }
    }

    @Override
    public void cancel() {
        if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
            onCancel.run();
        }
    }

    @Override
    public boolean isComplete() {
        return state.get() == State.COMPLETED;
    }

    @Override
    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }
}
