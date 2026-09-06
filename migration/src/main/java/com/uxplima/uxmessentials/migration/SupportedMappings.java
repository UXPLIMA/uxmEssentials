package com.uxplima.uxmessentials.migration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.athelion.AthelionPlayerWarpsMappings;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsMappings;
import com.uxplima.uxmessentials.migration.convert.decentholograms.DecentHologramsMappings;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXMappings;
import com.uxplima.uxmessentials.migration.convert.fancyholograms.FancyHologramsMappings;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansMappings;
import com.uxplima.uxmessentials.migration.convert.live.LiveSourceMappings;
import com.uxplima.uxmessentials.migration.convert.multiverse.MultiverseMappings;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsMappings;
import org.jspecify.annotations.NullMarked;

/**
 * Aggregates every <em>built</em> source's per-source mapping table (docs/12-migration §5). It is the
 * single greppable answer to "what does the importer claim to migrate?", scoped by source. The built
 * sources are EssentialsX (a full on-disk source), the live economy sources Vault and PlayerPoints
 * (balance only), LiteBans (a JDBC source for bans/ip-bans/mutes/warns), the two hologram sources
 * DecentHolograms and FancyHolograms (on-disk sources for server-wide holograms), and the three player-warp sources
 * AxPlayerWarps (a JDBC source), Athelion PlayerWarps (a serialised {@code data.yml} source) and Olzie PlayerWarps (a
 * JDBC source). Only built sources
 * contribute rows. A planned source has no code table, so it appears nowhere here until it is built
 * (planned ≠ stubbed, §1.2). The drift guard reads this aggregate to assert the three-way equality
 * code ⇄ doc ⇄ fixtures.
 */
@NullMarked
public final class SupportedMappings {

    private static final Map<SourceId, List<MappingRow>> BY_SOURCE = Map.ofEntries(
            Map.entry(SourceId.of("essentialsx"), EssentialsXMappings.rows()),
            Map.entry(SourceId.of("vault"), LiveSourceMappings.vaultRows()),
            Map.entry(SourceId.of("playerpoints"), LiveSourceMappings.playerPointsRows()),
            Map.entry(SourceId.of("litebans"), LiteBansMappings.rows()),
            Map.entry(SourceId.of("decentholograms"), DecentHologramsMappings.rows()),
            Map.entry(SourceId.of("fancyholograms"), FancyHologramsMappings.rows()),
            Map.entry(SourceId.of("axplayerwarps"), AxPlayerWarpsMappings.rows()),
            Map.entry(SourceId.of("athelionplayerwarps"), AthelionPlayerWarpsMappings.rows()),
            Map.entry(SourceId.of("olzieplayerwarps"), OlziePlayerWarpsMappings.rows()),
            Map.entry(SourceId.of("multiverse"), MultiverseMappings.rows()));

    private SupportedMappings() {}

    /** The source ids that contribute a mapping table: the built sources (§1.2). */
    public static java.util.Set<SourceId> builtSources() {
        return BY_SOURCE.keySet();
    }

    /** The mapping rows for {@code source}, or an empty list when the source contributes none. */
    public static List<MappingRow> rows(SourceId source) {
        Objects.requireNonNull(source, "source");
        return BY_SOURCE.getOrDefault(source, List.of());
    }
}
