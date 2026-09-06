package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link BoundedAwait}: it returns a ready value, rethrows a provider failure as an unchecked
 * {@link IllegalStateException} carrying the original cause, and, the point of the class, gives up
 * promptly on a future that never completes instead of blocking the caller forever.
 */
class BoundedAwaitTest {

    @Test
    void returnsTheValueOfAnAlreadyCompletedFuture() {
        String value = BoundedAwait.get(CompletableFuture.completedFuture("ok"), Duration.ofSeconds(1), "provider");

        assertThat(value).isEqualTo("ok");
    }

    @Test
    void rethrowsAProviderFailureAsIllegalStateWithTheCausePreserved() {
        IllegalArgumentException cause = new IllegalArgumentException("boom");
        CompletableFuture<String> failed = CompletableFuture.failedFuture(cause);

        assertThatThrownBy(() -> BoundedAwait.get(failed, Duration.ofSeconds(1), "provider"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider failed")
                .hasCause(cause);
    }

    @Test
    void timesOutPromptlyWhenTheFutureNeverCompletes() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        long startNanos = System.nanoTime();

        assertThatThrownBy(() -> BoundedAwait.get(pending, Duration.ofMillis(50), "provider"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not respond within");

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        assertThat(elapsedMillis).isLessThan(2_000L);
        assertThat(pending.isCancelled()).isTrue();
    }
}
