package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Keeps the three surfaces that must list every claim provider in lockstep. A provider becomes usable only when
 * all three agree: {@link ClaimProviders} registers it, {@code config.conf} exposes a {@code claims.providers}
 * key an operator can toggle, and {@code paper-plugin.yml} declares its plugin as a {@code load: BEFORE}
 * dependency so it is loaded before us and its present-guard sees it. Add a provider to the registry but forget
 * the config line and an operator cannot discover the key to disable it; forget the dependency and the plugin
 * may load after us and sit silently inactive. This guard turns either omission into a build failure.
 *
 * <p>It reads only tracked sources, {@code /config.conf} and {@code /paper-plugin.yml} from the classpath, both
 * bundled from {@code src/main/resources}, and never touches {@code docs/}, which is gitignored and absent in a
 * clean CI checkout. The provider keys come from {@link ClaimProviders#candidateKeys()}, the single source of
 * truth the registry itself derives, so the guard cannot drift from the set it checks: the config lines are read
 * from raw text (the toggle entries ship commented, so a HOCON parse would not see them) and the dependency
 * names are parsed from the YAML. {@code uxmclaims} is the one documented exception to the dependency check: it
 * is discovered reflectively with no load-order need.
 *
 * <p>Each real-corpus assertion is paired with a teeth test that fires the same detector on a synthetic missing
 * key, so a green result is a checked fact rather than a vacuous pass.
 */
class ClaimProviderCoverageDriftTest {

    /**
     * Each registered provider key mapped to the plugin name {@code paper-plugin.yml} must list, verified against
     * that provider's {@code active()} {@code getPlugin("...")} string. {@code uxmclaims} is deliberately absent
     * it is the reflective, no-load-order exception, checked separately in {@link #theKeyToPluginMapCoversTheRegistryExactly}.
     */
    private static final Map<String, String> KEY_TO_PLUGIN = Map.ofEntries(
            Map.entry("lands", "Lands"),
            Map.entry("griefprevention", "GriefPrevention"),
            Map.entry("griefdefender", "GriefDefender"),
            Map.entry("excellentclaims", "ExcellentClaims"),
            Map.entry("simpleclaimsystem", "SimpleClaimSystem"),
            Map.entry("rclaim", "RClaim"),
            Map.entry("xclaim", "XClaim"),
            Map.entry("homestead", "Homestead"),
            Map.entry("worldguard", "WorldGuard"),
            Map.entry("towny", "Towny"),
            Map.entry("kingdoms", "Kingdoms"),
            Map.entry("huskclaims", "HuskClaims"),
            Map.entry("husktowns", "HuskTowns"),
            Map.entry("factions", "Factions"),
            Map.entry("bentobox", "BentoBox"),
            Map.entry("residence", "Residence"),
            Map.entry("plotsquared", "PlotSquared"),
            Map.entry("superiorskyblock", "SuperiorSkyblock2"));

    /** The one provider discovered reflectively, so it carries no {@code paper-plugin.yml} load-order dependency. */
    private static final String UXM_CLAIMS_KEY = "uxmclaims";

    @Test
    void theKeyToPluginMapCoversTheRegistryExactly() {
        Set<String> mapped = new HashSet<>(KEY_TO_PLUGIN.keySet());
        mapped.add(UXM_CLAIMS_KEY);

        assertThat(ClaimProviders.candidateKeys())
                .as("registry keys must be unique")
                .doesNotHaveDuplicates();
        assertThat(new HashSet<>(ClaimProviders.candidateKeys()))
                .as("every registered provider needs a KEY_TO_PLUGIN entry (or be the uxmclaims exception); a "
                        + "provider added to ClaimProviders must be mapped here so its paper-plugin.yml dependency "
                        + "is checked")
                .isEqualTo(mapped);
    }

    @Test
    void everyRegisteredKeyIsDocumentedInConfig() {
        assertThat(missingFromConfig(ClaimProviders.candidateKeys(), providersBlock()))
                .as("a provider is registered but has no discoverable claims.providers toggle line in config.conf; "
                        + "add a commented '# <key> = false' entry so an operator can turn it off")
                .isEmpty();
    }

    @Test
    void everyRegisteredKeyExceptUxmClaimsDeclaresItsPluginDependency() {
        assertThat(missingFromPaperPlugin(KEY_TO_PLUGIN, declaredServerDependencies()))
                .as("a provider's plugin is not a paper-plugin.yml load-order dependency; without 'load: BEFORE' it "
                        + "may load after us and be silently inactive, so add it under dependencies.server")
                .isEmpty();
    }

    @Test
    void theConfigGuardFiresOnAnUndocumentedKey() {
        assertThat(missingFromConfig(List.of("ghostclaims"), providersBlock()))
                .as("a key absent from the providers block must be reported")
                .containsExactly("ghostclaims");
    }

    @Test
    void thePaperPluginGuardFiresOnAMissingDependency() {
        assertThat(missingFromPaperPlugin(Map.of("ghostclaims", "GhostClaimsPlugin"), declaredServerDependencies()))
                .as("a plugin name absent from the dependency list must be reported")
                .containsExactly("ghostclaims");
    }

    /**
     * The keys whose toggle line is absent from the {@code claims.providers} block. This is the whole config
     * detector: the shipped-corpus test runs it over the real registry keys and the teeth test over a synthetic
     * key, so both exercise identical logic.
     */
    private static List<String> missingFromConfig(Collection<String> keys, String providersBlock) {
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            // Matches an optionally-commented "key = ..." line, so the default-on commented entries count as present.
            Pattern line = Pattern.compile("(?m)^\\s*#?\\s*" + Pattern.quote(key) + "\\s*=");
            if (!line.matcher(providersBlock).find()) {
                missing.add(key);
            }
        }
        return missing;
    }

    /** The keys whose mapped plugin name is not among the declared dependencies. The whole paper-plugin.yml detector. */
    private static List<String> missingFromPaperPlugin(Map<String, String> keyToPlugin, Set<String> declared) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : keyToPlugin.entrySet()) {
            if (!declared.contains(entry.getValue())) {
                missing.add(entry.getKey());
            }
        }
        return missing;
    }

    /** The {@code claims.providers { ... }} sub-block, so a key named elsewhere in config.conf cannot satisfy the guard. */
    private static String providersBlock() {
        String config = resource("/config.conf");
        int claimsAt = config.indexOf("claims {");
        assertThat(claimsAt).as("config.conf must declare a claims block").isGreaterThanOrEqualTo(0);
        int providersAt = config.indexOf("providers {", claimsAt);
        assertThat(providersAt)
                .as("the claims block must declare a providers block")
                .isGreaterThanOrEqualTo(0);
        return braceBlock(config, config.indexOf('{', providersAt));
    }

    /** The substring from the {@code '{'} at {@code open} through its matching {@code '}'}, by brace depth. */
    private static String braceBlock(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open, i + 1);
                }
            }
        }
        throw new IllegalStateException("unbalanced braces in the config.conf providers block");
    }

    /** The {@code dependencies.server} plugin names declared in {@code paper-plugin.yml}. */
    private static Set<String> declaredServerDependencies() {
        Map<?, ?> root = (Map<?, ?>) new Yaml().load(resource("/paper-plugin.yml"));
        Map<?, ?> dependencies = (Map<?, ?>) root.get("dependencies");
        Map<?, ?> server = (Map<?, ?>) dependencies.get("server");
        Set<String> names = new HashSet<>();
        for (Object key : server.keySet()) {
            names.add(String.valueOf(key));
        }
        return names;
    }

    private static String resource(String path) {
        try (InputStream in = ClaimProviderCoverageDriftTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing tracked resource on the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
