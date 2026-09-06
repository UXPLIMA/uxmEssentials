package com.uxplima.uxmessentials.ranks.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The kinds of condition a rank may gate its rankup on. Each constant carries the keyword an operator writes as
 * the first token of a {@code requirements} entry in {@code ranks.conf} ({@code money 1000},
 * {@code playtime 3600}); {@link #parse(String)} maps a raw keyword back to the constant so the codec never
 * branches on the literal itself. The domain only names the kinds and the keyword grammar. How each one is
 * resolved for a player (a balance read, a playtime lookup, a permission check, the stored rank, a placeholder
 * comparison, an inventory count) is the evaluator adapter's job, keeping the kernel free of any Bukkit,
 * economy or placeholder dependency.
 */
public enum RankRequirementType {

    /** The player must be able to afford the value's amount in the default currency (a balance check, not a charge). */
    MONEY("money"),

    /** The player's tracked playtime must be at least the value's whole-second count. */
    PLAYTIME("playtime"),

    /** The player must hold the value's permission node. */
    PERMISSION("permission"),

    /** The player's current stored rank must be the value's rank id (the previous rung, for a linear ladder). */
    PREVIOUS_RANK("previous-rank"),

    /** A {@code <left> <op> <right>} comparison whose operands may embed {@code %placeholder%} tokens. */
    PLACEHOLDER("placeholder"),

    /** The player's inventory must hold the value's {@code <material> [amount]} (default amount one). */
    ITEM("item");

    private final String keyword;

    RankRequirementType(String keyword) {
        this.keyword = keyword;
    }

    /** The keyword this type is written as at the head of a {@code requirements} entry. */
    public String keyword() {
        return keyword;
    }

    /**
     * Parse a raw keyword into its type, tolerating surrounding spaces, case, and the {@code prev-rank} alias for
     * {@link #PREVIOUS_RANK}; empty when the keyword names no known requirement kind (so an operator's typo or a
     * requirement kind a later phase adds is simply not enforced rather than failing the whole ladder).
     */
    public static Optional<RankRequirementType> parse(String keyword) {
        Objects.requireNonNull(keyword, "keyword");
        String normalized = keyword.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("prev-rank") || normalized.equals("prevrank")) {
            return Optional.of(PREVIOUS_RANK);
        }
        for (RankRequirementType type : values()) {
            if (type.keyword.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
