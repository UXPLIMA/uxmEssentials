package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.NullMarked;

/**
 * Awaits a {@link CompletableFuture} with a hard time bound.
 *
 * <p>A foreign provider (a Treasury economy backed by a remote service, for instance) can stall
 * indefinitely, and an unbounded {@code join()} on its future would hold the calling thread for as long
 * as it hangs. This bounds the wait: a stall or a provider failure surfaces as an
 * {@link IllegalStateException} carrying the provider's own cause, so the operation fails loudly rather
 * than freezing the caller or returning a bogus value. The wait is meant to run off the tick thread; the
 * bound is a backstop against an indefinite hang, not a licence to call it on a region thread.
 */
@NullMarked
public final class BoundedAwait {

    private BoundedAwait() {}

    /** Wait up to {@code timeout} for {@code future}, throwing with {@code context} on a stall or failure. */
    public static <T> T get(CompletableFuture<T> future, Duration timeout, String context) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException stalled) {
            future.cancel(true);
            throw new IllegalStateException(context + " did not respond within " + timeout.toSeconds() + "s", stalled);
        } catch (ExecutionException failed) {
            throw new IllegalStateException(context + " failed", failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted awaiting " + context, interrupted);
        }
    }
}
