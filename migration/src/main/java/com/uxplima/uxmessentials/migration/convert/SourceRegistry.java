package com.uxplima.uxmessentials.migration.convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The registry of <em>built</em> sources the import command resolves a {@link Convert} from
 * (docs/12-migration §1.1). Only built sources have an entry; planned sources (CMI, HuskHomes,
 * PlayerVaultX, CoinsEngine, SunLight, AxVault) never appear here and never tab-complete, planned ≠
 * stubbed (§1.2). A source name the registry does not know resolves to {@link Optional#empty()}, which
 * the command turns into an actionable "unknown source" message listing the built ids, never a silent
 * no-op. {@code MigrationSourceRegistryDriftTest} keeps these keys in lock-step with the roster and the
 * per-source mapping tables.
 */
@NullMarked
public final class SourceRegistry {

    private final Map<SourceId, Convert> sources;

    /** Build a registry over the given built sources; the iteration order is the {@code --list} order. */
    public SourceRegistry(List<Convert> built) {
        Objects.requireNonNull(built, "built");
        Map<SourceId, Convert> byId = new LinkedHashMap<>();
        for (Convert source : built) {
            if (byId.putIfAbsent(source.id(), source) != null) {
                throw new IllegalArgumentException("duplicate source id: " + source.id());
            }
        }
        this.sources = Map.copyOf(byId);
    }

    /** Resolve a source by id, or empty when no built source answers to it. */
    public Optional<Convert> resolve(SourceId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(sources.get(id));
    }

    /** Every built source's descriptor, in registration order, for {@code /uxmess import --list}. */
    public List<SourceDescriptor> describeAll() {
        return sources.values().stream().map(Convert::describe).toList();
    }

    /** The built source ids, for the command's tab-completion suggestions. */
    public List<SourceId> builtIds() {
        return List.copyOf(sources.keySet());
    }
}
