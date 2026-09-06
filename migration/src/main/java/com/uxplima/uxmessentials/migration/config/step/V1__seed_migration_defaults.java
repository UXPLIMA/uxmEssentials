package com.uxplima.uxmessentials.migration.config.step;

import java.util.List;

import com.uxplima.uxmessentials.migration.config.ConfigStep;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * The first config-version step for {@code migration.conf}: seeds the source path and conflict knobs that
 * the importer reads (docs/12-migration §10, §12). A pure node transform. Each key is set only when absent
 * ({@code node.virtual()}), so re-running on an already-seeded config rewrites nothing. Naming mirrors the
 * Flyway DB-migration convention ({@code V1__…}) so config and schema evolution read identically.
 */
@NullMarked
public final class V1__seed_migration_defaults implements ConfigStep {

    @Override
    public int targetVersion() {
        return 1;
    }

    @Override
    public void apply(ConfigurationNode root) {
        setIfAbsent(root.node("source-path"), "Essentials");
        setIfAbsent(root.node("on-conflict"), "skip");
        setIfAbsent(root.node("balance-policy"), "skip-if-present");
        seedList(root.node("seed-back-location"));
    }

    private static void setIfAbsent(ConfigurationNode node, String value) {
        if (node.virtual()) {
            trySet(node, value);
        }
    }

    private static void seedList(ConfigurationNode node) {
        if (node.virtual()) {
            try {
                node.setList(String.class, List.of());
            } catch (SerializationException ignored) {
                // A serialization failure on an empty default list cannot occur for String; defensive only.
                node.raw(List.of());
            }
        }
    }

    private static void trySet(ConfigurationNode node, String value) {
        try {
            node.set(value);
        } catch (SerializationException ignored) {
            // String always serialises; the raw fallback keeps the step total without a checked throw.
            node.raw(value);
        }
    }
}
