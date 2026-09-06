package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;
import java.util.function.Function;

/**
 * Platform-neutral description of one command a module contributes.
 *
 * <p>The {@code factory} is not invoked for a disabled module, so the handler, and every adapter it
 * closes over: is never constructed unless the module is enabled and started. The bukkit-adapter
 * calls the factory with the started module's {@link ModuleContext} and registers the resulting
 * {@link BrigadierCommand} on the next {@code LifecycleEvents.COMMANDS} fire.
 *
 * @param literal the command literal without a leading slash ({@code "home"})
 * @param permission the permission node guarding the command ({@code "uxmessentials.home.use"})
 * @param factory builds the realised command from the module's runtime context
 */
public record CommandSpec(String literal, String permission, Function<ModuleContext, BrigadierCommand> factory) {

    public CommandSpec {
        Objects.requireNonNull(literal, "literal");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(factory, "factory");
        if (literal.isBlank()) {
            throw new IllegalArgumentException("command literal must not be blank");
        }
        if (permission.isBlank()) {
            throw new IllegalArgumentException("command permission must not be blank");
        }
    }
}
