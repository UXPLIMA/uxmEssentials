package com.uxplima.uxmessentials.npc.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc list [type]}: list server-wide NPCs. Every one in stored creation order by default, or just those
 * of a given entity type (case-insensitive), name-sorted, when a {@code type} filter is supplied. NPCs are an
 * operator surface (the command is gated as a whole). The matches are returned for the adapter, and the header /
 * per-entry / empty feedback is pushed through the notifier so all text resolves from {@link NpcMessageKey}.
 */
public final class ListNpcs {

    private final NpcRepository repository;
    private final Notifier notifier;

    public ListNpcs(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Every NPC in stored creation order, also pushing the header/entries (or the empty notice). */
    public List<Npc> list(PlayerRef viewer) {
        return list(viewer, null);
    }

    /**
     * The NPCs, optionally narrowed to {@code typeFilter} (an entity-type name, case-insensitive) and then
     * name-sorted; an unfiltered call keeps stored creation order. Pushes the header/entries, or the empty notice
     * when none match.
     */
    public List<Npc> list(PlayerRef viewer, @Nullable String typeFilter) {
        Objects.requireNonNull(viewer, "viewer");
        List<Npc> matched = select(typeFilter);
        if (matched.isEmpty()) {
            notifier.send(viewer, NpcMessageKey.NPC_LIST_EMPTY);
            return matched;
        }
        notifier.send(viewer, NpcMessageKey.NPC_LIST_HEADER, Map.of("count", Integer.toString(matched.size())));
        for (Npc npc : matched) {
            notifier.send(
                    viewer,
                    NpcMessageKey.NPC_LIST_ENTRY,
                    Map.of(
                            "name",
                            npc.name().value(),
                            "world",
                            npc.location().world().name()));
        }
        return matched;
    }

    private List<Npc> select(@Nullable String typeFilter) {
        List<Npc> all = repository.all();
        if (typeFilter == null) {
            return all;
        }
        String upper = typeFilter.strip().toUpperCase(Locale.ROOT);
        return all.stream()
                .filter(npc -> npc.entityType().equals(upper))
                .sorted(Comparator.comparing(npc -> npc.name().value()))
                .toList();
    }
}
