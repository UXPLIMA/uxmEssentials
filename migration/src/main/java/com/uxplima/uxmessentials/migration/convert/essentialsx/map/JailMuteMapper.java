package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXJail;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXModeration;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXMute;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a parsed EssentialsX moderation block into a domain {@link ModerationProfile}
 * (docs/12-migration §2, §5.1). Pure ACL: no Bukkit, no YAML, no I/O. A {@code muted} flag maps to a
 * {@link MuteState} (permanent when no expiry, timed otherwise); a {@code jailed} flag with a named jail
 * maps to a {@link JailState} (permanent, wall-clock timed, or online-only timed, mirroring how
 * EssentialsX ran the sentence).
 *
 * <p>EssentialsX userdata records neither who issued a sanction nor when, so both are reconstructed
 * conservatively: the issuer is the {@link Issuer#console(String) console} actor named for the source,
 * and the issue instant is {@link Instant#EPOCH}. The import does not invent a staff UUID or a
 * plausible-but-false timestamp. The expiry/remaining figures, which <em>are</em> recorded, are carried
 * faithfully so the domain gate computes activity exactly as it would for a live sanction.
 */
@NullMarked
public final class JailMuteMapper {

    private static final String SOURCE_ISSUER = "EssentialsX";

    /** Map the block for {@code owner}, or empty when the player carries no mute and no jail. */
    public Optional<ImportedModeration> map(PlayerRef owner, EssXModeration src) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(src, "src");
        if (src.isEmpty()) {
            return Optional.empty();
        }
        ModerationProfile profile = ModerationProfile.clean(owner)
                .withMute(src.mute().map(this::toMute).orElseGet(MuteState::none))
                .withJail(src.jail().map(this::toJail).orElseGet(JailState::none));
        return Optional.of(new ImportedModeration(
                profile, src.mute().isPresent(), src.jail().isPresent()));
    }

    private MuteState toMute(EssXMute src) {
        Issuer issuer = Issuer.console(SOURCE_ISSUER);
        return src.expiryEpochMillis()
                .map(Instant::ofEpochMilli)
                .map(until -> MuteState.timed(until, issuer, src.reason(), Instant.EPOCH))
                .orElseGet(() -> MuteState.permanent(issuer, src.reason(), Instant.EPOCH));
    }

    private JailState toJail(EssXJail src) {
        Issuer issuer = Issuer.console(SOURCE_ISSUER);
        Optional<Long> wallClock = src.wallClockExpiryEpochMillis();
        if (wallClock.isPresent()) {
            Instant until = Instant.ofEpochMilli(wallClock.get());
            return JailState.wallClockTimed(src.jailName(), until, issuer, Optional.empty(), Instant.EPOCH);
        }
        Optional<Long> online = src.onlineMillisRemaining();
        if (online.isPresent()) {
            Duration remaining = Duration.ofMillis(online.get());
            return JailState.onlineTimed(src.jailName(), remaining, issuer, Optional.empty(), Instant.EPOCH);
        }
        return JailState.permanent(src.jailName(), issuer, Optional.empty(), Instant.EPOCH);
    }
}
