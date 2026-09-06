package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The MineSkin v2 generate/queue exchange, split out of {@link MineSkinService} so the orchestration class stays
 * focused on validation, async-wrapping and caching while this owns the v2 wire contract. One generate is:
 *
 * <ol>
 *   <li>{@code POST /v2/generate} with the JSON body {@code {"url":"<imageUrl>","visibility":"unlisted"}} and,
 *       when configured, an {@code Authorization: Bearer <apiKey>} header (the key raises the rate limit; the
 *       unauthenticated tier still works for light use);</li>
 *   <li>a {@code 200} carrying the texture inline ({@code skin.texture.data.{value,signature}}, also tolerating
 *       the v1 {@code data.texture.*} shapes) completes immediately;</li>
 *   <li>a queued answer ({@code 202}, or a {@code 200} carrying only a {@code job.id} and no texture) is polled
 *       {@code GET /v2/queue/<jobId>} a bounded number of times with a short delay between, until the texture is
 *       ready or the attempts are exhausted (then a miss);</li>
 *   <li>a {@code 429} rate limit is retried a bounded number of times honouring {@code Retry-After} (capped), and
 *       failed soft to a miss thereafter. Never an unbounded retry storm.</li>
 * </ol>
 *
 * <p>The blocking calls and the bounded {@link #sleeper} delay run on the async-scheduler thread the caller has
 * already hopped onto (see {@link MineSkinService#fetchFromUrl}), never a region or tick thread. Every path is
 * fail-soft: a transport error, a textureless response, an exhausted poll, or an exhausted rate-limit backoff all
 * yield {@link Optional#empty()}.
 *
 * <p>Maintainer-verify: the exact v2 wire shape. Whether a queue submit answers {@code 200}-with-skin or
 * {@code 202}-with-job, the JSON path to the texture ({@code skin.texture.data.*} here) and to the job id
 * ({@code job.id}), the {@code visibility} enum, and the queue-poll path ({@code /v2/queue/<id>}), is read from
 * the documented v2 API and parsed defensively (v2 + v1 paths). Confirm against the live API and adjust the
 * endpoint constants / {@link MineSkinJson} paths in one place if it has shifted.
 */
@NullMarked
final class MineSkinV2 {

    private static final Gson GSON = new Gson();
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    /** Cap an honoured {@code Retry-After} so a hostile/huge value can never park the async thread for long. */
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5L);

    private final HttpFetcher fetcher;
    private final Logger log;
    private final URI generateEndpoint;
    private final String queueEndpointBase;
    private final @Nullable String apiKey;
    private final String visibility;
    private final int maxPollAttempts;
    private final int maxRateLimitRetries;
    private final Duration delay;
    private final Sleeper sleeper;

    MineSkinV2(
            HttpFetcher fetcher,
            Logger log,
            URI generateEndpoint,
            String queueEndpointBase,
            @Nullable String apiKey,
            String visibility,
            int maxPollAttempts,
            int maxRateLimitRetries,
            Duration delay,
            Sleeper sleeper) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.log = Objects.requireNonNull(log, "log");
        this.generateEndpoint = Objects.requireNonNull(generateEndpoint, "generateEndpoint");
        this.queueEndpointBase = Objects.requireNonNull(queueEndpointBase, "queueEndpointBase");
        this.apiKey = apiKey;
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.maxPollAttempts = maxPollAttempts;
        this.maxRateLimitRetries = maxRateLimitRetries;
        this.delay = Objects.requireNonNull(delay, "delay");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** Generate {@code imageUrl}'s signed skin through the v2 flow, or empty for any miss. Never throws checked. */
    Optional<SignedSkin> generate(String imageUrl) {
        return generate(imageUrl, null);
    }

    /**
     * Generate {@code imageUrl}'s signed skin, asking for a body model. A {@code null} {@code variant} leaves the
     * choice to MineSkin, which reads it off the image; {@code classic} or {@code slim} state it outright, which
     * is what an uploaded image needs, since the uploader knows which arm they drew.
     */
    Optional<SignedSkin> generate(String imageUrl, @Nullable String variant) {
        for (int attempt = 0; attempt <= maxRateLimitRetries; attempt++) {
            HttpResponseView response = fetcher.exchange(generateEndpoint, requestBody(imageUrl, variant), apiKey);
            if (response.status() == HTTP_TOO_MANY_REQUESTS) {
                if (attempt == maxRateLimitRetries || !backoff(response, imageUrl)) {
                    return Optional.empty();
                }
                continue;
            }
            return fromGenerateResponse(response, imageUrl);
        }
        return Optional.empty();
    }

    /** Resolve a non-429 generate response: an inline texture, a queued job to poll, or a miss. */
    private Optional<SignedSkin> fromGenerateResponse(HttpResponseView response, String imageUrl) {
        Optional<String> body = response.body();
        if (body.isEmpty()) {
            log.debug(
                    "MineSkin v2 generate for image URL {} returned no body (status {})", imageUrl, response.status());
            return Optional.empty();
        }
        Optional<SignedSkin> inline = MineSkinJson.skin(body.get());
        if (inline.isPresent()) {
            return inline;
        }
        Optional<String> jobId = MineSkinJson.jobId(body.get());
        if (jobId.isPresent()) {
            return poll(jobId.get(), imageUrl);
        }
        log.warn("MineSkin v2 generate response for image URL {} carried neither texture nor job id", imageUrl);
        return Optional.empty();
    }

    /** Poll the queue for {@code jobId} a bounded number of times until the texture is ready, else a miss. */
    private Optional<SignedSkin> poll(String jobId, String imageUrl) {
        Optional<URI> endpoint = queueUri(jobId);
        if (endpoint.isEmpty()) {
            return Optional.empty();
        }
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            if (!sleep()) {
                return Optional.empty();
            }
            HttpResponseView response = fetcher.exchangeGet(endpoint.get(), apiKey);
            Optional<SignedSkin> ready = response.body().flatMap(MineSkinJson::skin);
            if (ready.isPresent()) {
                return ready;
            }
        }
        log.debug("MineSkin v2 job {} for image URL {} not ready after {} polls", jobId, imageUrl, maxPollAttempts);
        return Optional.empty();
    }

    /** Honour a bounded {@code Retry-After} (or the default delay) before another generate attempt. */
    private boolean backoff(HttpResponseView response, String imageUrl) {
        OptionalLong retryAfter = response.retryAfterSeconds();
        Duration wait = retryAfter.isPresent() ? cap(Duration.ofSeconds(retryAfter.getAsLong())) : delay;
        log.debug("MineSkin v2 rate-limited for image URL {}; backing off {}", imageUrl, wait);
        return sleepFor(wait);
    }

    private static Duration cap(Duration requested) {
        Duration nonNegative = requested.isNegative() ? Duration.ZERO : requested;
        return nonNegative.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : nonNegative;
    }

    private boolean sleep() {
        return sleepFor(delay);
    }

    /** Sleep {@code wait} on the current (async) thread; returns false if interrupted, so the caller bails out. */
    private boolean sleepFor(Duration wait) {
        if (wait.isZero() || wait.isNegative()) {
            return true;
        }
        return sleeper.sleep(wait.toMillis());
    }

    private Optional<URI> queueUri(String jobId) {
        try {
            return Optional.of(URI.create(queueEndpointBase + jobId));
        } catch (IllegalArgumentException malformed) {
            log.warn("MineSkin v2 queue endpoint for job {} is not a valid URI", jobId);
            return Optional.empty();
        }
    }

    /**
     * The v2 generate body: the image URL and a string {@code visibility}. Built through gson so the URL, which
     * may carry a quote, backslash, or control character. Is escaped exactly as JSON requires and can never break
     * the body or inject a field, rather than relying on hand-rolled escaping.
     */
    private String requestBody(String imageUrl, @Nullable String variant) {
        JsonObject body = new JsonObject();
        body.addProperty("url", imageUrl);
        body.addProperty("visibility", visibility);
        if (variant != null && !variant.isBlank()) {
            body.addProperty("variant", variant);
        }
        return GSON.toJson(body);
    }

    /**
     * The bounded inter-poll / backoff sleep, behind a seam so a unit test runs instantly (a no-op sleeper) while
     * production sleeps on the async-scheduler thread the caller already owns, never a region or tick thread.
     */
    @FunctionalInterface
    interface Sleeper {

        /** Sleep {@code millis}; return {@code true} normally, {@code false} if interrupted (caller bails out). */
        boolean sleep(long millis);

        /** A real sleep on the calling (async) thread that restores the interrupt flag and reports interruption. */
        static boolean blocking(long millis) {
            try {
                Thread.sleep(millis);
                return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
