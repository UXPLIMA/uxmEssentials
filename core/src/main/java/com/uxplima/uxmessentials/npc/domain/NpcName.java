package com.uxplima.uxmessentials.npc.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * An NPC's name, normalised to its canonical lowercase form so uniqueness and lookup are case-insensitive
 * {@code /npc create Guide} and {@code /npc delete guide} address the same NPC.
 *
 * <p>The name is the NPC's identity: NPCs are server-wide, so a name is unique across the whole {@code npc}
 * table, and it doubles as the seed for the NPC's deterministic profile uuid in the renderer (so the skin
 * always links to the same fake-player entry). Blank names and names longer than the column width are rejected
 * at construction, so an invalid name can never reach the aggregate or the repository.
 *
 * @param value the canonical lowercase name
 */
public record NpcName(String value) {

    /** Mirrors the {@code npc.name} column width in the V38 migration. */
    public static final int MAX_LENGTH = 64;

    public NpcName {
        Objects.requireNonNull(value, "value");
    }

    /** Build a name from raw player input, trimming and lower-casing it; rejects blank or overlong input. */
    public static NpcName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("npc name must not be blank");
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("npc name must be at most " + MAX_LENGTH + " characters");
        }
        return new NpcName(normalised);
    }

    @Override
    public String toString() {
        return value;
    }
}
