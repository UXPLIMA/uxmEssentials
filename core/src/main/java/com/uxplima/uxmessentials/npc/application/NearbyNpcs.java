package com.uxplima.uxmessentials.npc.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * {@code /npc nearby [radius]}: list every NPC within {@code radius} blocks of the operator, nearest first, with
 * its distance. NPCs in another world are infinitely far apart (the {@link Position} distance contract), so they
 * fall outside any finite radius. The header / per-entry / empty feedback all resolve from {@link NpcMessageKey}.
 * The operator-only permission is enforced at the command gate.
 */
public final class NearbyNpcs {

    /** The default search radius in blocks when the operator gives none. */
    public static final int DEFAULT_RADIUS = 16;

    /** The largest radius the search accepts, in blocks, so a careless value never scans the whole map. */
    public static final int MAX_RADIUS = 256;

    private final NpcRepository repository;
    private final Notifier notifier;

    public NearbyNpcs(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clamp a raw radius into {@code [1, MAX_RADIUS]} so operator input never scans unboundedly. */
    public static int clampRadius(int raw) {
        return Math.min(MAX_RADIUS, Math.max(1, raw));
    }

    /** List the NPCs within {@code radius} blocks of {@code origin}, nearest first; returns the matches. */
    public List<Npc> nearby(PlayerRef viewer, Position origin, int radius) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(origin, "origin");
        int bounded = clampRadius(radius);
        List<Match> matches = withinRadius(origin, bounded);
        if (matches.isEmpty()) {
            notifier.send(viewer, NpcMessageKey.NPC_NEARBY_EMPTY, Map.of("radius", Integer.toString(bounded)));
            return List.of();
        }
        notifier.send(
                viewer,
                NpcMessageKey.NPC_NEARBY_HEADER,
                Map.of("count", Integer.toString(matches.size()), "radius", Integer.toString(bounded)));
        for (Match match : matches) {
            notifier.send(
                    viewer,
                    NpcMessageKey.NPC_NEARBY_ENTRY,
                    Map.of(
                            "name", match.npc().name().value(),
                            "distance", String.format(Locale.ROOT, "%.1f", match.distance())));
        }
        return matches.stream().map(Match::npc).toList();
    }

    private List<Match> withinRadius(Position origin, int radius) {
        List<Match> matches = new ArrayList<>();
        for (Npc npc : repository.all()) {
            double distance = origin.distanceTo(npc.location());
            if (distance <= radius) {
                matches.add(new Match(npc, distance));
            }
        }
        matches.sort(Comparator.comparingDouble(Match::distance));
        return matches;
    }

    private record Match(Npc npc, double distance) {}
}
