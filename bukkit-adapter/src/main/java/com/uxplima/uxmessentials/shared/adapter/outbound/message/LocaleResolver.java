package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a viewer's locale through the deterministic fallback chain (docs/13-i18n §4). The first hit
 * wins:
 *
 * <ol>
 *   <li>the player's persisted {@code /lang} override (from the {@link LocaleStore}): an operator's
 *       explicit choice always beats the client;</li>
 *   <li>the locale bound for this request at the command boundary ({@link LocaleScope#CURRENT}), the
 *       client locale captured on the region thread and carried across async hops, so a deferred
 *       message resolves in the requester's language on a worker thread;</li>
 *   <li>the configured server-default locale. The fallback for a path that never crossed the
 *       boundary (an event-driven broadcast with no requesting command);</li>
 *   <li>{@link Locale#ENGLISH}, the canonical root.</li>
 * </ol>
 *
 * <p>The resolver never touches the Bukkit API: the live client locale ({@code Player.locale()}) is a
 * region-thread call captured once at the boundary and folded into {@link LocaleScope}, so this class
 * works on any worker thread. The per-key {@code en} fallback (a key missing in {@code tr} falling back
 * to {@code en} for that key only) lives one layer down in the {@code LocaleCatalog}; this class only
 * chooses which locale to ask for.
 */
@NullMarked
public final class LocaleResolver {

    private final LocaleStore overrides;
    private final Locale serverDefault;

    public LocaleResolver(LocaleStore overrides, Locale serverDefault) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.serverDefault = Objects.requireNonNull(serverDefault, "serverDefault");
    }

    /** The viewer's resolved locale, walking the override → scope → server-default → en chain. */
    public Locale resolve(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Optional<Locale> override = overrides.override(viewer);
        if (override.isPresent()) {
            return override.get();
        }
        return LocaleScope.orElse(serverDefault);
    }
}
