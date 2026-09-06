package com.uxplima.uxmessentials.persistence.communication;

import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the communication context's announcement-store adapters, so the consuming bukkit-adapter wires an
 * {@link AnnouncementStore} and the global {@link AnnouncerSettingsStore} from the {@link Persistence} handle it
 * already holds without ever naming a jOOQ type (jOOQ is an {@code implementation} dependency of this module, kept
 * off the consumer's compile classpath).
 */
@NullMarked
public final class AnnouncementStores {

    private AnnouncementStores() {}

    /** A jOOQ {@link AnnouncementStore} over the shared persistence DSL. */
    public static AnnouncementStore jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqAnnouncementStore(persistence.dsl());
    }

    /** A jOOQ {@link AnnouncerSettingsStore} over the shared persistence DSL: the single global-settings row. */
    public static AnnouncerSettingsStore settings(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqAnnouncerSettingsStore(persistence.dsl());
    }
}
