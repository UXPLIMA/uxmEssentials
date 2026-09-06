package com.uxplima.uxmessentials.kits.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One operator-authored condition a player must satisfy before they may claim a kit, modelled as two opaque
 * operands and the {@link RequirementOperator} between them. The operands are raw strings exactly as written
 * in the kit's config. Typically carrying {@code %placeholder%} tokens ({@code "%player_level%"},
 * {@code "10"}), and the kernel never resolves or inspects them, mirroring how {@link KitItem#data()} carries
 * an opaque serialized item. Resolving the placeholders and comparing the resolved values is the job of the
 * {@code RequirementEvaluator} port the adapter implements over PlaceholderAPI, so the kits context keeps zero
 * dependency on any placeholder engine.
 *
 * <p>A requirement is a value object: two requirements are equal when their operands and operator match, which
 * is what lets a kit definition compare equal across a config reload. Both operands must be non-blank, a
 * condition with an empty side is malformed and is rejected at construction so it never reaches the gate.
 *
 * @param left the left operand, an opaque raw string (often a {@code %placeholder%})
 * @param operator the comparison applied between the resolved operands
 * @param right the right operand, an opaque raw string (often a literal or a {@code %placeholder%})
 */
public record KitRequirement(String left, RequirementOperator operator, String right) {

    public KitRequirement {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(right, "right");
        left = left.strip();
        right = right.strip();
        if (left.isBlank()) {
            throw new IllegalArgumentException("kit requirement left operand must not be blank");
        }
        if (right.isBlank()) {
            throw new IllegalArgumentException("kit requirement right operand must not be blank");
        }
    }

    /**
     * Parse a single {@code requirements} entry, {@code "%player_level% >= 10"}, into a requirement, splitting
     * it on the first operator token found, or empty when no recognised operator appears or either side is
     * blank. The two-character tokens ({@code >= <= == !=}) are tried before the single-character ones so a
     * {@code >=} is never mistaken for a {@code >}. Spacing around the operator is tolerated. A malformed entry
     * returns empty so the codec can skip it without failing the whole kit.
     */
    public static Optional<KitRequirement> parse(String entry) {
        Objects.requireNonNull(entry, "entry");
        String text = entry.strip();
        for (int i = 1; i < text.length() - 1; i++) {
            String token = tokenAt(text, i);
            if (token == null) {
                continue;
            }
            String left = text.substring(0, i).strip();
            String right = text.substring(i + token.length()).strip();
            Optional<RequirementOperator> op = RequirementOperator.parse(token);
            if (op.isEmpty() || left.isBlank() || right.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new KitRequirement(left, op.get(), right));
        }
        return Optional.empty();
    }

    // The operator token starting at index i, longest-match-first so ">=" wins over ">"; null when none.
    @org.jspecify.annotations.Nullable private static String tokenAt(String text, int i) {
        for (String token : TWO_CHAR_TOKENS) {
            if (text.regionMatches(i, token, 0, token.length())) {
                return token;
            }
        }
        char c = text.charAt(i);
        return (c == '>' || c == '<') ? String.valueOf(c) : null;
    }

    // Two-character tokens are matched before single-character ones so ">=" never mis-splits as ">".
    private static final String[] TWO_CHAR_TOKENS = {">=", "<=", "==", "!="};

    /** This requirement rendered back to its config form: {@code <left> <token> <right>}. */
    public String asText() {
        return left + " " + operator.token() + " " + right;
    }
}
