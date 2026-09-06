package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A block of {@link Requirement}s bound to a click gesture, plus how many of them must pass and what to run when the
 * block fails. It is the pure model behind the config grammar {@code left { click = [...], requirements = [...],
 * minimum = N, deny = [...] }}: the {@code requirements} are the gates, {@code minimum} is how many must hold, and
 * {@code deny} is the action list the runtime fires instead of the click's own actions when the gate fails.
 *
 * <p>The {@link #minimum} yields the three common combinators without a separate flag:
 * <ul>
 *   <li>{@code minimum <= 0} or {@code minimum >= requirements.size()}. ALL must pass (AND).</li>
 *   <li>{@code minimum == 1}. ANY may pass (OR).</li>
 *   <li>otherwise. At least N of the M must pass (N-of-M).</li>
 * </ul>
 * {@link #effectiveMinimum()} folds those rules into the concrete count the runtime compares its pass tally against,
 * so the runtime carries no combinator logic of its own.
 *
 * <p>{@code stopAtSuccess} short-circuits evaluation once the {@link #minimum} is met: the runtime stops evaluating
 * further requirements (and stops running their per-requirement actions) as soon as enough have passed. It only means
 * anything with a positive {@code minimum} (with the default AND minimum every requirement must be looked at anyway)
 * and it defaults to {@code false}, so a block that does not ask for it evaluates every requirement exactly as before.
 *
 * <p>Pure by design: which requirements actually pass is decided by the caller (it holds the condition registry);
 * this record only says how they combine, so it stays Bukkit-free for plain-JUnit testing. {@link #NONE} is the empty
 * block that always passes and denies nothing, the value a gesture with no requirement block resolves to.
 *
 * <p>The same block also backs an item's {@code view} visibility gate: {@link #allOf(List)} lifts a flat list of
 * condition refs into an all-mandatory AND block (the historic view grammar), and {@link #passes(Predicate)} is the
 * pure yes/no a view gate needs, so an item can now gate visibility on a minimum (OR / N-of-M) or an inverted
 * condition without any new model, the click path and the view path share one requirement block.
 */
public record RequirementSpec(List<Requirement> requirements, int minimum, List<Ref> deny, boolean stopAtSuccess) {

    /** The empty block: no requirements (so it always passes), no deny actions, and no short-circuit. */
    public static final RequirementSpec NONE = new RequirementSpec(List.of(), 0, List.of());

    /**
     * The historic three-argument form. It forwards to the canonical constructor with {@code stopAtSuccess} off, so
     * every existing {@code new RequirementSpec(requirements, minimum, deny)} call-site keeps compiling unchanged
     * only the loader reaches for the four-argument form to opt a block into short-circuit evaluation.
     */
    public RequirementSpec(List<Requirement> requirements, int minimum, List<Ref> deny) {
        this(requirements, minimum, deny, false);
    }

    public RequirementSpec {
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        deny = List.copyOf(Objects.requireNonNull(deny, "deny"));
    }

    /**
     * How many requirements must pass for this block to pass, resolved from the {@link #minimum} rules: a
     * non-positive minimum means all of them (AND), and a minimum larger than the block is capped at the block size
     * so an over-large N still means "all". A block with no requirements yields {@code 0}, so it always passes.
     */
    public int effectiveMinimum() {
        return minimum <= 0 ? requirements.size() : Math.min(minimum, requirements.size());
    }

    /**
     * Wrap a flat list of condition refs into an all-mandatory AND block: every ref becomes a plain, non-inverted
     * mandatory {@link Requirement}, the minimum is {@code 0} (so all of them must pass), and there are no deny
     * actions or short-circuit. This is how the historic flat item {@code view}. A bare list of conditions every one
     * of which had to hold. Becomes a requirement block with its meaning intact, so a view that was an AND list stays
     * an AND list. It is pure and Bukkit-free, like the rest of this record.
     */
    public static RequirementSpec allOf(List<Ref> refs) {
        Objects.requireNonNull(refs, "refs");
        return new RequirementSpec(
                refs.stream().map(ref -> new Requirement(ref, false)).toList(), 0, List.of());
    }

    /** A copy carrying a new {@code requirements} list: the requirement editor's add / edit / remove / reorder funnel. */
    public RequirementSpec withRequirements(List<Requirement> requirements) {
        return new RequirementSpec(requirements, minimum, deny, stopAtSuccess);
    }

    /** A copy with a new {@code minimum} (the AND / OR / N-of-M combinator), every other field unchanged. */
    public RequirementSpec withMinimum(int minimum) {
        return new RequirementSpec(requirements, minimum, deny, stopAtSuccess);
    }

    /** A copy carrying a new {@code deny} action list: what the runtime fires instead when the block fails. */
    public RequirementSpec withDeny(List<Ref> deny) {
        return new RequirementSpec(requirements, minimum, deny, stopAtSuccess);
    }

    /**
     * Whether this block passes, given {@code holds}. The caller's decision on which individual requirements hold
     * right now (the caller owns the condition registry, so it evaluates each requirement's condition and hands the
     * outcome back here). The pass rule mirrors the click runtime's {@code evaluateRequirements} exactly, minus its
     * per-requirement side effects: with a non-positive {@link #minimum} every <em>mandatory</em> requirement must
     * hold (an optional one failing does not block, though the caller may still react to it) and with a positive
     * minimum at least {@link #effectiveMinimum()} of all requirements (optional included) must hold. A block with no
     * requirements passes. A view gate uses this: visibility is a pure yes/no with no deny or success actions, so the
     * boolean is all it needs.
     */
    public boolean passes(Predicate<Requirement> holds) {
        Objects.requireNonNull(holds, "holds");
        int passes = 0;
        boolean mandatoryFail = false;
        for (Requirement requirement : requirements) {
            if (holds.test(requirement)) {
                passes++;
            } else if (!requirement.optional()) {
                mandatoryFail = true;
            }
        }
        return minimum <= 0 ? !mandatoryFail : passes >= effectiveMinimum();
    }
}
