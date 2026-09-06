package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Messages} implementation: resolve the viewer's locale, fetch the catalog template, and do
 * literal {@code {name}} placeholder substitution. The return is a plain MiniMessage source string
 * no Adventure type crosses this boundary, which is what keeps the kernel free of {@code net.kyori};
 * the tag parsing into a {@code Component} happens once downstream in {@link BukkitMessageSink}.
 *
 * <p>The viewer's locale is resolved per viewer by the {@link LocaleResolver} through the
 * override → request-scope → server-default → {@code en} chain, so two players on one server each see
 * their own language from the same call site, and a deferred message renders in the requester's locale
 * via the request-scope binding. The per-key {@code en} fallback chain itself lives behind the
 * {@link LocaleCatalog}; this class only chooses which locale to ask for.
 */
@NullMarked
public final class CatalogMessages implements Messages {

    private final LocaleCatalog catalog;
    private final LocaleResolver locales;

    public CatalogMessages(LocaleCatalog catalog, LocaleResolver locales) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.locales = Objects.requireNonNull(locales, "locales");
    }

    @Override
    public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Locale locale = locales.resolve(viewer);
        String template = catalog.template(locale, key);
        return substitute(template, placeholders);
    }

    private static String substitute(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
