/**
 * The DecentHolograms import source (docs/12-migration §1.2): reads one YAML file per hologram from
 * {@code plugins/DecentHolograms/holograms/}, maps each file's first page into the {@code holograms}
 * context's {@code Hologram} aggregate, and streams them as {@code HologramRecord}s for the shared write
 * path. {@code parse/} confines the foreign YAML codec; {@code map/} performs the ACL translation to the
 * domain. Parsing stays strictly in this source: no YAML node escapes to the importer.
 */
@NullMarked
package com.uxplima.uxmessentials.migration.convert.decentholograms;

import org.jspecify.annotations.NullMarked;
