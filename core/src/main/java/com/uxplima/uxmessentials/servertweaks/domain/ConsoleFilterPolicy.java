package com.uxplima.uxmessentials.servertweaks.domain;

import java.util.List;
import java.util.Objects;

/**
 * The pure decision behind the console-spam filter: given a fixed list of substrings an operator wants gone from the
 * console, decide whether one rendered log line should be suppressed. The policy is deliberately conservative: it
 * suppresses a line only when the tweak is enabled, the list is non-empty, and the line actually contains one of the
 * configured substrings, so an empty or disabled configuration lets every line through and no error is ever
 * swallowed by accident.
 *
 * <p>Matching is a plain, case-sensitive {@link String#contains(CharSequence) substring} test: the operator names an
 * exact fragment of the noisy line ({@code "Can't keep up!"}), and only lines carrying that fragment verbatim are
 * dropped. Blank entries are ignored so a stray empty string in the list cannot match every line. Pure Java: no
 * Bukkit, Paper, Kyori, or SLF4J, so it is unit-testable in isolation and reused verbatim by the adapter's Log4j2
 * filter.
 */
public final class ConsoleFilterPolicy {

    private final boolean enabled;
    private final List<String> patterns;

    /**
     * @param enabled whether the filter is active at all; when {@code false} nothing is ever suppressed
     * @param patterns the substrings that mark a line for suppression (blank entries are ignored)
     */
    public ConsoleFilterPolicy(boolean enabled, List<String> patterns) {
        this.enabled = enabled;
        this.patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }

    /**
     * Whether {@code line} should be kept out of the console. Returns {@code false} whenever the tweak is off or no
     * patterns are configured, so the default posture is to suppress nothing.
     *
     * @param line the rendered log line to test
     * @return {@code true} only when the filter is enabled and {@code line} contains a configured substring
     */
    public boolean shouldSuppress(String line) {
        Objects.requireNonNull(line, "line");
        if (!enabled || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (!pattern.isEmpty() && line.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
