package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.ServerLinks;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the configured server links into Paper's global {@link ServerLinks} (the 1.21+ pause-menu links). On
 * apply it clears the existing links and sets the parsed list whole, so a reload is idempotent and never
 * accumulates stale entries. An empty configured list means the feature is off. The live links are left
 * untouched so links pushed by other plugins or the vanilla server survive.
 *
 * <p>The {@code ServerLinks} object is global game state, so the mutation runs on the global region thread through
 * the injected {@link Scheduler} ({@code onGlobal}), the one place CLAUDE.md sanctions for genuinely global state.
 * A malformed or empty entry is dropped by {@link ServerLinkSpec#parse} upstream and logged here; nothing the
 * parser rejects ever reaches the live links, and a bad list is never fatal.
 */
@NullMarked
public final class ServerLinksApplier {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger log;

    public ServerLinksApplier(Server server, Scheduler scheduler, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Parse {@code raw}, then clear-and-set the global links on the global thread; an empty list is left alone. */
    public void apply(List<ServerLinksConfig.RawLink> raw) {
        Objects.requireNonNull(raw, "raw");
        List<ServerLinkSpec> specs = parseAll(raw);
        if (specs.isEmpty()) {
            return;
        }
        scheduler.onGlobal(() -> push(specs));
    }

    private List<ServerLinkSpec> parseAll(List<ServerLinksConfig.RawLink> raw) {
        List<ServerLinkSpec> specs = new ArrayList<>();
        for (ServerLinksConfig.RawLink entry : raw) {
            Optional<ServerLinkSpec> spec = ServerLinkSpec.parse(entry.type(), entry.label(), entry.url());
            if (spec.isPresent()) {
                specs.add(spec.get());
            } else {
                log.warn("skipping malformed server-link entry: {}", entry);
            }
        }
        return specs;
    }

    private void push(List<ServerLinkSpec> specs) {
        ServerLinks links = server.getServerLinks();
        for (ServerLinks.ServerLink existing : new ArrayList<>(links.getLinks())) {
            links.removeLink(existing);
        }
        for (ServerLinkSpec spec : specs) {
            add(links, spec);
        }
        log.info("applied {} server link(s) to the 1.21+ pause menu", specs.size());
    }

    private static void add(ServerLinks links, ServerLinkSpec spec) {
        if (spec.type() != null) {
            links.addLink(spec.type(), spec.url());
        } else {
            links.addLink(Component.text(Objects.requireNonNull(spec.label(), "label")), spec.url());
        }
    }
}
