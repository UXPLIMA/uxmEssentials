package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a parsed EssentialsX kit into a domain {@link KitDefinition} (docs/12-migration §5.1). The
 * EssentialsX {@code delay} (seconds) becomes the kit cooldown; each raw item descriptor line becomes an
 * opaque {@link KitItem} whose {@code data} is the descriptor and whose amount is the trailing quantity
 * token (defaulting to 1). The descriptor stays opaque. The bukkit-side writer round-trips it through
 * Bukkit's item codec, so this mapper never imports an {@code ItemStack} type.
 */
@NullMarked
public final class KitMapper {

    public ImportedKit map(EssXKit src) {
        Objects.requireNonNull(src, "src");
        KitDefinition definition = KitDefinition.repeatable(
                KitId.of(src.name()), items(src.items()), Duration.ofSeconds(src.delaySeconds()));
        return new ImportedKit(definition);
    }

    private List<KitItem> items(List<String> rawLines) {
        List<KitItem> items = new ArrayList<>();
        for (String line : rawLines) {
            items.add(KitItem.of(line, amountOf(line)));
        }
        return items;
    }

    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    private static int amountOf(String line) {
        List<String> tokens = WHITESPACE.splitAsStream(line.strip()).toList();
        if (tokens.size() < 2) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(tokens.get(1)));
        } catch (NumberFormatException notAQuantity) {
            // The second token is not a count (an enchantment or metadata token); the writer reads the
            // full descriptor, so the kit still imports with a single stack of the item.
            return 1;
        }
    }
}
