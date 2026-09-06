package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The actions, conditions, requirement blocks, and else-chains bound to an item's click gestures, keyed by {@link
 * ClickKind}. A gesture's effective action list is its own list followed by whatever is bound to {@link ClickKind#ANY},
 * so authors can share behaviour across every gesture without repeating it. A gesture's effective requirement block
 * ({@link #requirementFor}) merges its own block with the {@code ANY} block the same way, so a shared gate ("must have
 * money") can sit under {@code ANY} once.
 *
 * <p>A gesture may also carry an else-chain ({@link #elseFor}). The fallback branches tried when its requirement block
 * fails: try block A, else block B, and so on, nested to any depth. Unlike actions and requirements the else-chain is
 * <em>not</em> merged with {@code ANY}: a fallback ladder is a self-contained if / else-if / else structure specific to
 * one gesture, so blending it with a shared {@code ANY} chain would make the branch order ambiguous.
 */
public record ClickSpec(
        Map<ClickKind, List<Ref>> actions,
        Map<ClickKind, List<Ref>> conditions,
        Map<ClickKind, RequirementSpec> requirements,
        Map<ClickKind, ClickBranch> orElse) {

    /**
     * The historic two-argument form. It forwards to the canonical constructor with no requirement blocks and no
     * else-chains, so every existing {@code new ClickSpec(actions, conditions)} call-site keeps compiling unchanged
     * only the loader reaches for the longer forms to attach a gesture's requirements and fallbacks.
     */
    public ClickSpec(Map<ClickKind, List<Ref>> actions, Map<ClickKind, List<Ref>> conditions) {
        this(actions, conditions, Map.of());
    }

    /**
     * The three-argument form (actions, conditions, requirement blocks). It forwards to the canonical constructor with
     * no else-chains, so every existing {@code new ClickSpec(actions, conditions, requirements)} call-site keeps
     * compiling unchanged: only the loader reaches for the four-argument form to attach a gesture's else-chain.
     */
    public ClickSpec(
            Map<ClickKind, List<Ref>> actions,
            Map<ClickKind, List<Ref>> conditions,
            Map<ClickKind, RequirementSpec> requirements) {
        this(actions, conditions, requirements, Map.of());
    }

    public ClickSpec {
        actions = copyRefs(Objects.requireNonNull(actions, "actions"));
        conditions = copyRefs(Objects.requireNonNull(conditions, "conditions"));
        requirements = copyRequirements(Objects.requireNonNull(requirements, "requirements"));
        orElse = copyBranches(Objects.requireNonNull(orElse, "orElse"));
    }

    /**
     * Whether any gesture carries at least one action, the test the Bedrock form renderer uses to tell a tappable
     * button from a decorative filler. A SimpleForm button that does nothing on tap is meaningless, so an item whose
     * click binds no action anywhere (the auto-filler, a blank border pane, any display-only item) is left off the
     * form. Requirements and else-chains do not count: without an action there is nothing for a tap to run.
     */
    public boolean hasAnyAction() {
        return actions.values().stream().anyMatch(list -> !list.isEmpty());
    }

    /**
     * The actions that should fire for {@code kind}: the gesture's own list first, then the shared {@code ANY}
     * list. The result is a fresh immutable list so callers can't mutate the underlying spec.
     */
    public List<Ref> actionsFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        List<Ref> merged = new ArrayList<>(actions.getOrDefault(kind, List.of()));
        merged.addAll(actions.getOrDefault(ClickKind.ANY, List.of()));
        return List.copyOf(merged);
    }

    /**
     * The requirement block that gates {@code kind}: the gesture's own block merged with the shared {@link
     * ClickKind#ANY} block. The merge concatenates their requirements (the gesture's first, then {@code ANY}'s) and
     * their deny actions the same way (the gesture's deny runs before {@code ANY}'s); the gesture's own {@code minimum}
     * applies to the concatenated set, so a specific gesture keeps control of how its combined gate combines. When only
     * one side is set that side is returned as-is, and when neither is set the result is {@link RequirementSpec#NONE}
     * (always passes, denies nothing).
     */
    public RequirementSpec requirementFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        RequirementSpec own = requirements.get(kind);
        RequirementSpec any = requirements.get(ClickKind.ANY);
        if (own == null) {
            return any == null ? RequirementSpec.NONE : any;
        }
        if (any == null || kind == ClickKind.ANY) {
            return own;
        }
        List<Requirement> mergedReqs = new ArrayList<>(own.requirements());
        mergedReqs.addAll(any.requirements());
        List<Ref> mergedDeny = new ArrayList<>(own.deny());
        mergedDeny.addAll(any.deny());
        return new RequirementSpec(mergedReqs, own.minimum(), mergedDeny);
    }

    /**
     * The head of the else-chain the runtime tries when {@code kind}'s requirement block fails, or empty when the
     * gesture has no fallback (in which case the block's own {@code deny} runs instead). This is deliberately per-kind
     * with no {@link ClickKind#ANY} merge: an else-chain is a single ordered fallback ladder, so a {@code left}
     * gesture's chain and any {@code ANY} chain stay independent rather than being spliced into one ambiguous order.
     */
    public Optional<ClickBranch> elseFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Optional.ofNullable(orElse.get(kind));
    }

    /**
     * A copy with {@code kind}'s own action list replaced by {@code refs}, the primitive the per-gesture action editor
     * rebuilds one gesture through. An empty list drops the gesture's entry entirely (so the writer emits nothing for
     * it), keeping the map to the gestures that actually bind an action; every other gesture, the conditions, the
     * requirement blocks and the else-chains are carried through unchanged.
     */
    public ClickSpec withGestureActions(ClickKind kind, List<Ref> refs) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(refs, "refs");
        Map<ClickKind, List<Ref>> next = new EnumMap<>(ClickKind.class);
        next.putAll(actions);
        if (refs.isEmpty()) {
            next.remove(kind);
        } else {
            next.put(kind, List.copyOf(refs));
        }
        return new ClickSpec(next, conditions, requirements, orElse);
    }

    /**
     * A copy with {@code kind}'s requirement block replaced by {@code requirement}, the primitive the per-gesture
     * requirement editor rebuilds one gesture's gate through. A block that gates nothing and denies nothing drops the
     * gesture's entry entirely, so the writer emits no empty {@code requirements {}} for it; every other gesture, the
     * actions, the conditions and the else-chains are carried through unchanged.
     */
    public ClickSpec withGestureRequirement(ClickKind kind, RequirementSpec requirement) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requirement, "requirement");
        Map<ClickKind, RequirementSpec> next = new EnumMap<>(ClickKind.class);
        next.putAll(requirements);
        if (requirement.requirements().isEmpty() && requirement.deny().isEmpty()) {
            next.remove(kind);
        } else {
            next.put(kind, requirement);
        }
        return new ClickSpec(actions, conditions, next, orElse);
    }

    private static Map<ClickKind, List<Ref>> copyRefs(Map<ClickKind, List<Ref>> source) {
        Map<ClickKind, List<Ref>> copy = new EnumMap<>(ClickKind.class);
        source.forEach((kind, refs) ->
                copy.put(Objects.requireNonNull(kind, "kind"), List.copyOf(Objects.requireNonNull(refs, "refs"))));
        return Map.copyOf(copy);
    }

    private static Map<ClickKind, RequirementSpec> copyRequirements(Map<ClickKind, RequirementSpec> source) {
        Map<ClickKind, RequirementSpec> copy = new EnumMap<>(ClickKind.class);
        source.forEach((kind, spec) ->
                copy.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(spec, "requirementSpec")));
        return Map.copyOf(copy);
    }

    private static Map<ClickKind, ClickBranch> copyBranches(Map<ClickKind, ClickBranch> source) {
        Map<ClickKind, ClickBranch> copy = new EnumMap<>(ClickKind.class);
        source.forEach((kind, branch) ->
                copy.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(branch, "branch")));
        return Map.copyOf(copy);
    }
}
