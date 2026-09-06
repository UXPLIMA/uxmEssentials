package com.uxplima.uxmessentials.invrollback.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.invrollback.adapter.inbound.command.InvrestoreCommand;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotExporter;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotListView;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotPreviewView;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotPreviewWindow;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotRestorer;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotTeleporter;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.listener.SnapshotCaptureListener;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.ListenerInvrollbackPlaceholders;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.SnapshotPruneSweep;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackConfig;
import com.uxplima.uxmessentials.invrollback.application.PruneSnapshots;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.persistence.invrollback.SnapshotRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.InvrollbackPlaceholders;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the invrollback context's adapters and use cases over the injected kernel ports, the persistence DSL
 * and the shared menu engine, and produces everything the plugin registers: the death/logout capture listener, the
 * read-only snapshot-preview listener, the {@code /invrestore} staff command, and, as the {@code stop} hook, the
 * scheduled retention sweep's cancel handle.
 *
 * <p>The snapshot repository is the jOOQ adapter built by {@link SnapshotRepositories} (so this module never names a
 * jOOQ type); {@link CaptureSnapshot} bounds a player's snapshots to the configured count at write time;
 * {@link RestoreSnapshot} safety-snapshots the pre-restore state and resolves the chosen snapshot; and
 * {@link PruneSnapshots} runs on the {@link SnapshotPruneSweep}'s repeating, off-tick sweep to keep the table
 * bounded by age. Every DB touch is scheduled off the region thread through the injected {@code Scheduler}
 * (Folia-safe). A disabled module wires none of this; on stop the sweep is cancelled through the returned handle.
 */
@NullMarked
public final class InvrollbackWiring {

    private InvrollbackWiring() {}

    /** Build the invrollback adapters and use cases from {@code ctx}, {@code persistence} and {@code menus}. */
    public static Wired wire(
            ModuleContext ctx, Persistence persistence, Menus menus, MenuBindings menuBindings, Path dataFolder) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        InvrollbackConfig config = InvrollbackConfig.from(ctx.config());
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();

        SnapshotRepository repository = SnapshotRepositories.jooq(persistence);
        CaptureSnapshot capture = new CaptureSnapshot(repository, config.maxPerPlayer());

        SnapshotCaptureListener captureListener = new SnapshotCaptureListener(
                capture,
                kernel.scheduler(),
                clock,
                config.captureOnDeath(),
                config.captureOnLogout(),
                config.includeEnderchest());

        RestoreSnapshot restore = new RestoreSnapshot(repository, capture);
        SnapshotRestorer restorer = new SnapshotRestorer(
                restore, kernel.scheduler(), kernel.messages(), kernel.messageSink(), clock, kernel.events());
        SnapshotTeleporter teleporter =
                new SnapshotTeleporter(kernel.scheduler(), kernel.messages(), kernel.messageSink(), kernel.log());
        SnapshotExporter exporter = new SnapshotExporter(kernel.scheduler(), kernel.messages(), kernel.messageSink());
        SnapshotPreviewView preview = new SnapshotPreviewView(
                kernel.messages(),
                kernel.scheduler(),
                clock,
                new SnapshotPreviewWindow(menus, dataFolder, kernel.log()),
                restorer,
                teleporter,
                exporter);
        preview.register(menuBindings);
        SnapshotListView listView = new SnapshotListView(
                menus,
                new GuiText(kernel.messages()),
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                repository,
                preview,
                clock);
        InvrestoreCommand command = new InvrestoreCommand(
                listView,
                exporter,
                teleporter,
                repository,
                kernel.playerLookup(),
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink());

        PruneSnapshots prune = new PruneSnapshots(repository, config.retentionPolicy());
        boolean retentionOn = config.maxPerPlayer() > 0 || config.maxAgeDays() > 0;
        SnapshotPruneSweep sweep = new SnapshotPruneSweep(prune, kernel.scheduler(), clock, retentionOn);
        AutoCloseable sweepHandle = sweep.start();

        return new Wired(
                List.of(captureListener),
                List.of(command),
                repository,
                restorer,
                () -> close(sweepHandle, kernel),
                new ListenerInvrollbackPlaceholders(captureListener));
    }

    /** Cancel the repeating retention sweep on module stop; a failure to close is logged, never rethrown. */
    private static void close(AutoCloseable sweepHandle, KernelPorts kernel) {
        try {
            sweepHandle.close();
        } catch (Exception closing) {
            kernel.log().error("failed to cancel the invrollback retention sweep", closing);
        }
    }

    /**
     * Everything the invrollback module contributes once wired: the capture and preview listeners the plugin
     * registers, the {@code /invrestore} command, and the stop hook that cancels the retention sweep.
     *
     * @param listeners the Bukkit listeners to register
     * @param commands the Brigadier commands to register
     * @param repository the snapshot store the published query lists from
     * @param restorer the three-hop restore flow the published action runs, safety copy and all
     * @param stop the teardown run on module stop
     */
    public record Wired(
            List<Listener> listeners,
            List<CommandRegistration> commands,
            SnapshotRepository repository,
            SnapshotRestorer restorer,
            Runnable stop,
            InvrollbackPlaceholders placeholders) {
        public Wired {
            listeners = List.copyOf(listeners);
            commands = List.copyOf(commands);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(restorer, "restorer");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(placeholders, "placeholders");
        }
    }
}
