package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.adapter.ModerationSettings;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.JailLocator;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link JailLocator} adapter: resolves a jail's display coordinates store-first (the DB-backed
 * {@link JailLocationStore} an operator fills with {@code /setjail}), falling back to the {@link ModerationSettings}
 * config jails. The same precedence {@link BukkitSanctions#sendToJail} teleports against, so a jail's lore names
 * the very location the operator would arrive at. The coordinates are read straight from the stored
 * {@link Position} (or the config record) and floored to blocks; no live world is required, so an unloaded world
 * is no obstacle to showing where the jail is. A jail neither stored nor configured returns empty.
 */
@NullMarked
public final class BukkitJailLocator implements JailLocator {

    private final ModerationSettings settings;
    private final JailLocationStore jailLocations;

    public BukkitJailLocator(ModerationSettings settings, JailLocationStore jailLocations) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jailLocations = Objects.requireNonNull(jailLocations, "jailLocations");
    }

    @Override
    public Optional<JailCoords> locate(String jail) {
        Objects.requireNonNull(jail, "jail");
        Optional<JailCoords> stored = storedCoords(jail);
        return stored.isPresent() ? stored : configCoords(jail);
    }

    private Optional<JailCoords> storedCoords(String jail) {
        return jailLocations.find(StoredJail.normalise(jail)).map(stored -> {
            Position at = stored.location();
            return new JailCoords(at.world().name(), at.blockX(), at.blockY(), at.blockZ());
        });
    }

    private Optional<JailCoords> configCoords(String jail) {
        return settings.location(jail)
                .map(loc -> new JailCoords(
                        loc.world(), (int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z())));
    }
}
