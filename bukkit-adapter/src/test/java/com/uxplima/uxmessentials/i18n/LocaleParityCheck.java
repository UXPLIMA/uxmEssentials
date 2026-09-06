package com.uxplima.uxmessentials.i18n;

import java.util.Set;
import java.util.TreeSet;

import com.uxplima.uxmessentials.shared.application.message.MessageKeyCatalog;

/**
 * The fast fail-fast locale-parity gate (docs/04-build.md §8.1, docs/13-i18n §10.2). Run by the
 * {@code localeParityCheck} Gradle task ahead of the slower JUnit matrix in
 * {@code MessageKeyLocaleParityDriftTest}: it asserts that {@code messages_en.conf} carries exactly the
 * {@link MessageKeyCatalog} key set, and that every shipped locale carries exactly {@code en}'s key set.
 *
 * <p>A non-empty difference prints the offending keys and exits non-zero so the build stops before the
 * test phase even starts. {@code en} is the authority; a locale with a missing key (a hole after an
 * {@code en} change) or a stale extra key fails here.
 */
public final class LocaleParityCheck {

    private static final String EN = "en";

    private LocaleParityCheck() {}

    public static void main(String[] args) {
        StringBuilder problems = new StringBuilder();
        Set<String> enumKeys = new TreeSet<>(MessageKeyCatalog.allKeys());
        Set<String> en = new TreeSet<>(CatalogKeys.read(EN));

        appendDiff(problems, "messages_en.conf vs MessageKey enum constants", enumKeys, en);
        for (String language : CatalogKeys.shippedLanguages()) {
            if (language.equals(EN)) {
                continue;
            }
            appendDiff(
                    problems,
                    "messages_" + language + ".conf vs messages_en.conf",
                    en,
                    new TreeSet<>(CatalogKeys.read(language)));
        }

        if (problems.length() > 0) {
            System.err.println("Locale parity check FAILED:");
            System.err.println(problems);
            System.exit(1);
        }
        System.out.println("Locale parity check OK. Every shipped locale carries en's exact key set.");
    }

    private static void appendDiff(StringBuilder out, String label, Set<String> authority, Set<String> actual) {
        Set<String> missing = new TreeSet<>(authority);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(authority);
        if (missing.isEmpty() && extra.isEmpty()) {
            return;
        }
        out.append("  [").append(label).append("]\n");
        if (!missing.isEmpty()) {
            out.append("    missing keys: ").append(missing).append('\n');
        }
        if (!extra.isEmpty()) {
            out.append("    extra keys:   ").append(extra).append('\n');
        }
    }
}
