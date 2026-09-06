package com.uxplima.uxmessentials.migration.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.ImportData;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.SourceRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The inbound-facing service the {@code /uxmess import} node calls. It resolves a source from the
 * registry, builds the run's {@link ImportOptions} from config, and hands the import to the bounded
 * executor <em>off the tick thread</em> through the {@link Scheduler#async} port, the command handler
 * returns immediately and never blocks (docs/12-migration §3, §7). A dry run binds the no-op accumulator
 * writer; a live run binds the repository writer. Nothing here runs at plugin enable; the importer fires
 * only when this {@code dispatch} is called.
 */
@NullMarked
public final class MigrationImportService {

    private final SourceRegistry registry;
    private final ImportData importData;
    private final RecordWriter liveWriter;
    private final RecordWriter dryRunWriter;
    private final ImportOptions baseOptions;
    private final Scheduler scheduler;
    private final Logger log;
    private final boolean enabled;

    public MigrationImportService(
            SourceRegistry registry,
            ImportData importData,
            RecordWriter liveWriter,
            RecordWriter dryRunWriter,
            ImportOptions baseOptions,
            Scheduler scheduler,
            Logger log,
            boolean enabled) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.importData = Objects.requireNonNull(importData, "importData");
        this.liveWriter = Objects.requireNonNull(liveWriter, "liveWriter");
        this.dryRunWriter = Objects.requireNonNull(dryRunWriter, "dryRunWriter");
        this.baseOptions = Objects.requireNonNull(baseOptions, "baseOptions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.enabled = enabled;
    }

    /** True when the migration module is enabled for a cutover. */
    public boolean enabled() {
        return enabled;
    }

    /** The built source ids, for tab-completion and the usage message. */
    public List<SourceId> builtSourceIds() {
        return registry.builtIds();
    }

    /** The built source descriptors, for {@code /uxmess import --list}. */
    public List<SourceDescriptor> builtSources() {
        return registry.describeAll();
    }

    /** Resolve a raw source name to a built {@link SourceId}, or empty when unknown. */
    public Optional<SourceId> resolve(String raw) {
        try {
            return registry.resolve(SourceId.of(raw)).map(Convert::id);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** Dispatch an import for {@code source} off the tick thread; returns immediately. */
    public void dispatch(String actor, SourceId source, ImportMode mode) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mode, "mode");
        Optional<Convert> convert = registry.resolve(source);
        if (convert.isEmpty()) {
            log.warn("migration import requested for unknown source {}", source);
            return;
        }
        scheduler.async(() -> runOffTick(actor, convert.get(), mode));
    }

    private void runOffTick(String actor, Convert source, ImportMode mode) {
        ImportOptions options = mode.isDryRun() ? baseOptions.asDryRun() : baseOptions;
        RecordWriter writer = mode.isDryRun() ? dryRunWriter : liveWriter;
        try {
            importData.run(actor, source, options, writer);
        } catch (RuntimeException failure) {
            log.error("migration import failed for source " + source.id(), failure);
        }
    }
}
