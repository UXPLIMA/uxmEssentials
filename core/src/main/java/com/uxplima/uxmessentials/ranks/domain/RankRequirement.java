package com.uxplima.uxmessentials.ranks.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One operator-authored condition a player must satisfy to rank up, modelled as a {@link RankRequirementType} and
 * the raw {@code value} that type interprets. The value is a plain string exactly as written after the keyword in
 * a rank's {@code requirements} entry ({@code "1000"} for {@code money 1000}, {@code "%player_level% >= 10"} for a
 * placeholder comparison). The kernel never resolves or inspects it, mirroring how {@link Rank} carries its
 * still-opaque action strings. Resolving the value against a live player is the {@code RankRequirementEvaluator}
 * port's job, so the ranks context keeps zero dependency on any economy, playtime or placeholder engine.
 *
 * <p>A requirement is a value object: two are equal when their type and value match. The value must be non-blank
 *. A requirement keyword with nothing after it is malformed and is rejected at construction so it never reaches
 * the evaluator.
 *
 * @param type what kind of condition this is
 * @param value the type-specific payload, stored as given (never blank)
 */
public record RankRequirement(RankRequirementType type, String value) {

    public RankRequirement {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("rank requirement value must not be blank");
        }
    }

    /**
     * Parse a single {@code requirements} entry ({@code "money 1000"}, {@code "playtime 3600"}) into a typed
     * requirement, splitting it into the leading keyword and the remaining value. Empty when the keyword names no
     * known {@link RankRequirementType} or nothing follows it, so the ladder codec skips an unrecognised or
     * value-less entry rather than failing the whole rank (an unknown requirement is simply not enforced).
     */
    public static Optional<RankRequirement> parse(String entry) {
        Objects.requireNonNull(entry, "entry");
        String text = entry.strip();
        int split = indexOfFirstSpace(text);
        if (split < 0) {
            return Optional.empty();
        }
        String keyword = text.substring(0, split);
        String value = text.substring(split + 1).strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return RankRequirementType.parse(keyword).map(type -> new RankRequirement(type, value));
    }

    private static int indexOfFirstSpace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
