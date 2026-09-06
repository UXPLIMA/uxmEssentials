package com.uxplima.uxmessentials.migration.convert;

import java.nio.file.Path;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import org.jspecify.annotations.NullMarked;

/**
 * A single legacy data source the importer can read from, the multi-source port (docs/12-migration
 * §1.1). One implementation per legacy plugin: EssentialsX ships first; CMI, HuskHomes, PlayerVaultX,
 * CoinsEngine, SunLight, and AxVault are planned, each a future {@code Convert} impl behind this same
 * interface.
 *
 * <p>An impl is stateless and self-contained: it owns its own {@code parse/} tree (foreign-format
 * detail) and its {@code map/} ACL mappers, and it drains into the same domain aggregates every other
 * adapter uses. What it does <em>not</em> own is the write path. Every source funnels its mapped
 * records through the same {@code ImportData} use case and the same application services, so
 * idempotency, conflict policy, quotas, and audit are identical no matter which source ran. Adding a
 * source adds a {@code Convert} impl and a mappings table; it changes nothing in the write/idempotency/
 * audit machinery.
 *
 * <p>Parsing stays here, strictly in the adapter: no {@code Convert} impl exposes a YAML node or a
 * foreign field to its caller: {@link #plan} yields only mapped {@code ImportRecord} domain shapes.
 */
@NullMarked
public interface Convert {

    /** Stable lowercase identifier used on the command line: {@code "essentialsx"}, {@code "cmi"}, … */
    SourceId id();

    /** Human-readable name plus the on-disk layout this source expects, for {@code /uxmess import --list}. */
    SourceDescriptor describe();

    /** Cheap structural probe: true once {@code sourcePath} looks like this source's data tree. */
    boolean detect(Path sourcePath);

    /** Stream parsed-and-mapped records under the given options. Parsing stays here; mapping is inline. */
    ImportPlan plan(ImportOptions options);
}
