package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;

/**
 * A single requirement inside a {@link RequirementSpec}: a condition {@link Ref} the runtime evaluates, whether that
 * outcome is negated, whether it is optional, and the per-requirement action lists to run on its own pass or failure.
 * A condition may carry a value ({@code has-money:100}), so the ref keeps its args; the {@code inverted} flag comes
 * from a leading {@code !} in the config token, letting an author gate on the absence of something
 * ({@code !has-empty-slots:1} passes exactly when the inventory is full).
 *
 * <p>{@code optional} decides how this requirement's failure counts toward the block: an optional requirement that
 * fails does not on its own block the gesture (with the default {@code minimum}), yet it still runs its own
 * {@code deny} actions, so an author can react to a missing perk without turning it into a hard gate. {@code success}
 * are the actions the runtime fires when this requirement passes, and {@code deny} the actions it fires when this
 * requirement fails; both run regardless of whether the block as a whole ends up passing, so they express
 * per-requirement side effects (a message, a sound) independent of the click's own actions.
 *
 * <p>Pure by design: this holds only the parsed intent. Evaluating the condition and running the actions need the
 * runtime's registries, so both happen there: this record stays Bukkit-free for plain-JUnit testing.
 */
public record Requirement(Ref condition, boolean inverted, boolean optional, List<Ref> success, List<Ref> deny) {

    /**
     * The historic two-argument form. It forwards to the canonical constructor as a mandatory requirement with no
     * per-requirement actions, so every existing {@code new Requirement(condition, inverted)} call-site keeps
     * compiling unchanged. Only the loader's map form reaches for the {@code optional}/{@code success}/{@code deny}
     * fields.
     */
    public Requirement(Ref condition, boolean inverted) {
        this(condition, inverted, false, List.of(), List.of());
    }

    public Requirement {
        Objects.requireNonNull(condition, "condition");
        success = List.copyOf(Objects.requireNonNull(success, "success"));
        deny = List.copyOf(Objects.requireNonNull(deny, "deny"));
    }
}
