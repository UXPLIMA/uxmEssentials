package com.uxplima.uxmessentials.vanish.application.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;

/**
 * The single authority over the vanish state: a transient per-player map of who is vanished and at which
 * {@link VanishLevel}. The adapter backs it with a {@code ConcurrentHashMap<UUID, VanishLevel>} mutated through
 * {@code compute} / {@code remove}, so a reader on any thread. A sync command, the async messaging resolution, the
 * nametag viewer cull, always sees a coherent value. Nothing here is persisted: the state is seeded by {@code
 * /vanish}, read by every consumer (messaging, nametags, staff), and dropped on quit, re-derived on the next join.
 *
 * <p>This is the one vanish state in the plugin. The presence, messaging, nametags, and staff contexts all read or
 * mutate vanish through this port (or a query backed by it): there is no second vanish flag anywhere.
 */
public interface VanishStore {

    /** Whether {@code who} is currently vanished. Safe from any thread. */
    boolean isVanished(UUID who);

    /** Mark {@code who} vanished at {@code level}, replacing any prior level. */
    void vanish(UUID who, VanishLevel level);

    /** Reveal {@code who}: drop them from the vanished set. Used for {@code /vanish off} and on quit. */
    void reveal(UUID who);

    /** The level {@code who} is vanished at, or empty when they are not vanished. */
    Optional<VanishLevel> levelOf(UUID who);

    /** The currently-vanished players. A point-in-time copy safe to iterate off the map. */
    Set<UUID> vanished();

    /** An immutable snapshot of the whole state, for the pure {@link VanishState#canSee} rule. */
    VanishState snapshot();
}
