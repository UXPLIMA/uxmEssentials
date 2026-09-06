package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One node in a click gesture's else-chain: a fallback branch tried when the branch before it failed. A branch is a
 * gate ({@link #requirement}), the {@link #actions} to run when that gate passes, and the {@link #orElse} branch tried
 * next when it fails. Together they form the if / else-if / else ladder for a click: the runtime walks the chain, and
 * the first branch whose requirement passes runs its actions and stops.
 *
 * <p>The two terminal shapes fall out of that walk. A failing branch with no {@link #orElse} has nowhere left to go,
 * so it runs its own block {@code deny} (the {@link RequirementSpec#deny()} of its gate). A final else, a branch whose
 * requirement is {@link RequirementSpec#NONE}, which always passes. Runs its actions unconditionally, so it is the
 * "otherwise" arm. The {@link #orElse} link is what makes the chain recursive: one branch nests inside another to any
 * (config-bounded) depth.
 *
 * <p>Pure by design: which requirement passes is decided by the runtime, which owns the condition registry, so this
 * record carries only the parsed intent and stays Bukkit-free for plain-JUnit testing, like the rest of the spec model
 * it feeds ({@link RequirementSpec}, {@link Requirement}, {@link Ref}).
 */
public record ClickBranch(RequirementSpec requirement, List<Ref> actions, Optional<ClickBranch> orElse) {

    public ClickBranch {
        Objects.requireNonNull(requirement, "requirement");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        Objects.requireNonNull(orElse, "orElse");
    }
}
