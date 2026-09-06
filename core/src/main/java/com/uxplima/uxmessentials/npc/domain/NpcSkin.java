package com.uxplima.uxmessentials.npc.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A fake player's skin as the two raw strings the game protocol carries: the base64 value of the Mojang
 * profile's "textures" property and its (optional) Yggdrasil signature, plus whether the body model is the
 * {@code slim} (three-pixel-arm, "Alex") variant rather than the {@code classic} (four-pixel-arm, "Steve") one.
 * The domain stores them verbatim, how they reach a client (a player-info entry on a spawn packet) is an
 * adapter concern, so the aggregate never imports a Bukkit profile type. A {@code null} signature is an
 * unsigned skin (still rendered, though some clients reject an unsigned skin from another player's account); a
 * blank {@code texture} is rejected so a skin object always carries something to render.
 *
 * <p>The model variant is a property of the texture itself, a slim texture wears wrong on a classic model, and
 * the texture's own metadata carries it for a fetched skin, so {@code slim} defaults to {@code false} (classic)
 * and is only set explicitly when the operator overrides it. It is carried here so the adapter can apply it to
 * the displayed-model byte at render time, the way the skin profile's variant is normally set.
 *
 * @param texture the base64-encoded skin-property value
 * @param signature the Yggdrasil signature for {@code texture}, or {@code null} for an unsigned skin
 * @param slim whether the body model is the slim (Alex) variant rather than classic (Steve)
 */
public record NpcSkin(String texture, @Nullable String signature, boolean slim) {

    public NpcSkin {
        Objects.requireNonNull(texture, "texture");
        if (texture.isBlank()) {
            throw new IllegalArgumentException("npc skin texture must not be blank");
        }
    }

    /** A classic-model skin carrying a texture and its (optional) signature: the common, variant-unspecified form. */
    public NpcSkin(String texture, @Nullable String signature) {
        this(texture, signature, false);
    }

    /** A skin carrying only a texture value, with no signature (an unsigned, classic-model skin). */
    public static NpcSkin unsigned(String texture) {
        return new NpcSkin(texture, null, false);
    }

    /** A copy of this skin on the given body model ({@code true} slim, {@code false} classic), texture unchanged. */
    public NpcSkin withSlim(boolean newSlim) {
        return new NpcSkin(texture, signature, newSlim);
    }
}
