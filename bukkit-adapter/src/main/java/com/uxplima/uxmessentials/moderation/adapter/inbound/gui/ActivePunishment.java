package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import org.jspecify.annotations.NullMarked;

/**
 * One row of the active-punishments management list. A flat, adapter-side projection that folds the three
 * distinct active read models ({@link BanEntry}, {@link MuteEntry}, {@link JailEntry}) into a single carrier
 * the shared {@code EntityListView} and {@code EntityEditorView} are generic over. The moderation domain has
 * no single "punishment" aggregate (a ban is a tempban row, a mute a mute row, a jail a jail row), so the GUI
 * needs one type to list and to edit; this is purely a presentation projection, never a domain concept.
 *
 * <p>It carries everything the list icon and the detail/manage view interpolate (the kind, the target's UUID
 * and resolved display name, the issuer, the reason) plus how the sentence ends, a wall-clock {@link #until}
 * for a timed ban/mute/jail, or an online-only {@link #remaining} for a jail served while online; both empty
 * marks a permanent sanction. Revoking dispatches by {@link #kind} to the matching existing use case, keyed by
 * the {@link #target} UUID alone (the only key the {@code unban}/{@code unmute}/{@code unjail} use cases take).
 *
 * @param kind which sanction family this row records (ban, mute, or jail)
 * @param target the sanctioned player's UUID
 * @param targetName the target's resolved display name, falling back to the UUID when the account is unknown
 * @param issuer who issued the sanction
 * @param reason the optional free-text reason
 * @param until the wall-clock expiry for a timed sanction, or empty for a permanent one
 * @param remaining the online time still to serve for an online-only jail, or empty otherwise
 */
@NullMarked
public record ActivePunishment(
        PunishmentKind kind,
        UUID target,
        String targetName,
        Issuer issuer,
        Optional<String> reason,
        Optional<Instant> until,
        Optional<Duration> remaining) {

    public ActivePunishment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(remaining, "remaining");
    }

    /** Project an active ban row, resolving its display name through the supplied resolver. */
    public static ActivePunishment ofBan(BanEntry ban, String targetName) {
        Objects.requireNonNull(ban, "ban");
        return new ActivePunishment(
                PunishmentKind.BAN,
                ban.target(),
                targetName,
                ban.issuer(),
                ban.reason(),
                Optional.of(ban.until()),
                Optional.empty());
    }

    /** Project an active mute row. A permanent mute has an empty {@code until}. */
    public static ActivePunishment ofMute(MuteEntry mute, String targetName) {
        Objects.requireNonNull(mute, "mute");
        return new ActivePunishment(
                PunishmentKind.MUTE,
                mute.target(),
                targetName,
                mute.issuer(),
                mute.reason(),
                mute.until(),
                Optional.empty());
    }

    /** Project an active jail row, carrying either its wall-clock expiry or its online-only remaining time. */
    public static ActivePunishment ofJail(JailEntry jail, String targetName) {
        Objects.requireNonNull(jail, "jail");
        return new ActivePunishment(
                PunishmentKind.JAIL,
                jail.target(),
                targetName,
                jail.issuer(),
                jail.reason(),
                jail.until(),
                jail.remaining());
    }

    /** True when neither a wall-clock expiry nor an online-only remaining time is set: a permanent sanction. */
    public boolean isPermanent() {
        return until.isEmpty() && remaining.isEmpty();
    }
}
