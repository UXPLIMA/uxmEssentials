package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * Per-player history of the custom menus each viewer has opened, so {@code /menu last} can reopen the current one and
 * a {@code back} button can step to the previous one. Only a subject-less menu, a disk-loaded custom menu, is ever
 * recorded; a feature menu carries a live domain subject that must not be reopened blind, and it has its own command,
 * so the engine deliberately never remembers one here.
 *
 * <p>Each player owns a bounded stack, newest on top. {@link #record} pushes an open onto it, but a consecutive
 * identical open (the same menu/page/arguments. A refresh, or reopening the window already on top) is de-duplicated
 * rather than stacked, and the stack is capped at {@link #MAX_DEPTH}, evicting the oldest entry so a player who wanders
 * through many menus cannot grow it without bound. {@link #get} peeks the top (the current open): what {@code /menu
 * last} reopens. {@link #back} pops the top and returns the new top (the previous open), or empty when nothing remains
 * below; the previous then becomes the current, so a further {@code back} steps further. {@link #clear} drops a
 * player's whole history on quit so the map never retains an offline player.
 *
 * <p>Bukkit-free (only {@code java.util}) and thread-safe: the backing map is a {@link ConcurrentHashMap} and every
 * read and mutation of a player's deque runs inside a {@code compute}/{@code computeIfPresent} remapping, which the
 * map executes atomically per key. Because {@link ArrayDeque} is not itself thread-safe, routing every access through
 * that per-key lock is what keeps a {@code record} on the viewer's entity thread and a {@code clear} on the quit
 * thread from corrupting the same deque.
 */
@NullMarked
public final class LastMenu {

    /** The deepest a single player's history grows; the oldest open past this is evicted on the next {@link #record}. */
    private static final int MAX_DEPTH = 32;

    /**
     * One remembered open: the menu id, the page it was shown on, and the typed command arguments it carried.
     * Immutable. The arguments are defensively copied so a later mutation of the caller's map cannot rewrite a
     * player's remembered open. Value equality (records) is what lets {@link #record} recognise a consecutive
     * identical open and skip stacking a duplicate.
     */
    public record LastOpen(String menuId, int page, Map<String, String> arguments) {
        public LastOpen {
            Objects.requireNonNull(menuId, "menuId");
            arguments = Map.copyOf(arguments);
        }
    }

    private final Map<UUID, Deque<LastOpen>> byPlayer = new ConcurrentHashMap<>();

    /**
     * Push {@code open} onto {@code player}'s history, unless it equals the current top (a refresh, or reopening the
     * window already showing), in which case the history is left untouched. Pushing past {@link #MAX_DEPTH} evicts the
     * oldest open at the bottom of the stack.
     */
    public void record(UUID player, LastOpen open) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(open, "open");
        byPlayer.compute(player, (id, existing) -> {
            Deque<LastOpen> stack = existing == null ? new ArrayDeque<>() : existing;
            if (open.equals(stack.peek())) {
                return stack;
            }
            stack.push(open);
            while (stack.size() > MAX_DEPTH) {
                stack.removeLast();
            }
            return stack;
        });
    }

    /** The menu {@code player} currently has on top of their history, or empty if none has been recorded (or cleared). */
    public Optional<LastOpen> get(UUID player) {
        Objects.requireNonNull(player, "player");
        List<LastOpen> top = new ArrayList<>(1);
        byPlayer.computeIfPresent(player, (id, stack) -> {
            LastOpen peek = stack.peek();
            if (peek != null) {
                top.add(peek);
            }
            return stack;
        });
        return top.isEmpty() ? Optional.empty() : Optional.of(top.getFirst());
    }

    /**
     * Step back: pop {@code player}'s current top open and return the one beneath it, the previous menu, or empty
     * when nothing remains below (the history is now exhausted). The returned previous is left on top, so it becomes
     * the current open and a further {@link #back} steps to the one before it. An emptied history drops its map entry.
     */
    public Optional<LastOpen> back(UUID player) {
        Objects.requireNonNull(player, "player");
        List<LastOpen> previous = new ArrayList<>(1);
        byPlayer.computeIfPresent(player, (id, stack) -> {
            if (!stack.isEmpty()) {
                stack.pop();
            }
            LastOpen top = stack.peek();
            if (top != null) {
                previous.add(top);
            }
            return stack.isEmpty() ? null : stack;
        });
        return previous.isEmpty() ? Optional.empty() : Optional.of(previous.getFirst());
    }

    /** Forget {@code player}'s whole history: called on quit so the map never retains an offline player. */
    public void clear(UUID player) {
        Objects.requireNonNull(player, "player");
        byPlayer.remove(player);
    }
}
