package com.uxplima.uxmessentials.persistence.invrollback;

import java.util.Objects;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the invrollback context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link SnapshotRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is a {@code compileOnly} dependency of this module, kept off the consumer's compile classpath). The
 * returned repository is the plain jOOQ adapter over the shared persistence DSL. Snapshots are written once and
 * read only by staff, so no cache decorator is warranted.
 */
@NullMarked
public final class SnapshotRepositories {

    private SnapshotRepositories() {}

    /** A jOOQ {@link SnapshotRepository} over the shared persistence DSL. */
    public static SnapshotRepository jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqSnapshotRepository(persistence.dsl());
    }
}
