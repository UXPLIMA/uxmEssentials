package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests {@link MineSkinService}'s MineSkin v2 generate/queue flow, defensive parsing, fail-soft empties and
 * URL-keyed caching against a fake HTTP seam: no live MineSkin call is ever made. The fake records each call
 * (count + last body + the {@code Authorization} token it was handed) and returns a scripted sequence of
 * {@link HttpResponseView}s, so a test can model a {@code 200} inline skin, a {@code 202} queued job followed by
 * a ready poll, a {@code 429} rate limit, or a transport error. A deferred scheduler runs the async work (and the
 * bounded inter-poll sleep) synchronously, so the returned future is already complete when the test reads it.
 */
class MineSkinServiceTest {

    private static final String IMAGE_URL = "https://example.com/skin.png";

    /** A v1-shaped success body (data.texture.*): proves the v2 service still parses a v1 response. */
    private static final String V1_GENERATED =
            "{\"data\":{\"texture\":{\"value\":\"Z2VuZXJhdGVk\",\"signature\":\"genSig=\"}}}";

    /** A v2-shaped inline success body (skin.texture.data.*). */
    private static final String V2_GENERATED =
            "{\"skin\":{\"texture\":{\"data\":{\"value\":\"djJnZW4=\",\"signature\":\"v2Sig=\"}}}}";

    /** A v2 queue body that defers the texture to a job poll. */
    private static final String V2_QUEUED = "{\"job\":{\"id\":\"job-77\",\"status\":\"waiting\"}}";

    private static final SignedSkin V1_SKIN = new SignedSkin(new SkinTexture("Z2VuZXJhdGVk", "genSig="), false);
    private static final SignedSkin V2_SKIN = new SignedSkin(new SkinTexture("djJnZW4=", "v2Sig="), false);

