package com.uxplima.uxmessentials.migration.convert.litebans.map;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRow;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Maps one {@code litebans_mutes} row into an {@link ImportRecord.ModerationRecord} carrying a mute-only
 * {@link ImportedModeration} (docs/12-migration §5.4). Mutes ride the existing {@code ImportedModeration}
 * path the EssentialsX source already uses, so they funnel through the same mute write the {@code /mute}
 * command does: an imported mute can never reach a state a live mute could not.
 *
 * <p>A {@code until <= 0} mute is permanent; otherwise it lifts at the recorded instant. A row whose target
 * UUID is a LiteBans sentinel is dropped. The issue instant is the row's {@code time}, faithfully carried
 * (unlike the EssentialsX source, which lacks one and falls back to the epoch).
 */
@NullMarked
public final class LiteBansMuteMapper {

    /** Map a mutes-table row to a moderation record, or empty when the target is a sentinel. */
    public Optional<ImportRecord> map(LiteBansRow row) {
        Optional<PlayerRef> target = LiteBansActors.target(row);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Instant issuedAt = Instant.ofEpochMilli(row.time());
        Issuer issuer = LiteBansActors.issuer(row);
        MuteState mute = row.isPermanent()
                ? MuteState.permanent(issuer, row.reason(), issuedAt)
                : MuteState.timed(Instant.ofEpochMilli(row.until()), issuer, row.reason(), issuedAt);
        ModerationProfile profile = ModerationProfile.clean(target.get()).withMute(mute);
        return Optional.of(new ImportRecord.ModerationRecord(new ImportedModeration(profile, true, false)));
    }
}
