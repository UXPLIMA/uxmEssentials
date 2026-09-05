package com.uxplima.uxmessentials.holograms.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * {@code /hologram nearby [radius]}: list every hologram within {@code radius} blocks of the operator, nearest
 * first, with its distance. Holograms in another world are infinitely far apart (the {@link Position} distance
 * contract), so they fall outside any finite radius. The header / per-entry / empty feedback all resolve from
 * {@link HologramsMessageKey}. The operator-only permission is enforced at the command gate.
 */
public final class NearbyHolograms {

    /** The default search radius in blocks when the operator gives none. */
    public static final int DEFAULT_RADIUS = 16;

    /** The largest radius the search accepts, in blocks, so a careless value never scans the whole map. */
    public static final int MAX_RADIUS = 256;

    private final HologramRepository repository;
    private final Notifier notifier;

    public NearbyHolograms(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clamp a raw radius into {@code [1, MAX_RADIUS]} so operator input never scans unboundedly. */
    public static int clampRadius(int raw) {
        return Math.min(MAX_RADIUS, Math.max(1, raw));
    }

    /** List the holograms within {@code radius} blocks of {@code origin}, nearest first; returns the matches. */
    public List<Hologram> nearby(PlayerRef viewer, Position origin, int radius) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(origin, "origin");
        int bounded = clampRadius(radius);
        List<Match> matches = withinRadius(origin, bounded);
        if (matches.isEmpty()) {
            notifier.send(
                    viewer, HologramsMessageKey.HOLOGRAM_NEARBY_EMPTY, Map.of("radius", Integer.toString(bounded)));
            return List.of();
        }
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_NEARBY_HEADER,
                Map.of("count", Integer.toString(matches.size()), "radius", Integer.toString(bounded)));
        for (Match match : matches) {
            notifier.send(
                    viewer,
                    HologramsMessageKey.HOLOGRAM_NEARBY_ENTRY,
                    Map.of(
                            "name", match.hologram().name().value(),
                            "distance", String.format(Locale.ROOT, "%.1f", match.distance())));
        }
        return matches.stream().map(Match::hologram).toList();
    }

    private List<Match> withinRadius(Position origin, int radius) {
        List<Match> matches = new ArrayList<>();
        for (Hologram hologram : repository.all()) {
            double distance = origin.distanceTo(hologram.location());
            if (distance <= radius) {
                matches.add(new Match(hologram, distance));
            }
        }
        matches.sort(Comparator.comparingDouble(Match::distance));
        return matches;
    }

    private record Match(Hologram hologram, double distance) {}
}
