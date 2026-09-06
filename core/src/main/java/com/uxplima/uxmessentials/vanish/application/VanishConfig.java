package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The typed, immutable view of {@code modules/vanish/config.conf}: the module enable gate plus the Phase 3 behaviour
 * toggles that govern what a vanished player experiences. Resolved once from the module's scoped {@link ConfigStore}
 * when the module starts and, per the atomic-reload rule, swapped whole on reload, so a command or listener that reads
 * it mid-reload sees one coherent snapshot.
 *
 * <p>Every toggle carries the default the bundled config ships, so an operator who deletes a line falls back to the
 * shipped value rather than to {@code false}. The interaction toggles ({@code silent-chests}, {@code no-hunger},
 * {@code no-damage}, {@code mob-target}, the night-vision and flight buffs) default on because a vanished admin is
 * meant to move through the world unseen and untouched; {@code pickup-items} defaults off so a vanished player does not
 * silently vacuum drops, and each player flips their own preference with {@code /vanish pickup}.
 *
 * <p>The Phase 4 connection illusions default on too. {@code fake-join-quit} makes a vanishing player broadcast a fake
 * "left the game" line (and a fake "joined the game" on reappear) so no viewer notices they merely went invisible; the
 * two staff variants let a different line (or nothing, when left blank) go to viewers who <em>can</em> see them. The
 * template strings are operator MiniMessage content ({@code {player}} is substituted), never a {@code MessageKey}, so
 * they mirror how the connection-message context treats join/quit lines. {@code action-bar} drives the persistent
 * "you are vanished" indicator (its text is the {@code vanish.actionbar} catalog key), and {@code join-vanished} lets a
 * player who holds {@code uxmessentials.vanish.persist} rejoin already hidden.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param silentChests suppress the open animation/sound when a vanished player opens a chest, shulker box, ender
 *     chest, or barrel ({@code silent-chests}, default {@code true})
 * @param pickupItems the default for whether a vanished player picks up items; each player overrides it per-player
 *     with {@code /vanish pickup} ({@code pickup-items}, default {@code false})
 * @param nightVision grant permanent night vision while vanished ({@code night-vision}, default {@code true})
 * @param allowFlight allow flight while vanished, restoring the prior state on reappear ({@code allow-flight},
 *     default {@code true})
 * @param noHunger stop hunger draining while vanished ({@code no-hunger}, default {@code true})
 * @param noDamage make a vanished player take no incoming damage ({@code no-damage}, default {@code true})
 * @param mobTarget stop mobs from targeting, and drop any existing target on, a vanished player ({@code mob-target},
 *     default {@code true})
 * @param fakeJoinQuit broadcast a fake quit when a player vanishes and a fake join when they reappear, suppressing the
 *     player's real connection lines while vanished ({@code fake-join-quit}, default {@code true})
 * @param actionBar show the persistent {@code vanish.actionbar} indicator to a vanished player ({@code action-bar},
 *     default {@code true})
 * @param joinVanished let a player holding {@code uxmessentials.vanish.persist} rejoin already vanished, with their
 *     real join line suppressed ({@code join-vanished}, default {@code true})
 * @param fakeQuitMessage the MiniMessage broadcast a vanishing player fakes to viewers who cannot see them, with
 *     {@code {player}} substituted ({@code fake-quit-message})
 * @param fakeJoinMessage the MiniMessage broadcast a reappearing player fakes to viewers who cannot see them
 *     ({@code fake-join-message})
 * @param fakeQuitMessageStaff the MiniMessage shown instead to viewers who <em>can</em> see the vanishing player; a
 *     blank value sends them nothing ({@code fake-quit-message-staff})
 * @param fakeJoinMessageStaff the MiniMessage shown instead to viewers who can see the reappearing player; blank sends
 *     nothing ({@code fake-join-message-staff})
 * @param crossServer sync vanish state across backends over the bus, so a player vanished on one server arrives still
 *     vanished on another and {@code /vanish list} shows the whole network ({@code cross-server}, default {@code
 *     false}); inert unless the network bus is also enabled
 * @param readForeignVanish treat a player hidden by SuperVanish or PremiumVanish as vanished for our tab list,
 *     nametags, {@code /msg} and {@code /list} ({@code foreign.read-supervanish}, default {@code true}); inert when
 *     neither plugin is installed
 * @param foreignVanishLevel the use level a foreign-hidden player is folded in at, so the usual see-level rule decides
 *     who still sees them ({@code foreign.level}, default {@code 1})
 */
public record VanishConfig(
        boolean enabled,
        boolean silentChests,
        boolean pickupItems,
        boolean nightVision,
        boolean allowFlight,
        boolean noHunger,
        boolean noDamage,
        boolean mobTarget,
        boolean fakeJoinQuit,
        boolean actionBar,
        boolean joinVanished,
        String fakeQuitMessage,
        String fakeJoinMessage,
        String fakeQuitMessageStaff,
        String fakeJoinMessageStaff,
        boolean crossServer,
        boolean readForeignVanish,
        int foreignVanishLevel) {

    private static final String DEFAULT_FAKE_QUIT = "<yellow>{player} left the game";
    private static final String DEFAULT_FAKE_JOIN = "<yellow>{player} joined the game";

    public VanishConfig {
        Objects.requireNonNull(fakeQuitMessage, "fakeQuitMessage");
        Objects.requireNonNull(fakeJoinMessage, "fakeJoinMessage");
        Objects.requireNonNull(fakeQuitMessageStaff, "fakeQuitMessageStaff");
        Objects.requireNonNull(fakeJoinMessageStaff, "fakeJoinMessageStaff");
    }

    /** Resolve the vanish config from the module's scoped {@link ConfigStore} ({@code modules.vanish}). */
    public static VanishConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new VanishConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("silent-chests", true),
                config.getBoolean("pickup-items", false),
                config.getBoolean("night-vision", true),
                config.getBoolean("allow-flight", true),
                config.getBoolean("no-hunger", true),
                config.getBoolean("no-damage", true),
                config.getBoolean("mob-target", true),
                config.getBoolean("fake-join-quit", true),
                config.getBoolean("action-bar", true),
                config.getBoolean("join-vanished", true),
                config.getString("fake-quit-message", DEFAULT_FAKE_QUIT),
                config.getString("fake-join-message", DEFAULT_FAKE_JOIN),
                config.getString("fake-quit-message-staff", ""),
                config.getString("fake-join-message-staff", ""),
                config.getBoolean("cross-server", false),
                config.getBoolean("foreign.read-supervanish", true),
                config.getInt("foreign.level", VanishLevel.MIN_LEVEL));
    }
}
