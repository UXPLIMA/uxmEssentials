package com.uxplima.uxmessentials.persistence.playerstate;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the playerstate context's playtime persistence adapter, so the consuming bukkit-adapter wires a
 * {@link PlaytimeRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath).
 *
 * <p>The ledger is write-heavy (one upsert per online player per sample) and read-rarely (only on
 * {@code /playtime}), so it is the plain jOOQ adapter with no Caffeine layer. A cache would buy nothing and would
 * have to be invalidated on every sample.
 */
@NullMarked
public final class PlaytimeRepositories {

    private PlaytimeRepositories() {}

    /** A jOOQ {@link PlaytimeRepository} over the shared persistence DSL. */
    public static PlaytimeRepository jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlaytimeRepository(persistence.dsl());
    }
}
