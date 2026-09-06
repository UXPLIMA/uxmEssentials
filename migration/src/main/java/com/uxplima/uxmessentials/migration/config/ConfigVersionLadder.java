package com.uxplima.uxmessentials.migration.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Upgrades one of our own {@code .conf} files from its on-disk {@code config-version} to the current
 * target by applying numbered {@link ConfigStep}s in order (docs/12-migration §12). This is a separate,
 * always-on concern from the foreign-source importer: the importer moves a foreign plugin's data in once;
 * this ladder evolves our own config forward on every upgrade. The two share one principle, numbered,
 * idempotent, tracked, never destructive.
 *
 * <p>Idempotent: a step whose version is already passed is skipped, and a no-op upgrade rewrites nothing.
 * Tracked: the {@code config-version} key in the file <em>is</em> the version table; there is no
 * out-of-band state. The steps are sorted ascending by {@code targetVersion()} and validated contiguous
 * (no gaps, no duplicates) at construction, so a missing or doubled number is a build/test failure, not a
 * runtime surprise.
 */
@NullMarked
public final class ConfigVersionLadder {

    private final List<ConfigStep> steps;

    public ConfigVersionLadder(List<ConfigStep> steps) {
        Objects.requireNonNull(steps, "steps");
        List<ConfigStep> sorted = new ArrayList<>(steps);
        sorted.sort(Comparator.comparingInt(ConfigStep::targetVersion));
        requireContiguous(sorted);
        this.steps = List.copyOf(sorted);
    }

    /** The highest target version this ladder reaches: a fresh config ships already at this version. */
    public int targetVersion() {
        return steps.isEmpty() ? 0 : steps.get(steps.size() - 1).targetVersion();
    }

    /** Apply every step strictly greater than the file's current {@code config-version}, advancing it. */
    public MigrationResult upgrade(ConfigurationNode root) {
        Objects.requireNonNull(root, "root");
        int from = root.node("config-version").getInt(0);
        int to = from;
        for (ConfigStep step : steps) {
            if (step.targetVersion() <= from) {
                continue;
            }
            step.apply(root);
            to = step.targetVersion();
            setVersion(root, to);
        }
        return new MigrationResult(from, to);
    }

    private static void setVersion(ConfigurationNode root, int version) {
        try {
            root.node("config-version").set(version);
        } catch (org.spongepowered.configurate.serialize.SerializationException failure) {
            throw new UncheckedConfigException("failed to set config-version", failure);
        }
    }

    private static void requireContiguous(List<ConfigStep> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            int expected = i + 1;
            if (sorted.get(i).targetVersion() != expected) {
                throw new IllegalArgumentException(
                        "config steps must be contiguous from 1 with no gaps or duplicates; expected version "
                                + expected + " but found " + sorted.get(i).targetVersion());
            }
        }
    }

    /** Unchecked wrapper for a Configurate serialization failure, so the ladder stays exception-clean. */
    public static final class UncheckedConfigException extends RuntimeException {
        UncheckedConfigException(String message, IOException cause) {
            super(message, cause);
        }
    }
}
