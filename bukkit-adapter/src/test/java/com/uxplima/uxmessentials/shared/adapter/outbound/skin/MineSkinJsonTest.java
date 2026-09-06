package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link MineSkinJson}'s defensive extraction of the signed texture value/signature from a MineSkin
 * generate response, across the response shapes the public API has used, the v1 {@code data.texture.*}, a bare
 * {@code texture.*}, a nested {@code texture.data.*}, and the v2 {@code skin.texture.data.*} /
 * {@code skin.data.texture.*}, plus the v2 queue job id, and its fail-soft empties for a malformed or partial
 * body.
 */
class MineSkinJsonTest {

    @Test
    void extractsValueAndSignatureFromDataTextureShape() {
        String body = "{\"data\":{\"texture\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}}";

        Optional<SignedSkin> skin = MineSkinJson.skin(body);

        assertThat(skin).contains(new SignedSkin(new SkinTexture("dGV4dA==", "sig="), false));
    }

    @Test
    void extractsFromBareTextureShape() {
        String body = "{\"texture\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}";

        assertThat(MineSkinJson.skin(body)).contains(new SignedSkin(new SkinTexture("dGV4dA==", "sig="), false));
    }

    @Test
    void extractsFromNestedTextureDataShape() {
        // The v2 queue response nests the strings one level deeper under texture.data.
        String body = "{\"texture\":{\"data\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}}";

        assertThat(MineSkinJson.skin(body)).contains(new SignedSkin(new SkinTexture("dGV4dA==", "sig="), false));
    }

    @Test
    void aValueWithNoSignatureYieldsAnUnsignedSkin() {
        String body = "{\"data\":{\"texture\":{\"value\":\"dGV4dA==\"}}}";

        Optional<SignedSkin> skin = MineSkinJson.skin(body);

        assertThat(skin).isPresent();
        assertThat(skin.orElseThrow().value()).isEqualTo("dGV4dA==");
        assertThat(skin.orElseThrow().texture().signature()).isNull();
    }

    @Test
    void extractsFromV2SkinTextureDataShape() {
        // The v2 generate/queue success carries the texture under skin.texture.data, the shape the official v2
        // client reads (skinInfo.texture().data().value()).
        String body = "{\"skin\":{\"texture\":{\"data\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}}}";

        assertThat(MineSkinJson.skin(body)).contains(new SignedSkin(new SkinTexture("dGV4dA==", "sig="), false));
    }

    @Test
    void extractsFromV2SkinDataTextureShape() {
        // A v1-style data.texture nesting wrapped under a v2 skin object, tolerated defensively.
        String body = "{\"skin\":{\"data\":{\"texture\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}}}";

        assertThat(MineSkinJson.skin(body)).contains(new SignedSkin(new SkinTexture("dGV4dA==", "sig="), false));
    }

    @Test
    void aMissingTextureYieldsEmpty() {
        assertThat(MineSkinJson.skin("{\"data\":{\"id\":42}}")).isEmpty();
    }

    @Test
    void readsTheQueueJobIdFromTheTopLevelJobObject() {
        // The v2 queue submit returns a job reference; the id lives under job.id.
        String body = "{\"job\":{\"id\":\"abc-123\",\"status\":\"waiting\"}}";

        assertThat(MineSkinJson.jobId(body)).contains("abc-123");
    }

    @Test
    void readsTheQueueJobIdFromAVariantUuidField() {
        // Defensive across the field the id may sit under (id / uuid) and a flatter shape with no job wrapper.
        assertThat(MineSkinJson.jobId("{\"job\":{\"uuid\":\"u-9\"}}")).contains("u-9");
        assertThat(MineSkinJson.jobId("{\"id\":\"flat-7\"}")).contains("flat-7");
    }

    @Test
    void aResponseWithNoJobIdYieldsEmpty() {
        assertThat(MineSkinJson.jobId("{\"skin\":{\"texture\":{}}}")).isEmpty();
        assertThat(MineSkinJson.jobId("not json {")).isEmpty();
    }

    @Test
    void aBlankValueYieldsEmpty() {
        assertThat(MineSkinJson.skin("{\"data\":{\"texture\":{\"value\":\"\"}}}"))
                .isEmpty();
    }

    @Test
    void malformedJsonYieldsEmpty() {
        assertThat(MineSkinJson.skin("not json {")).isEmpty();
    }
}
