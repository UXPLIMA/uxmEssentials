package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.translation.Translator;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Wires the catalog into Adventure's {@link GlobalTranslator} for the {@code translatable}-key path
 * (docs/13-i18n §8.2).
 *
 * <p>The {@link MessageKey} catalog is the server-side default and covers everything a player sees
 * through {@code messages.resolve(...)}. This source serves the narrow case where a call site emits a
 * {@code Component.translatable("uxmessentials.<key>")} and wants Adventure to render it per the
 * receiving connection's locale: without the server resolving the locale itself. Each catalog key is
 * registered under the {@code uxmessentials} namespace ({@code home.teleported} →
 * {@code uxmessentials.home.teleported}) for every loaded locale, with {@code en} as the registry's
 * default so a locale missing a key falls back exactly as the catalog chain does.
 *
 * <p>Registration is idempotent: an existing source under the same store key is removed before the
 * fresh one is added, so a {@code /uxmess reload} re-registers rather than stacking duplicates.
 */
@NullMarked
public final class AdventureTranslations {

    private static final Key SOURCE_KEY = Key.key("uxmessentials", "messages");
    private static final String TRANSLATION_NAMESPACE = "uxmessentials";

    private final LocaleCatalog catalog;
    private final Logger log;

    public AdventureTranslations(LocaleCatalog catalog, Logger log) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Build the store from the catalog and (re)register it as a {@link GlobalTranslator} source. */
    public void register(Set<MessageKey> keys) {
        Objects.requireNonNull(keys, "keys");
        removeExistingSource();
        TranslationStore<MessageFormat> store = TranslationStore.messageFormat(SOURCE_KEY);
        store.defaultLocale(Locale.ENGLISH);
        int registered = 0;
        for (MessageKey key : keys) {
            registered += registerKey(store, key);
        }
        GlobalTranslator.translator().addSource(store);
        log.debug("registered {} translatable message templates with the GlobalTranslator", registered);
    }

    private void removeExistingSource() {
        for (Translator source : GlobalTranslator.translator().sources()) {
            if (source instanceof TranslationStore<?> store && SOURCE_KEY.equals(store.name())) {
                GlobalTranslator.translator().removeSource(store);
                return;
            }
        }
    }

    private int registerKey(TranslationStore<MessageFormat> store, MessageKey key) {
        String translationKey = TRANSLATION_NAMESPACE + "." + key.key();
        int count = 0;
        for (Locale locale : catalog.loadedLocales()) {
            String template = catalog.template(locale, key);
            MessageFormat format = safeFormat(template, locale);
            if (format != null) {
                store.register(translationKey, locale, format);
                count++;
            }
        }
        return count;
    }

    /**
     * A {@link MessageFormat} for {@code template}, or {@code null} when the template is not a valid
     * format pattern. Catalog templates use literal {@code {name}} placeholders that {@code MessageFormat}
     * would read as argument indices; the {@code translatable} path is for placeholder-free keys, so a
     * template that does not parse is skipped here rather than failing the whole registration.
     */
    private @org.jspecify.annotations.Nullable MessageFormat safeFormat(String template, Locale locale) {
        try {
            return new MessageFormat(template, locale);
        } catch (IllegalArgumentException notAFormatPattern) {
            log.debug("skipping translatable registration for a non-format template: {}", template);
            return null;
        }
    }
}
