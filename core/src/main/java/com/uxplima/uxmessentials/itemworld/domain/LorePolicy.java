package com.uxplima.uxmessentials.itemworld.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pure line-editing rules behind {@code /itemedit lore}: append, replace, insert, remove and clear operations
 * over an abstract {@code List<String>} of lore lines, with the index-bounds checking owned here rather than at the
 * Bukkit boundary. Each line is an opaque source string (a MiniMessage line in the adapter's use), so the policy
 * knows nothing of {@code ItemMeta} or {@code Component}. The adapter reads the held item's current lore into a
 * string list, applies one operation here, and writes the result back onto the meta.
 *
 * <p>Line numbers are <em>1-based</em>, matching how a player counts lore lines in the {@code /itemedit lore set 3
 * …} form: {@link #set} and {@link #remove} accept {@code 1..size}, and {@link #insert} accepts {@code 1..size+1}
 * (inserting at {@code size+1} appends). An out-of-range line number is a first-class, expected outcome, the
 * operation returns {@link Optional#empty()} (the adapter renders {@link ItemworldMessageKey#ITEMEDIT_LORE_OUT_OF_RANGE})
 * rather than throwing. Every returned list is a fresh immutable copy; the input list is never mutated.
 */
public final class LorePolicy {

    private LorePolicy() {}

    /** Append {@code line} to the end of the lore. Always succeeds. */
    public static List<String> add(List<String> lore, String line) {
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(line, "line");
        List<String> next = new ArrayList<>(lore);
        next.add(line);
        return List.copyOf(next);
    }

    /** The empty lore, for {@code /itemedit lore clear}. */
    public static List<String> clear() {
        return List.of();
    }

    /**
     * Replace the line at {@code lineNumber} (1-based) with {@code line}. Present when {@code 1 <= lineNumber <=
     * size}; empty for an out-of-range line number.
     */
    public static Optional<List<String>> set(List<String> lore, int lineNumber, String line) {
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(line, "line");
        if (lineNumber < 1 || lineNumber > lore.size()) {
            return Optional.empty();
        }
        List<String> next = new ArrayList<>(lore);
        next.set(lineNumber - 1, line);
        return Optional.of(List.copyOf(next));
    }

    /**
     * Insert {@code line} so it becomes line {@code lineNumber} (1-based), shifting the rest down. Present when
     * {@code 1 <= lineNumber <= size + 1} (inserting at {@code size + 1} appends); empty otherwise.
     */
    public static Optional<List<String>> insert(List<String> lore, int lineNumber, String line) {
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(line, "line");
        if (lineNumber < 1 || lineNumber > lore.size() + 1) {
            return Optional.empty();
        }
        List<String> next = new ArrayList<>(lore);
        next.add(lineNumber - 1, line);
        return Optional.of(List.copyOf(next));
    }

    /**
     * Remove the line at {@code lineNumber} (1-based). Present when {@code 1 <= lineNumber <= size}; empty for an
     * out-of-range line number.
     */
    public static Optional<List<String>> remove(List<String> lore, int lineNumber) {
        Objects.requireNonNull(lore, "lore");
        if (lineNumber < 1 || lineNumber > lore.size()) {
            return Optional.empty();
        }
        List<String> next = new ArrayList<>(lore);
        next.remove(lineNumber - 1);
        return Optional.of(List.copyOf(next));
    }
}
