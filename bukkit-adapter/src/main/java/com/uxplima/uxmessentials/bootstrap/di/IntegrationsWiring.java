package com.uxplima.uxmessentials.bootstrap.di;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker.MapMarkersWiring;
import com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks.ServerLinksApplier;
import com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks.ServerLinksConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateCheckSettings;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.update.JsonUrlReleaseProvider;
import com.uxplima.uxmlib.update.UpdateAnnouncement;
import com.uxplima.uxmlib.update.UpdateChecker;
import com.uxplima.uxmlib.update.UpdateNotifier;
import com.uxplima.uxmlib.update.UpdateOutcome;
import org.jspecify.annotations.NullMarked;

/**
 * Wires the cross-cutting server-integration polish features that belong to no single feature context: the 1.21+
 * pause-menu server links, the opt-in update checker, and the Dynmap/squaremap map-marker integration. All are
 * driven from the root {@code config.conf}; none owns a bounded context, so they are wired here in the bootstrap
 * surface rather than registered as a {@code FeatureModule}.
 *
 * <p>Server links apply immediately (clear-and-set the global links from {@code server-links}); an empty list
 * leaves the live links untouched. The update checker is off by default and built on the uxmLib update toolkit —
 * when enabled it reads the operator's {@code source-url} as a generic release-JSON endpoint, runs its checks
 * off-thread through uxmLib's Folia-aware scheduler, and announces a newer release to the console once per
 * distinct version. When {@code notify-ops-on-join} is on it also surfaces uxmLib's permission-gated,
 * click-to-open join notice (the {@code uxmessentials.update.notify} node). When disabled, nothing is scheduled.
 * The map-marker integration renders warps and spawns (homes are off by default for privacy) onto whichever
 * supported map plugin is installed; with no map plugin or {@code map-markers.enabled = false} it wires nothing.
 */
@NullMarked
final class IntegrationsWiring {

    private IntegrationsWiring() {}

    // The node a player must hold to see the on-join update notice. An admin/op-style node, not isOp(), so a
    // server owner controls exactly who is told (the uxmLib notifier gates on the permission, not on op status).
    private static final String UPDATE_NOTIFY_PERMISSION = "uxmessentials.update.notify";

    // What the notice says. uxmLib used to write this line itself and it stopped: a library that words a
    // message decides how a plugin sounds, and that is the plugin's to decide. The wording is the one the
    // library shipped, kept to the letter, because an operator who has read this line on every start should
    // not have to read a new one to learn the same thing.
    private static final UpdateAnnouncement UPDATE_ANNOUNCEMENT = (pluginName, currentVersion, release) -> Text.mini(
                    "<gray>[<aqua><name></aqua>]</gray> <yellow>A new version is available:</yellow> "
                            + "<gray><current></gray> <dark_gray>-></dark_gray> <green><latest></green>",
                    Text.placeholder("name", pluginName),
                    Text.placeholder("current", currentVersion),
                    Text.placeholder("latest", release.version()))
            .appendSpace()
            .append(Text.mini("<aqua><underlined>[Open release page]</underlined></aqua>")
                    .clickEvent(ClickEvent.openUrl(release.url())));

    /**
     * Apply server links, start the update checker, and wire the map-marker integration from {@code config},
     * returning the disable hooks (recurring-check stop + marker clear) the caller runs on disable.
     */
    static Wired wire(JavaPlugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(persistence, "persistence");
        Path dataFolder = plugin.getDataFolder().toPath();

        applyServerLinks(plugin, dataFolder, kernel);
        MapMarkersWiring.Stopped markers = wireMapMarkers(plugin, config, kernel, persistence);
        return wireUpdateChecker(plugin, config, kernel).withMapMarkers(markers.stop());
    }

    private static MapMarkersWiring.Stopped wireMapMarkers(
            JavaPlugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence) {
        // The bootstrap knows the concrete event publisher (it wired it), so the marker service can subscribe
        // to the in-process bus for live warp/home updates. KernelPorts only carries the narrow publish port.
        InProcessDomainEventPublisher events = (InProcessDomainEventPublisher) kernel.events();
        return MapMarkersWiring.wire(plugin.getServer(), config, persistence, kernel.scheduler(), events, kernel.log());
    }

    private static void applyServerLinks(JavaPlugin plugin, Path dataFolder, KernelPorts kernel) {
        ServerLinksApplier applier = new ServerLinksApplier(plugin.getServer(), kernel.scheduler(), kernel.log());
        applier.apply(new ServerLinksConfig(dataFolder, kernel.log()).read());
    }

