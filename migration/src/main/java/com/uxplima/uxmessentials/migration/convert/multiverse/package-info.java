/**
 * The Multiverse-Core import source (docs/12-migration §5): reads the world registry from Multiverse's
 * {@code plugins/Multiverse-Core/worlds.yml}, maps each entry into the competitor-neutral {@code ImportedWorld}, and
 * streams them as {@code WorldRecord}s for the worlds repository. {@code parse/} confines the foreign YAML codec and
 * absorbs the two on-disk layouts Multiverse has shipped; {@code map/} performs the ACL translation onto our
 * {@code ManagedWorld} aggregate. Parsing stays strictly in this source: no YAML node escapes it.
 */
@NullMarked
package com.uxplima.uxmessentials.migration.convert.multiverse;

import org.jspecify.annotations.NullMarked;
