package com.uxplima.uxmessentials.communication.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.communication.domain.InfoPage;

/**
 * Maps a command name to its {@link InfoPage}. The operator declares pages in {@code info-pages.conf}
 * ({@code rules}, {@code motd}, {@code info}, and any custom page); the module reads them into this registry and
 * registers one dynamic command per page. A {@code /rules} lookup returns the page's lines for the adapter to
 * render through MiniMessage; a lookup for an unconfigured page returns empty, which the command surface reports
 * with the {@link CommunicationMessageKey#INFO_PAGE_MISSING} plugin string.
 *
 * <p>The registry is immutable once built: a reload constructs a fresh one and swaps it in. Command names are
 * normalised to lower case by {@link InfoPage}, and two pages claiming the same command are rejected at build
 * time so the dynamic-command set is unambiguous. The pages carry operator content only; nothing here is a
 * {@code MessageKey} or parity-checked.
 */
public final class InfoRegistry {

    private final Map<String, InfoPage> pages;

    private InfoRegistry(Map<String, InfoPage> pages) {
        this.pages = Map.copyOf(pages);
    }

    /** An empty registry: no info pages configured, so the module registers no info commands. */
    public static InfoRegistry empty() {
        return new InfoRegistry(Map.of());
    }

    /** A registry over {@code pages}, rejecting two pages that claim the same command name. */
    public static InfoRegistry of(List<InfoPage> pages) {
        Objects.requireNonNull(pages, "pages");
        Map<String, InfoPage> byCommand = new LinkedHashMap<>();
        for (InfoPage page : pages) {
            InfoPage clash = byCommand.putIfAbsent(page.command(), page);
            if (clash != null) {
                throw new IllegalArgumentException("duplicate info page command: " + page.command());
            }
        }
        return new InfoRegistry(byCommand);
    }

    /** The page bound to {@code command} (case-insensitive), or empty when no such page is configured. */
    public Optional<InfoPage> find(String command) {
        Objects.requireNonNull(command, "command");
        return Optional.ofNullable(pages.get(command.toLowerCase(Locale.ROOT)));
    }

    /** Every configured page in declaration order: the module iterates this to register one command each. */
    public List<InfoPage> all() {
        return List.copyOf(pages.values());
    }

    /** Whether any info page is configured. */
    public boolean isEmpty() {
        return pages.isEmpty();
    }
}
