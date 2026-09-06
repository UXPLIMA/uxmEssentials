package com.uxplima.uxmessentials.shared.application.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Merges the code-side {@link CommandDefinition}s with the parsed {@link CommandOverride}s into the
 * effective command surface the adapter registers.
 *
 * <p>Pure application logic: it knows nothing about Brigadier or config files. Resolution is
 * deterministic in definition order, the first command to claim a name or alias keeps it, and a later
 * collision is dropped with a {@link CatalogWarning} rather than failing the build. A disabled command
 * claims nothing, so disabling one frees its name and aliases for another command.
 */
public final class CommandCatalog {

    private CommandCatalog() {}

    /** The resolved surface plus any non-fatal warnings to surface to the operator log. */
    public record Resolution(List<EffectiveCommand> effective, List<CatalogWarning> warnings) {
        public Resolution {
            effective = List.copyOf(effective);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Resolve {@code definitions} against {@code overrides} (keyed by {@code commandId} value). The
     * {@code guiDefault} sets the global bare-opens-GUI behaviour each command inherits unless its own
     * override opts in or out explicitly.
     */
    public static Resolution resolve(
            List<CommandDefinition> definitions, Map<String, CommandOverride> overrides, boolean guiDefault) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(overrides, "overrides");
        List<EffectiveCommand> out = new ArrayList<>();
        List<CatalogWarning> warnings = new ArrayList<>();
        Set<String> claimed = new HashSet<>();
        for (CommandDefinition def : definitions) {
            out.add(resolveOne(def, overrides.get(def.id().value()), claimed, warnings, guiDefault));
        }
        return new Resolution(out, warnings);
    }

    private static EffectiveCommand resolveOne(
            CommandDefinition def,
            @Nullable CommandOverride ov,
            Set<String> claimed,
            List<CatalogWarning> warnings,
            boolean guiDefault) {
        boolean enabled = ov == null || ov.enabled();
        boolean gui = ov == null ? guiDefault : ov.gui().orElse(guiDefault);
        String name = effectiveName(def, ov, warnings);
        if (!enabled) {
            return new EffectiveCommand(def.id(), name, List.of(), Map.of(), false, gui);
        }
        name = claimName(def, name, claimed, warnings);
        List<String> aliases = claimAliases(def, ov, claimed, warnings);
        Set<String> globallyVisible = new HashSet<>();
        globallyVisible.add(key(name));
        aliases.forEach(alias -> globallyVisible.add(key(alias)));
        Map<String, List<String>> localized = claimLocalizedAliases(def, ov, claimed, globallyVisible, warnings);
        return new EffectiveCommand(def.id(), name, aliases, localized, true, gui);
    }

    private static String effectiveName(
            CommandDefinition def, @Nullable CommandOverride ov, List<CatalogWarning> warnings) {
        if (ov == null || ov.name().isEmpty()) {
            return def.defaultName();
        }
        String wanted = ov.name().get().trim();
        if (wanted.isBlank()) {
            warnings.add(
                    new CatalogWarning("blank name for '" + def.id() + "', using default '" + def.defaultName() + "'"));
            return def.defaultName();
        }
        return wanted;
    }

    private static String claimName(
            CommandDefinition def, String name, Set<String> claimed, List<CatalogWarning> warnings) {
        String nameKey = key(name);
        if (claimed.contains(nameKey)) {
            warnings.add(new CatalogWarning(
                    "name '" + name + "' for '" + def.id() + "' collides, using default '" + def.defaultName() + "'"));
            name = def.defaultName();
            nameKey = key(name);
            if (claimed.contains(nameKey)) {
                warnings.add(new CatalogWarning("default name '" + name + "' for '" + def.id() + "' also collides"));
            }
        }
        claimed.add(nameKey);
        return name;
    }

    private static List<String> claimAliases(
            CommandDefinition def, @Nullable CommandOverride ov, Set<String> claimed, List<CatalogWarning> warnings) {
        List<String> wanted = ov == null ? def.defaultAliases() : ov.aliases();
        List<String> out = new ArrayList<>();
        for (String alias : wanted) {
            String aliasKey = key(alias);
            if (claimed.contains(aliasKey)) {
                warnings.add(new CatalogWarning("alias '" + alias + "' for '" + def.id() + "' collides, dropping"));
                continue;
            }
            claimed.add(aliasKey);
            out.add(alias);
        }
        return out;
    }

    private static Map<String, List<String>> claimLocalizedAliases(
            CommandDefinition def,
            @Nullable CommandOverride override,
            Set<String> claimed,
            Set<String> globallyVisible,
            List<CatalogWarning> warnings) {
        if (override == null || override.localizedAliases().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> byLocale = new LinkedHashMap<>();
        Set<String> ownedLocalized = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : override.localizedAliases().entrySet()) {
            java.util.Optional<String> normalizedLocale = CommandLocales.normalize(entry.getKey());
            if (normalizedLocale.isEmpty()) {
                warnings.add(new CatalogWarning(
                        "invalid locale '" + entry.getKey() + "' for '" + def.id() + "', dropping its aliases"));
                continue;
            }
            List<String> accepted = byLocale.computeIfAbsent(normalizedLocale.get(), ignored -> new ArrayList<>());
            Set<String> acceptedHere = new HashSet<>();
            accepted.forEach(alias -> acceptedHere.add(key(alias)));
            for (String raw : entry.getValue()) {
                String alias = java.text.Normalizer.normalize(raw.strip(), java.text.Normalizer.Form.NFC);
                if (!validLiteral(alias)) {
                    warnings.add(new CatalogWarning("invalid localized alias '" + raw + "' for '" + def.id()
                            + "' locale '" + normalizedLocale.get() + "', dropping"));
                    continue;
                }
                String aliasKey = key(alias);
                if (globallyVisible.contains(aliasKey)) {
                    warnings.add(new CatalogWarning("localized alias '" + alias + "' for '" + def.id()
                            + "' is already globally visible, dropping duplicate"));
                    continue;
                }
                if (ownedLocalized.contains(aliasKey)) {
                    if (acceptedHere.add(aliasKey)) {
                        accepted.add(alias);
                    }
                    continue;
                }
                if (claimed.contains(aliasKey)) {
                    warnings.add(new CatalogWarning(
                            "localized alias '" + alias + "' for '" + def.id() + "' collides, dropping"));
                    continue;
                }
                claimed.add(aliasKey);
                ownedLocalized.add(aliasKey);
                acceptedHere.add(aliasKey);
                accepted.add(alias);
            }
        }
        byLocale.values().removeIf(List::isEmpty);
        return Collections.unmodifiableMap(byLocale);
    }

    private static boolean validLiteral(String value) {
        if (value.isEmpty() || value.charAt(0) == '/') {
            return false;
        }
        return value.codePoints().noneMatch(Character::isWhitespace);
    }

    private static String key(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
    }
}
