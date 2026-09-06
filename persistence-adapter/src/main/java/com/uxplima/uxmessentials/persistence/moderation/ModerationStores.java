package com.uxplima.uxmessentials.persistence.moderation;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the moderation context's persistence adapter, so the consuming bukkit-adapter wires the
 * {@link ModerationRepository} from the {@link Persistence} handle it already holds without ever naming a
 * jOOQ type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath).
 *
 * <p>The sanction store is left uncached: a mute/jail/tempban read is on the {@code MutePolicy}/{@code
 * JailGate} hot paths, but a stale cache would let a just-lifted mute keep gagging or a just-applied jail go
 * unenforced, and correctness wins over the small read saving here. The DB read is a single keyed lookup on a
 * primary-key index, so it is already cheap.
 */
@NullMarked
public final class ModerationStores {

    private ModerationStores() {}

    /** A jOOQ {@link ModerationRepository} over the shared persistence DSL. */
    public static ModerationRepository repository(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqModerationRepository(persistence.dsl());
    }

    /** A jOOQ {@link JailLocationStore} over the shared persistence DSL, the writable jail-location seam. */
    public static JailLocationStore jailLocationStore(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqJailLocationStore(persistence.dsl());
    }

    /** A jOOQ {@link SanctionHistory} over the shared persistence DSL, the append-only history seam. */
    public static SanctionHistory sanctionHistory(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqSanctionHistory(persistence.dsl());
    }
}