    // Map the operator's update-check block onto the uxmLib update toolkit. The library's UpdateChecker /
    // UpdateNotifier run off-thread through its own Folia-aware Scheduler (PaperScheduler), so the bridge is just
    // constructing that scheduler over the plugin. Disabled -> wire nothing. source-url -> a JsonUrlReleaseProvider
    // (the same any-endpoint behaviour the local checker had). The version string is handed straight to the lib,
    // which parses it; an unreadable plugin version still parses ("0.0.0"-style) inside the lib rather than here.
    private static Wired wireUpdateChecker(JavaPlugin plugin, ConfigStore config, KernelPorts kernel) {
        UpdateCheckSettings settings = UpdateCheckSettings.from(config);
        if (!settings.enabled()) {
            return Wired.inert();
        }
        Scheduler scheduler = new PaperScheduler(plugin);
        UpdateChecker checker = new UpdateChecker(
                scheduler,
                new JsonUrlReleaseProvider(settings.sourceUrl()),
                plugin.getPluginMeta().getVersion());
        Runnable stop = settings.notifyOpsOnJoin()
                ? startWithJoinNotice(plugin, scheduler, checker, settings)
                : startConsoleOnly(scheduler, checker, kernel.log(), settings);
        return new Wired(stop);
    }

    // notify-ops-on-join = true: the library notifier owns the console announce and registers its own
    // permission-gated, click-to-open join notice. It couples the recurring poll with that listener, so a zero
    // interval (check-once) falls back to the shipped default cadence here — the join listener self-warms its
    // cache anyway, so a player who joins before the first poll still triggers a check.
    private static Runnable startWithJoinNotice(
            JavaPlugin plugin, Scheduler scheduler, UpdateChecker checker, UpdateCheckSettings settings) {
        UpdateNotifier notifier =
                new UpdateNotifier(plugin, scheduler, checker, UPDATE_NOTIFY_PERMISSION, UPDATE_ANNOUNCEMENT);
        notifier.start(Duration.ZERO, settings.repeats() ? settings.interval() : DEFAULT_POLL_PERIOD);
        return notifier::stop;
    }

    // notify-ops-on-join = false: no join listener. The checker announces a newer release to the console once
    // (deduped per distinct newer release by the library), either a single check on enable (interval 0) or on a
    // recurring async timer. Returns a stop hook that cancels the recurring poll when one was armed.
    private static Runnable startConsoleOnly(
            Scheduler scheduler, UpdateChecker checker, Logger log, UpdateCheckSettings settings) {
        if (!settings.repeats()) {
            poll(checker, log);
            return () -> {};
        }
        TaskHandle poll = scheduler.asyncTimer(Duration.ZERO, settings.interval(), handle -> poll(checker, log));
        return poll::cancel;
    }

    // One console-announcing check. The library's checkAndAnnounce future funnels every outcome (including
    // failure) into the dedupe and the announce callback, so the returned future carries nothing the caller
    // needs — dropped here exactly as the library's own notifier drops it.
    private static void poll(UpdateChecker checker, Logger log) {
        var ignored = checker.checkAndAnnounce(outcome -> announce(log, checker, outcome));
    }

    private static void announce(Logger log, UpdateChecker checker, UpdateOutcome outcome) {
        outcome.release()
                .ifPresent(release -> log.info(
                        "uxmEssentials update available: {} -> {}",
                        checker.currentVersion().toString(),
                        release.version()));
    }

    /** The poll cadence used when notify-on-join is on but the operator set a zero (check-once) interval. */
    private static final Duration DEFAULT_POLL_PERIOD = Duration.ofHours(12);

    /**
     * What the integrations wiring contributes: a stop hook that runs every disable action (the update checker's
     * recurring loop / notifier teardown, the map-marker layer clear) on disable.
     *
     * @param stop the disable hook running every integration's teardown
     */
    record Wired(Runnable stop) {
        Wired {
            Objects.requireNonNull(stop, "stop");
        }

        static Wired inert() {
            return new Wired(() -> {});
        }

        /** Fold the map-marker disable hook into this wiring's aggregate stop, run after the checker stop. */
        Wired withMapMarkers(Runnable markerStop) {
            Objects.requireNonNull(markerStop, "markerStop");
            Runnable both = () -> {
                stop.run();
                markerStop.run();
            };
            return new Wired(both);
        }
    }
}
