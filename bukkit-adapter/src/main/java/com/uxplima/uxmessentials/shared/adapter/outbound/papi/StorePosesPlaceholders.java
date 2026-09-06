package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PosesPlaceholders} read seam over the live poses state: the {@link PoseSessions} registry for
 * {@code poses_sitting} (the same single source of truth the {@code /sit} command and the seat listeners mutate)
 * and the {@link PlayerSitPreferences} store for {@code poses_toggle} (the {@code /poses toggle} opt-out). Both are
 * cheap lookups on the placeholder path.
 */
@NullMarked
public final class StorePosesPlaceholders implements PosesPlaceholders {

    private final PoseSessions sessions;
    private final PlayerSitPreferences preferences;

    public StorePosesPlaceholders(PoseSessions sessions, PlayerSitPreferences preferences) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** The pose name reported when the player holds no pose. */
    private static final String NONE = "none";

    @Override
    public boolean sitting(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return sessions.current(who)
                .filter(session -> session.type() == PoseType.SIT)
                .isPresent();
    }

    @Override
    public boolean posing(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        // A free pose is anything but the two sits: the lie-down and spin verbs of Phase 3 (and crawl later).
        return sessions.current(who)
                .map(PoseSession::type)
                .filter(type -> type != PoseType.SIT && type != PoseType.PLAYER_SIT)
                .isPresent();
    }

    @Override
    public String pose(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        // The lower-cased pose name, or "none" when not posing. PLAYER_SIT reads as "sit": from the subject's
        // side it is simply sitting, so the surface stays sit/lay/bellyflop/spin/none.
        return sessions.current(who)
                .map(PoseSession::type)
                .map(StorePosesPlaceholders::poseName)
                .orElse(NONE);
    }

    private static String poseName(PoseType type) {
        return type == PoseType.PLAYER_SIT ? "sit" : type.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean allowsSitting(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return preferences.allowsSitting(who);
    }
}
