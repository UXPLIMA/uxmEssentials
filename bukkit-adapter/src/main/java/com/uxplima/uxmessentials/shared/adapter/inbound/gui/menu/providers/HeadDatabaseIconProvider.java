package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;

/**
 * Resolves a {@code hdb:<id>} spec to its HeadDatabase head through the Phase-0 {@link HeadQuery} hook. When
 * HeadDatabase is absent the query is {@link HeadQuery#ABSENT}, so the lookup returns empty and the spec falls
 * through to the renderer's material fallback (a plain head/stone). A menu referencing an HDB head still
 * renders on a server without HeadDatabase. An unknown id, or HeadDatabase still building its head index, is
 * empty for the same reason: the lookup is best-effort and never throws.
 *
 * <p>The provider claims only the {@code hdb:} prefix, never a bare material name.
 */
final class HeadDatabaseIconProvider implements IconProvider {

    private static final String HDB = "hdb:";

    private final HeadQuery headQuery;

    HeadDatabaseIconProvider(HeadQuery headQuery) {
        this.headQuery = Objects.requireNonNull(headQuery, "headQuery");
    }

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        String trimmed = spec.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(HDB)) {
            return Optional.empty();
        }
        return headQuery.head(trimmed.substring(HDB.length()).trim());
    }
}
