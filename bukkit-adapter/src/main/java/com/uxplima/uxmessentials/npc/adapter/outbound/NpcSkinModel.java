package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NullMarked;

/**
 * Forces the slim ("Alex", three-pixel-arm) body model onto a skin texture by editing the texture property's own
 * metadata. The body model lives inside the base64 {@code textures} value, under {@code textures.SKIN.metadata.model};
 * a fetched skin's value already states its model, so this is only needed when an operator overrides a classic
 * texture to slim. Re-encoding the value invalidates the Yggdrasil signature, so the caller sends the result
 * unsigned: a synthetic NPC renders an unsigned skin fine. Every step is fail-soft: a value that is not base64,
 * not JSON, carries no SKIN texture, or already declares slim yields {@link Optional#empty()}, and the caller then
 * keeps the original signed value unchanged.
 *
 * <p>gson is on the server classpath at runtime (the plugin loader provisions it), so this carries no shaded
 * dependency.
 */
@NullMarked
final class NpcSkinModel {

    private static final String MODEL = "model";
    private static final String SLIM = "slim";

    private NpcSkinModel() {}

    /**
     * The base64 texture value re-encoded to declare the slim model, or empty when no change is needed or possible
     * (the value is unparseable, carries no SKIN texture, or already declares slim).
     */
    static Optional<String> slimTexture(String textureValue) {
        try {
            String json = new String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            if (skin == null) {
                return Optional.empty();
            }
            JsonObject metadata = skin.getAsJsonObject("metadata");
            if (metadata != null
                    && metadata.has(MODEL)
                    && SLIM.equals(metadata.get(MODEL).getAsString())) {
                return Optional.empty();
            }
            if (metadata == null) {
                metadata = new JsonObject();
                skin.add("metadata", metadata);
            }
            metadata.addProperty(MODEL, SLIM);
            return Optional.of(
                    Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException malformed) {
            // gson and Base64 throw RuntimeExceptions on a value that is not base64 or not the expected JSON shape;
            // a texture we cannot edit is just left as-is (rendered classic), so swallow it rather than propagate.
            return Optional.empty();
        }
    }
}
