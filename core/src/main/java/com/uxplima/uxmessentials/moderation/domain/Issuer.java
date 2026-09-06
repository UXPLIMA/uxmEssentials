package com.uxplima.uxmessentials.moderation.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Who issued a sanction: the {@code *_by}/{@code *_by_name} columns every sanction row carries. A staff
 * member's UUID is present; a console or system actor has no UUID but always a name, so an audit line and an
 * {@code /unmute}/{@code /unjail} failure message still render an issuer even after a rename.
 *
 * <p>The name is captured at issue time and stored, so a since-renamed issuer still shows the name the
 * sanction was applied under; identity (when comparing exemption against the issuer) is the UUID alone.
 *
 * @param uuid the issuing staff member's UUID, or empty for a console/system actor
 * @param name the name the sanction was issued under (never blank)
 */
public record Issuer(Optional<UUID> uuid, String name) {

    public Issuer {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("issuer name must not be blank");
        }
    }

    /** The console/system issuer: no UUID, the given display name. */
    public static Issuer console(String name) {
        return new Issuer(Optional.empty(), name);
    }

    /** An issuer resolved from a live staff {@link PlayerRef}. */
    public static Issuer of(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return new Issuer(Optional.of(who.uuid()), who.name());
    }

    /** Rebuild an issuer from stored columns: a present UUID is a player, an absent one a console actor. */
    public static Issuer stored(Optional<UUID> uuid, String name) {
        return new Issuer(uuid, name);
    }
}