    @Test
    void generatesASignedSkinFromAnImageUrlViaTheV2InlineResponse() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, V2_GENERATED));
        MineSkinService service = newService(seam, null);

        Optional<SignedSkin> skin = await(service.fetchFromUrl(IMAGE_URL));

        assertThat(skin).contains(V2_SKIN);
        // The image URL is carried in the JSON POST body, and the v2 endpoint is hit.
        assertThat(seam.lastBody).contains(IMAGE_URL);
        assertThat(seam.lastPostUri).isEqualTo(MineSkinService.GENERATE_V2_ENDPOINT);
        assertThat(seam.posts).isEqualTo(1);
    }

    @Test
    void stillParsesAV1ShapedResponseForBackCompat() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, V1_GENERATED));
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).contains(V1_SKIN);
    }

    @Test
    void aQueuedJobIsPolledUntilTheTextureIsReady() {
        FakeSeam seam = new FakeSeam();
        // First the POST returns 202 + a job id, then the GET poll returns the ready skin.
        seam.script(new HttpResponseView(202, Optional.of(V2_QUEUED), OptionalLong.empty()));
        seam.scriptGet(HttpResponseView.of(200, V2_GENERATED));
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).contains(V2_SKIN);
        assertThat(seam.posts).isEqualTo(1);
        assertThat(seam.gets).isEqualTo(1);
        // The poll hit the queue endpoint with the job id appended.
        assertThat(seam.lastGetUri).contains(MineSkinService.QUEUE_V2_ENDPOINT).contains("job-77");
    }

    @Test
    void aQueuedJobStillPendingAcrossEveryAttemptYieldsEmpty() {
        FakeSeam seam = new FakeSeam();
        seam.script(new HttpResponseView(202, Optional.of(V2_QUEUED), OptionalLong.empty()));
        // Every poll re-answers "still queued" (echoes the job, no texture): the bound must give up, not loop.
        seam.getDefault = new HttpResponseView(200, Optional.of(V2_QUEUED), OptionalLong.empty());
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
        assertThat(seam.gets).isEqualTo(MineSkinService.MAX_POLL_ATTEMPTS);
    }

    @Test
    void aRateLimitedResponseYieldsEmptyWithoutHammering() {
        FakeSeam seam = new FakeSeam();
        // 429 every time, with a small Retry-After the bounded backoff honours before giving up.
        seam.getDefault = new HttpResponseView(429, Optional.of("{\"error\":\"rate_limit\"}"), OptionalLong.of(1));
        seam.postDefault = new HttpResponseView(429, Optional.of("{\"error\":\"rate_limit\"}"), OptionalLong.of(1));
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
        // At most a bounded number of POST attempts: never an unbounded retry storm.
        assertThat(seam.posts).isLessThanOrEqualTo(MineSkinService.MAX_RATE_LIMIT_RETRIES + 1);
    }

    @Test
    void aTransportErrorYieldsEmpty() {
        FakeSeam seam = new FakeSeam();
        seam.postDefault = HttpResponseView.transportError();
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
        assertThat(seam.posts).isEqualTo(1);
    }

    @Test
    void aResponseWithNoTextureAndNoJobYieldsEmpty() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, "{\"skin\":{\"id\":7}}"));
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
    }

    @Test
    void malformedJsonYieldsEmpty() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, "not json at all {"));
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
    }

    @Test
    void aBlankUrlYieldsEmptyWithoutAnyCall() {
        FakeSeam seam = new FakeSeam();
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl("   "))).isEmpty();
        assertThat(seam.posts).isZero();
    }

    @Test
    void anImageUrlThatIsNotAValidUrlYieldsEmptyWithoutAnyCall() {
        FakeSeam seam = new FakeSeam();
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl("not a url"))).isEmpty();
        assertThat(seam.posts).isZero();
    }

    @Test
    void aSecondGenerateForTheSameUrlIsServedFromCache() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, V2_GENERATED));
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isPresent();
        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isPresent();

        assertThat(seam.posts).isEqualTo(1);
    }

    @Test
    void afailedGenerateIsCachedSoItIsNotReGenerated() {
        FakeSeam seam = new FakeSeam();
        seam.postDefault = HttpResponseView.transportError();
        MineSkinService service = newService(seam, null);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();

        assertThat(seam.posts).isEqualTo(1);
    }

    @Test
    void whenAnApiKeyIsConfiguredItIsSentAsABearerToken() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, V2_GENERATED));
        MineSkinService service = newService(seam, "secret-key");

        await(service.fetchFromUrl(IMAGE_URL));

        assertThat(seam.lastPostToken).isEqualTo("secret-key");
    }

    @Test
    void withoutAnApiKeyNoBearerTokenIsSent() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, V2_GENERATED));
        MineSkinService service = newService(seam, null);

        await(service.fetchFromUrl(IMAGE_URL));

        assertThat(seam.lastPostToken).isNull();
    }

    @Test
    void aThrowingSeamCompletesTheFutureEmptyRatherThanOrphaningIt() {
        FakeSeam seam = new FakeSeam();
        seam.thrower = new IllegalStateException("boom");
        MineSkinService service = newService(seam, null);

        CompletableFuture<Optional<SignedSkin>> future = service.fetchFromUrl(IMAGE_URL);

        assertThat(future).isCompleted();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future.join()).isEmpty();
        assertThat(seam.posts).isEqualTo(1);
    }

    @Test
    void aSeamThatThrowsMidPollCompletesTheFutureEmptyRatherThanOrphaningIt() {
        FakeSeam seam = new FakeSeam();
        // The POST succeeds and hands back a queued job, so the throw only fires once the queue-poll GET runs
        // proving a throw deeper in the poll loop (not just on the first POST) still completes the future empty
        // rather than escaping the async stage and hanging the operator on the "generating" line.
        seam.script(new HttpResponseView(202, Optional.of(V2_QUEUED), OptionalLong.empty()));
        seam.getThrower = new IllegalStateException("poll exploded");
        MineSkinService service = newService(seam, null);

        CompletableFuture<Optional<SignedSkin>> future = service.fetchFromUrl(IMAGE_URL);

        assertThat(future).isCompleted();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future.join()).isEmpty();
        assertThat(seam.posts).isEqualTo(1);
        assertThat(seam.gets).isEqualTo(1);
    }

    @Test
    void anIllegalUrlCompletesEmptyWithoutEverCalling() {
        FakeSeam seam = new FakeSeam();
        seam.thrower = new IllegalStateException("must never be reached");
        MineSkinService service = newService(seam, null);

        CompletableFuture<Optional<SignedSkin>> future = service.fetchFromUrl("not a url");

        assertThat(future).isCompleted();
        assertThat(future.join()).isEmpty();
        assertThat(seam.posts).isZero();
    }

    @Test
    void theRequestBodyIsValidJsonCarryingTheUrlVerbatimAndAStringVisibility() {
        FakeSeam seam = new FakeSeam();
        seam.script(HttpResponseView.of(200, V2_GENERATED));
        MineSkinService service = newService(seam, null);
        // A real URL with query params and percent-escapes: the body must be parseable JSON whose url field is
        // the input verbatim and whose visibility is the v2 string form. Proving it is built (and escaped)
        // through gson rather than hand-concatenated, so an awkward URL can never break the body or inject a field.
        String url = "https://example.com/path/skin%20file.png?a=1&b=two";

        await(service.fetchFromUrl(url));

        JsonObject body = JsonParser.parseString(seam.lastBody).getAsJsonObject();
        assertThat(body.get("url").getAsString()).isEqualTo(url);
        assertThat(body.get("visibility").getAsString()).isEqualTo(MineSkinService.VISIBILITY);
        assertThat(body.size()).isEqualTo(2);
    }

    private static MineSkinService newService(FakeSeam seam, @Nullable String apiKey) {
        // Zero inter-poll/backoff delay keeps the queue-poll and rate-limit tests instant; production wires the
        // real bounded delay.
        return new MineSkinService(new ImmediateScheduler(), new NoOpLogger(), seam, apiKey, Duration.ZERO);
    }

    private static Optional<SignedSkin> await(CompletableFuture<Optional<SignedSkin>> future) {
        assertThat(future).isCompleted();
        return future.join();
    }

    /**
     * A scripted HTTP seam over the v2 {@code exchange}/{@code exchangeGet} variants: records each call (count +
     * last body + last uri + the {@code Authorization} token), and returns the next scripted response for that
     * verb (falling back to a per-verb default once the script is drained), or, when {@code thrower} is set,
     * throws it, modelling a faulty seam that must not orphan the service's future.
     */
    private static final class FakeSeam implements HttpFetcher {
        private final Deque<HttpResponseView> postScript = new ArrayDeque<>();
        private final Deque<HttpResponseView> getScript = new ArrayDeque<>();
        private HttpResponseView postDefault = HttpResponseView.transportError();
        private HttpResponseView getDefault = HttpResponseView.transportError();
        private @Nullable RuntimeException thrower;
        private @Nullable RuntimeException getThrower;
        private int posts;
        private int gets;
        private String lastBody = "";
        private String lastPostUri = "";
        private String lastGetUri = "";
        private @Nullable String lastPostToken;

        void script(HttpResponseView response) {
            postScript.add(response);
        }

        void scriptGet(HttpResponseView response) {
            getScript.add(response);
        }

        @Override
        public Optional<String> get(URI uri) {
            return Optional.empty();
        }

        @Override
        public HttpResponseView exchange(URI uri, String body, @Nullable String authToken) {
            posts++;
            lastBody = body;
            lastPostUri = uri.toString();
            lastPostToken = authToken;
            if (thrower != null) {
                throw thrower;
            }
            return postScript.isEmpty() ? postDefault : postScript.poll();
        }

        @Override
        public HttpResponseView exchangeGet(URI uri, @Nullable String authToken) {
            gets++;
            lastGetUri = uri.toString();
            if (getThrower != null) {
                throw getThrower;
            }
            if (thrower != null) {
                throw thrower;
            }
            return getScript.isEmpty() ? getDefault : getScript.poll();
        }
    }

    private static final class ImmediateScheduler implements Scheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }
    }

    private static final class NoOpLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();
        private final AtomicInteger errors = new AtomicInteger();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {
            errors.incrementAndGet();
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
