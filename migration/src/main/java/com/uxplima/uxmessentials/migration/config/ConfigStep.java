package com.uxplima.uxmessentials.migration.config;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * One numbered step in the config-version migration ladder (docs/12-migration §12), the analogue of a
 * Flyway DB migration for our own {@code .conf} files. A step transforms a config node tree from the
 * version immediately before {@link #targetVersion()} to that version. Steps are additive/transform only:
 * they add keys, rename keys (copy-then-remove), and re-shape blocks, and each individual mutation guards
 * itself ({@code if (node.virtual())}) so even a partial earlier run finishes cleanly. A step never drops
 * a key whose value an operator might still want without first migrating it somewhere.
 */
@NullMarked
public interface ConfigStep {

    /** The config version this step upgrades the file to; its single source of ordering. */
    int targetVersion();

    /** Apply the transform to {@code root}. Idempotent: re-applying on an already-upgraded tree is a no-op. */
    void apply(ConfigurationNode root);
}
