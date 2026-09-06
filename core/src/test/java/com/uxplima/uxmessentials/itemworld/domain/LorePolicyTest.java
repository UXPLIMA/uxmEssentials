package com.uxplima.uxmessentials.itemworld.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for the pure {@code /itemedit lore} line rules, the append/replace/insert/remove/clear
 * operations and their 1-based index-bounds checks. None of this touches Bukkit: {@link LorePolicy} reasons only
 * over an abstract {@code List<String>} of lore lines, so the adapter can lean on it having got the bounds right
 * before it ever rewrites an {@code ItemMeta}. Every operation returns a fresh copy and leaves the input untouched.
 */
class LorePolicyTest {

    @Test
    void addAppendsToTheEnd() {
        assertThat(LorePolicy.add(List.of("a", "b"), "c")).containsExactly("a", "b", "c");
        assertThat(LorePolicy.add(List.of(), "first")).containsExactly("first");
    }

    @Test
    void clearYieldsAnEmptyLore() {
        assertThat(LorePolicy.clear()).isEmpty();
    }

    @Test
    void setReplacesTheLineAtAOneBasedIndex() {
        assertThat(LorePolicy.set(List.of("a", "b", "c"), 2, "X")).contains(List.of("a", "X", "c"));
        assertThat(LorePolicy.set(List.of("a"), 1, "only")).contains(List.of("only"));
    }

    @Test
    void setRejectsAnOutOfRangeLineNumber() {
        assertThat(LorePolicy.set(List.of("a", "b"), 0, "X")).isEmpty();
        assertThat(LorePolicy.set(List.of("a", "b"), 3, "X")).isEmpty();
        assertThat(LorePolicy.set(List.of(), 1, "X")).isEmpty();
    }

    @Test
    void insertShiftsTheRestDownAndAppendsAtSizePlusOne() {
        assertThat(LorePolicy.insert(List.of("a", "b"), 1, "X")).contains(List.of("X", "a", "b"));
        assertThat(LorePolicy.insert(List.of("a", "b"), 2, "X")).contains(List.of("a", "X", "b"));
        assertThat(LorePolicy.insert(List.of("a", "b"), 3, "X")).contains(List.of("a", "b", "X"));
        assertThat(LorePolicy.insert(List.of(), 1, "X")).contains(List.of("X"));
    }

    @Test
    void insertRejectsAnOutOfRangeLineNumber() {
        assertThat(LorePolicy.insert(List.of("a", "b"), 0, "X")).isEmpty();
        assertThat(LorePolicy.insert(List.of("a", "b"), 4, "X")).isEmpty();
    }

    @Test
    void removeDropsTheLineAtAOneBasedIndex() {
        assertThat(LorePolicy.remove(List.of("a", "b", "c"), 2)).contains(List.of("a", "c"));
        assertThat(LorePolicy.remove(List.of("a"), 1)).contains(List.of());
    }

    @Test
    void removeRejectsAnOutOfRangeLineNumber() {
        assertThat(LorePolicy.remove(List.of("a", "b"), 0)).isEmpty();
        assertThat(LorePolicy.remove(List.of("a", "b"), 3)).isEmpty();
        assertThat(LorePolicy.remove(List.of(), 1)).isEmpty();
    }

    @Test
    void operationsDoNotMutateTheInputList() {
        List<String> original = List.of("a", "b");
        LorePolicy.add(original, "c");
        LorePolicy.set(original, 1, "X");
        LorePolicy.insert(original, 1, "X");
        LorePolicy.remove(original, 1);
        assertThat(original).containsExactly("a", "b");
    }
}
