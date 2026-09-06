package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable identifier for a {@link FeatureModule}: {@code "homes"}, {@code "economy"}, and so on.
 *
 * <p>The value is the single source of truth for the module's config subtree
 * ({@code modules.<id>}), its {@code uxmessentials.module.<id>} permission node, the
 * {@code /uxmess reload <id>} argument, and {@code module=<id>} audit attribution. It must equal
 * the bounded-context package name under {@code core/.../uxmessentials/<id>/}, so the value is
 * constrained to the lowercase, no-spaces shape a Java package segment allows.
 */
public record ModuleId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*");

    public ModuleId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "module id must be lowercase letters/digits, starting with a letter: " + value);
        }
    }

    /** Factory mirroring the canonical constructor, for readability at call sites. */
    public static ModuleId of(String value) {
        return new ModuleId(value);
    }

    /** The config subtree this module owns: {@code modules.<id>}. */
    public String configRoot() {
        return "modules." + value;
    }

    /** The per-module operator permission node: {@code uxmessentials.module.<id>}. */
    public String permissionNode() {
        return "uxmessentials.module." + value;
    }

    @Override
    public String toString() {
        return value;
    }
}
