package com.uxplima.uxmessentials.vote.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Parses the {@code sites} block of the vote module config into a {@link VoteSiteCatalog}. Each
 * site entry carries a name (required), an optional {@code service} (the Votifier service key the
 * cooldown is matched on; defaults to {@code name} when absent), an optional URL, and an optional
 * per-site {@code cooldown-minutes} that falls back to the module-level
 * {@code default-cooldown-minutes}.
 *
 * <h2>Back-compat: legacy {@code vote-links}</h2>
 * When {@code sites} is absent or empty but a {@code vote-links} list is present, the loader
 * synthesises a {@link VoteSiteSpec} for each link using the derived domain as the name, service,
 * and the link as the URL, applying the default cooldown. This keeps existing configs loading, but a
 * synthesised service rarely matches the real Votifier service string, so legacy links must migrate
 * to explicit {@code sites} with a {@code service} to get a working per-site cooldown.
 * The {@code vote-links} list is still used by
 * {@link com.uxplima.uxmessentials.vote.application.VoteLinks} to render the plain chat link
 * list; both paths can coexist as independent features.
 *
 * <h2>Decision: one authority, graceful fallback</h2>
 * {@code vote-links} continues to drive the {@code /vote} chat-link display. {@code sites} is
 * the authoritative source for cooldown tracking. When {@code sites} is absent, {@code vote-links}
 * seeds the catalog so per-site cooldown and reminder features work without migrating.
 */
@NullMarked
public final class VoteSiteCatalogLoader {

    private static final int DEFAULT_COOLDOWN_MINUTES = 1440; // 24 h

    private VoteSiteCatalogLoader() {}

    /**
     * Read the vote module config at {@code moduleConfig} and build a catalog from the {@code sites}
     * block (falling back to {@code vote-links} for back-compat). An absent or unreadable file yields
     * an empty catalog.
     */
    public static VoteSiteCatalog loadFrom(Path moduleConfig, Logger log) {
        Objects.requireNonNull(moduleConfig, "moduleConfig");
        Objects.requireNonNull(log, "log");
        if (!Files.exists(moduleConfig)) {
            return VoteSiteCatalog.empty();
        }
        try {
            ConfigurationNode root = HoconConfigurationLoader.builder()
                    .path(moduleConfig)
                    .build()
                    .load();
            return parse(root);
        } catch (ConfigurateException failure) {
            log.error("failed to load vote site catalog from " + moduleConfig + "; using empty catalog", failure);
            return VoteSiteCatalog.empty();
        }
    }

    /**
     * Parse a catalog from an already-loaded config node (the root of the vote module config).
     * Exposed for tests and for reuse from an in-memory node.
     */
    public static VoteSiteCatalog parse(ConfigurationNode root) {
        Objects.requireNonNull(root, "root");
        int defaultMinutes = Math.max(0, root.node("default-cooldown-minutes").getInt(DEFAULT_COOLDOWN_MINUTES));
        Duration defaultCooldown = Duration.ofMinutes(defaultMinutes);

        ConfigurationNode sitesNode = root.node("sites");
        if (!sitesNode.virtual() && sitesNode.isList()) {
            List<VoteSiteSpec> specs = parseSites(sitesNode, defaultCooldown);
            if (!specs.isEmpty()) {
                return new VoteSiteCatalog(specs);
            }
        }

        // No sites configured: fall back to legacy vote-links.
        ConfigurationNode linksNode = root.node("vote-links");
        if (!linksNode.virtual() && linksNode.isList()) {
            List<VoteSiteSpec> specs = parseFromLegacyLinks(linksNode, defaultCooldown);
            if (!specs.isEmpty()) {
                return new VoteSiteCatalog(specs);
            }
        }

        return VoteSiteCatalog.empty();
    }

    private static List<VoteSiteSpec> parseSites(ConfigurationNode sitesNode, Duration defaultCooldown) {
        List<VoteSiteSpec> specs = new ArrayList<>();
        for (ConfigurationNode child : sitesNode.childrenList()) {
            if (child.virtual() || !child.isMap()) {
                continue;
            }
            @Nullable String name = child.node("name").getString();
            if (name == null || name.isBlank()) {
                continue;
            }
            String displayName = name.strip();
            @Nullable String rawService = child.node("service").getString();
            String service = rawService == null || rawService.isBlank() ? displayName : rawService.strip();
            @Nullable String rawUrl = child.node("url").getString();
            Optional<String> url = rawUrl == null || rawUrl.isBlank() ? Optional.empty() : Optional.of(rawUrl.strip());
            int cooldownMinutes = child.node("cooldown-minutes").getInt(-1);
            Duration cooldown = cooldownMinutes > 0 ? Duration.ofMinutes(cooldownMinutes) : defaultCooldown;
            specs.add(new VoteSiteSpec(displayName, service, url, cooldown));
        }
        return List.copyOf(specs);
    }

    private static List<VoteSiteSpec> parseFromLegacyLinks(ConfigurationNode linksNode, Duration defaultCooldown) {
        List<VoteSiteSpec> specs = new ArrayList<>();
        for (ConfigurationNode child : linksNode.childrenList()) {
            @Nullable String link = child.getString();
            if (link == null || link.isBlank()) {
                continue;
            }
            String trimmed = link.strip();
            String name = deriveName(trimmed);
            specs.add(new VoteSiteSpec(name, Optional.of(trimmed), defaultCooldown));
        }
        return List.copyOf(specs);
    }

    /**
     * Extract a short display name from a URL for the legacy-links fallback. Strips the scheme and
     * {@code www.} prefix and takes the domain portion only. Falls back to the full URL when the
     * result would be blank.
     */
    private static String deriveName(String url) {
        String s = url.toLowerCase(Locale.ROOT);
        for (String scheme : List.of("https://", "http://")) {
            if (s.startsWith(scheme)) {
                s = s.substring(scheme.length());
                break;
            }
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        int slash = s.indexOf('/');
        String domain = slash > 0 ? s.substring(0, slash) : s;
        return domain.isBlank() ? url : domain;
    }
}
