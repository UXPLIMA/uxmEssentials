package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.npc.domain.event.NpcDeleted;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The bukkit-adapter realisation of the holograms {@link LinkedNpcLocator}, and the cross-context seam that lets a
 * hologram follow an NPC without the two contexts coupling at compile time beyond the shared {@link Position}.
 *
 * <p>It keeps a live {@code name → position} index of every NPC, seeded once from the npc repository when the
 * holograms module wires (so an NPC that already existed before enable is locatable at once) and kept current off
 * the in-process domain-event bus: {@link NpcCreated} and {@link NpcMoved} write the index, {@link NpcDeleted}
 * clears the entry. A {@link #locate(String)} therefore reads an in-memory map, allocation-light, never a
 * database hit on the render path. The npc name is the canonical lowercase {@code NpcName}, matching the value a
 * hologram stores when linked.
 *
 * <p>On a move or delete it also invokes the injected {@code reanchor} callback for that NPC name, so a hologram
 * linked to the NPC re-renders at the new position immediately (or falls back to its own stored location when the
 * NPC was deleted). The callback is supplied by the wiring and re-renders only the linked holograms; this class
 * owns no rendering itself.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code positions} is a {@link ConcurrentHashMap} mutated per event and
 * read per locate; an event is delivered on whatever thread published it, so the map must be thread-safe.
 */
@NullMarked
public final class EventDrivenNpcLocator implements LinkedNpcLocator, Consumer<DomainEvent> {

    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private final Consumer<String> reanchor;

    /**
     * Seed the index from {@code repository} and route re-anchors to {@code reanchor}. The repository is read once
     * here (warm after the npc module's own load) and never again: every later change rides the event bus.
     */
    public EventDrivenNpcLocator(NpcRepository repository, Consumer<String> reanchor) {
        Objects.requireNonNull(repository, "repository");
        this.reanchor = Objects.requireNonNull(reanchor, "reanchor");
        for (Npc npc : repository.all()) {
            positions.put(npc.name().value(), npc.location());
        }
    }

    @Override
    public Optional<Position> locate(String npcName) {
        Objects.requireNonNull(npcName, "npcName");
        return Optional.ofNullable(positions.get(npcName));
    }

    @Override
    public void accept(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof NpcCreated created) {
            positions.put(created.name().value(), created.location());
        } else if (event instanceof NpcMoved moved) {
            positions.put(moved.name().value(), moved.to());
            reanchor.accept(moved.name().value());
        } else if (event instanceof NpcDeleted deleted) {
            positions.remove(deleted.name().value());
            reanchor.accept(deleted.name().value());
        }
    }
}
