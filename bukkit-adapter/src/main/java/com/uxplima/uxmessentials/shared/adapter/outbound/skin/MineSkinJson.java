package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Extracts the signed texture value/signature (and a queue job id) from a MineSkin generate response, isolated
 * here so the {@link MineSkinService} orchestration stays free of gson and the parsing is testable on its own.
 * The MineSkin API has carried the texture pair under several shapes across versions, {@code data.texture.value}
 * / {@code data.texture.signature} (the classic v1 generate-from-url response), a top-level {@code texture.value},
 * a {@code texture.data.value} nesting, and the v2 {@code skin.texture.data.value} (the shape the official v2
 * client reads) / {@code skin.data.texture.value}, so the texture object is located defensively by walking those
 * candidate paths and the strings are read wherever they sit. The v2 queue submit can instead return a job
 * reference; {@link #jobId(String)} reads that id so the service can poll for completion. Every method is
 * fail-soft: a body that does not parse, or that lacks the texture/value/job, yields an empty {@link Optional}
 * rather than throwing, because a malformed or partial response is just another "no skin" the service handles.
 *
 * <p>gson is on the server classpath at runtime (the plugin loader provisions it), so this carries no shaded
 * dependency.
 */
@NullMarked
final class MineSkinJson {

    private MineSkinJson() {}

    /** The signed {@link SignedSkin} from a generate response, or empty when it carries no usable texture value. */
    static Optional<SignedSkin> skin(String body) {
        Optional<JsonObject> root = object(body);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        return texture(root.get()).flatMap(MineSkinJson::fromTexture);
    }

    /**
     * The v2 queue job id from a submit/poll response, or empty when the body carries no job reference (it is a
     * direct skin result, or malformed). Read defensively from {@code job.id}, {@code job.uuid}, or a flat
     * top-level {@code id}/{@code uuid}, since the exact field is a v2 wire detail a maintainer may need to
     * confirm against the live API.
     */
    static Optional<String> jobId(String body) {
        Optional<JsonObject> root = object(body);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonObject job = child(root.get(), "job");
        JsonObject holder = (job != null) ? job : root.get();
        String id = string(holder, "id");
        if (id == null) {
            id = string(holder, "uuid");
        }
        return (id == null || id.isBlank()) ? Optional.empty() : Optional.of(id);
    }

    /**
     * Locate the texture object across the response shapes the API has used. The v2 success nests the pair under a
     * {@code skin} object ({@code skin.texture.data} or {@code skin.data.texture}); the v1 shapes sit at the root
     * ({@code data.texture}, a top-level {@code texture}, or {@code texture.data}). The {@code skin} wrapper is
     * unwrapped first, then the same root paths are walked, so a v1-shaped response still resolves. Returns the
     * first texture object found, or empty when none is present.
     */
    private static Optional<JsonObject> texture(JsonObject root) {
        JsonObject skin = child(root, "skin");
        if (skin != null) {
            Optional<JsonObject> underSkin = textureAtRoot(skin);
            if (underSkin.isPresent()) {
                return underSkin;
            }
        }
        return textureAtRoot(root);
    }

    /** Walk the v1 root paths, {@code data.texture}, a top-level {@code texture}, or {@code texture.data}. */
    private static Optional<JsonObject> textureAtRoot(JsonObject root) {
        JsonObject data = child(root, "data");
        Optional<JsonObject> underData =
                (data == null) ? Optional.empty() : Optional.ofNullable(child(data, "texture"));
        if (underData.isPresent()) {
            return underData;
        }
        JsonObject texture = child(root, "texture");
        if (texture == null) {
            return Optional.empty();
        }
        JsonObject nested = child(texture, "data");
        return Optional.of(nested != null ? nested : texture);
    }

    /** Build an {@link SignedSkin} from a texture object, or empty when its {@code value} is absent or blank. */
    private static Optional<SignedSkin> fromTexture(JsonObject texture) {
        String value = string(texture, "value");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String signature = string(texture, "signature");
        String signed = (signature == null || signature.isBlank()) ? null : signature;
        return Optional.of(new SignedSkin(new SkinTexture(value, signed), slim(texture)));
    }

    /**
     * Whether the generated texture is the slim (Alex) model. MineSkin reports the variant it settled on, so the
     * answer comes from the response rather than from what was asked for; anything else reads as classic.
     */
    private static boolean slim(JsonObject texture) {
        String variant = string(texture, "variant");
        return "slim".equalsIgnoreCase(variant);
    }

    private static @Nullable JsonObject child(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return (element != null && element.isJsonObject()) ? element.getAsJsonObject() : null;
    }

    private static @Nullable String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return (element != null && element.isJsonPrimitive()) ? element.getAsString() : null;
    }

    private static Optional<JsonObject> object(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException malformed) {
            // gson throws JsonSyntaxException (a RuntimeException) on a malformed body; an unparseable response
            // is just a miss the caller caches, so swallow it here rather than propagate.
            return Optional.empty();
        }
    }
}
