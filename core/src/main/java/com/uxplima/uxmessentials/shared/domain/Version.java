package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A parsed semantic version, {@code major.minor.patch}, for comparing the running plugin version against the
 * latest released one the update checker fetches.
 *
 * <p>Parsing is lenient by design: a leading {@code v}/{@code V} is stripped (GitHub release tags ship as
 * {@code v2.7.0}), a {@code -SNAPSHOT} / pre-release suffix and any {@code +build} metadata are ignored (only the
 * numeric core orders releases), and a missing minor or patch defaults to zero ({@code 2} parses as
 * {@code 2.0.0}). Anything that does not start with a numeric major is rejected as {@link Optional#empty()} rather
 * than guessed at, so a malformed or HTML error body never compares as "newer" and never triggers a false update
 * notice.
 *
 * <p>This is a pure kernel value object with no Bukkit, HTTP, or i18n dependency: the HTTP fetch and the operator
 * notice live in the adapter, the version arithmetic lives here and is unit-tested in isolation.
 *
 * @param major the major component (breaking changes)
 * @param minor the minor component (backwards-compatible additions)
 * @param patch the patch component (fixes)
 */
public record Version(int major, int minor, int patch) implements Comparable<Version> {

    public Version {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components must be non-negative");
        }
    }

    /**
     * Parse {@code raw} into a {@link Version}, returning {@link Optional#empty()} when it carries no numeric
     * major component. A leading {@code v}/{@code V} and any {@code -}/{@code +} suffix are stripped before the
     * dotted numeric core is read; a missing minor or patch reads as zero.
     */
    public static Optional<Version> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.strip();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        String core = stripSuffix(trimmed);
        if (core.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = core.split("\\.", -1);
        return component(parts, 0)
                .flatMap(maj ->
                        component(parts, 1).flatMap(min -> component(parts, 2).map(pat -> new Version(maj, min, pat))));
    }

    private static String stripSuffix(String value) {
        int cut = value.length();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '-' || c == '+') {
                cut = i;
                break;
            }
        }
        return value.substring(0, cut);
    }

    /** The component at {@code index}, defaulting to {@code 0} when absent; empty when present but non-numeric. */
    private static Optional<Integer> component(String[] parts, int index) {
        if (index >= parts.length) {
            return Optional.of(0);
        }
        String part = parts[index];
        if (part.isEmpty()) {
            return index == 0 ? Optional.empty() : Optional.of(0);
        }
        if (!isAllDigits(part)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(part));
        } catch (NumberFormatException overflow) {
            return Optional.empty();
        }
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether this version is strictly newer than {@code other} (major, then minor, then patch). */
    public boolean isNewerThan(Version other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version other) {
        Objects.requireNonNull(other, "other");
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    /** The canonical {@code major.minor.patch} rendering. */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
