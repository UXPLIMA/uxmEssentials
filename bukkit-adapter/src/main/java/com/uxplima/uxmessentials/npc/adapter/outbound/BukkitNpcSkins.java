package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Optional;

import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import org.jspecify.annotations.NullMarked;

/**
 * Lifts a fake player's {@link NpcSkin} out of an online Bukkit player's profile. The {@code textures} property
 * value and its signature, exactly the two strings the spawn packet carries. The creator's own skin is the
 * default for {@code /npc create}, and {@code /npc skin <name> player:<other>} copies another online player's
 * skin; both read the live profile, which is already populated at login, so this is a pure off-the-profile read
 * with no Mojang round-trip (resolving an offline player's skin by name is deferred to a later sub-phase).
 *
 * <p>An online player on an offline-mode server has no {@code textures} property, so the read returns empty and
 * the caller renders the NPC skinless (the default Steve).
 */
@NullMarked
public final class BukkitNpcSkins {

    private static final String TEXTURES_PROPERTY = "textures";

    private BukkitNpcSkins() {}

    /** The {@code textures}-property skin of an online {@code player}, or empty when they carry none. */
    public static Optional<NpcSkin> of(Player player) {
        return of(player.getPlayerProfile());
    }

    private static Optional<NpcSkin> of(PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (TEXTURES_PROPERTY.equals(property.getName())) {
                return Optional.of(new NpcSkin(property.getValue(), property.getSignature()));
            }
        }
        return Optional.empty();
    }
}
