package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.NullMarked;

/**
 * The status-aware result of an HTTP call the {@link MineSkinService} v2 flow needs to branch on, where the plain
 * body-or-empty contract of {@link HttpFetcher#post(java.net.URI, String)} is too coarse. The v2 queue can answer
 * {@code 200} with the skin inline, {@code 202} (or a body carrying a job id) for a queued generation that must
 * be polled, and {@code 429} when rate-limited. Each carries the body (the inline skin, the job reference, or an
 * error) and, on a {@code 429}, the {@code Retry-After} seconds when the server sent one, so the caller can honour
 * a bounded backoff rather than hammer the endpoint.
 *
 * @param status the HTTP status code, or {@code 0} for a transport error/timeout (treated as a miss)
 * @param body the response body, empty for a transport error/timeout
 * @param retryAfterSeconds the {@code Retry-After} header value in seconds when present (typically on a {@code 429})
 */
@NullMarked
public record HttpResponseView(int status, Optional<String> body, OptionalLong retryAfterSeconds) {

    public HttpResponseView {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(retryAfterSeconds, "retryAfterSeconds");
    }

    /** A transport error or timeout: no status, no body: the service treats it as a miss. */
    public static HttpResponseView transportError() {
        return new HttpResponseView(0, Optional.empty(), OptionalLong.empty());
    }

    /** A response with a status and body but no {@code Retry-After}. */
    public static HttpResponseView of(int status, String body) {
        return new HttpResponseView(status, Optional.of(Objects.requireNonNull(body, "body")), OptionalLong.empty());
    }
}
