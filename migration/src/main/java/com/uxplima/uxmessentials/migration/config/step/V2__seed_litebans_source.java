package com.uxplima.uxmessentials.migration.config.step;

import com.uxplima.uxmessentials.migration.config.ConfigStep;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * The second config-version step for {@code migration.conf}: seeds the {@code litebans} connection subtree the
 * JDBC LiteBans source reads (docs/12-migration §10.1). Like {@link V1__seed_migration_defaults} it is a pure,
 * idempotent node transform (each key is set only when absent ({@code node.virtual()})) so re-running on an
 * already-seeded config rewrites nothing. An empty {@code jdbc-url} is the on-disk default: the source then
 * falls back to discovering an H2 file under {@code plugins/LiteBans/}.
 */
@NullMarked
public final class V2__seed_litebans_source implements ConfigStep {

    @Override
    public int targetVersion() {
        return 2;
    }

    @Override
    public void apply(ConfigurationNode root) {
        ConfigurationNode litebans = root.node("litebans");
        setIfAbsent(litebans.node("jdbc-url"), "");
        setIfAbsent(litebans.node("username"), "");
        setIfAbsent(litebans.node("password"), "");
        setIfAbsent(litebans.node("table-prefix"), "litebans_");
    }

    private static void setIfAbsent(ConfigurationNode node, String value) {
        if (node.virtual()) {
            trySet(node, value);
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
