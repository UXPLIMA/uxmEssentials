package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A shipped menu spec may carry a paged list's knobs (a {@code sorts} list or an explicit non-zero {@code page-size})
 * only on a source production wiring registers as a paged one. A plain in-memory source hands its whole corpus to the
 * engine and can act on neither knob, so pairing them there is a silent lie: the operator wires a sort button and it
 * renders but never sorts. This guard turns that mismatch into a boot-time failure a maintainer meets instead of a
 * dead button a player meets. It walks every bundled engine spec and, for each list-backed item that declares either
 * knob, asserts the source id is one production registers as paged.
 *
 * <p>{@link #PAGED_SOURCES} is the contract. It mirrors the ids the feature wiring passes to
 * {@code MenuBindings.pagedList}. It is empty today because no shipped menu pages its list yet: playerwarps' paged
 * browse ({@code playerwarps:browse}) lands here in the same change that wires it. So the guard passes now precisely
 * because no shipped spec pairs {@code sorts} or a {@code page-size} with a source that is not a registered paged
 * source. The first test asserts that state; the second proves the guard has teeth by firing the same detector on a
 * synthetic spec that violates it, so a green result is a checked fact rather than a vacuous pass.
 */
class PagedListSourceDriftTest {

    /** Source ids production wiring registers as paged (via {@code MenuBindings.pagedList}). Keep in sync with it. */
    private static final Set<String> PAGED_SOURCES = Set.of("playerwarps:browse");

    @Test
    void noShippedSpecPagesANonPagedSource() {
        Path modulesDir = repoRoot().resolve("bukkit-adapter/src/main/resources/modules");
        if (!Files.isDirectory(modulesDir)) {
            return;
        }

        assertThat(pagedKeyDrift(loadAll(modulesDir), PAGED_SOURCES))
                .as("a shipped spec declares page-size/sorts on a source not registered as a paged source; register "
                        + "the source through MenuBindings.pagedList (and add its id to PAGED_SOURCES here), or drop "
                        + "those keys")
                .isEmpty();
    }

    @Test
    void theGuardFiresOnASpecThatPagesANonPagedSource() {
        MenuSpecLoader loader = new MenuSpecLoader();
        MenuSpec sortsOnUnpagedSource = loader.parse(listMenu("playerwarps:list", "", "sorts = [\"RATING\"]"));
        MenuSpec pageSizeOnUnpagedSource = loader.parse(listMenu("playerwarps:list", "page-size = 45", ""));
        MenuSpec pagesARegisteredPagedSource =
                loader.parse(listMenu("playerwarps:browse", "page-size = 45", "sorts = [\"RATING\"]"));

        assertThat(pagedKeyDrift(List.of(sortsOnUnpagedSource), PAGED_SOURCES))
                .as("a sorts list on a source that is not registered as paged must be reported")
                .singleElement()
                .asString()
                .contains("playerwarps:list");
        assertThat(pagedKeyDrift(List.of(pageSizeOnUnpagedSource), PAGED_SOURCES))
                .as("a non-zero page-size on a source that is not registered as paged must be reported")
                .singleElement()
                .asString()
                .contains("playerwarps:list");
        assertThat(pagedKeyDrift(List.of(pagesARegisteredPagedSource), Set.of("playerwarps:browse")))
                .as("the very same knobs are clean once the source is a registered paged source")
                .isEmpty();
    }

    /**
     * Every list-backed item declaring a paged knob, a non-zero page-size or any sorts, whose source id is not in
     * {@code pagedSources}, named by menu, item and source so the offending {@code .conf} line is easy to find. This is
     * the guard's whole detector: the shipped-spec test runs it over the bundled corpus and the teeth test runs it over
     * synthetic specs, so both exercise identical logic.
     */
    private static List<String> pagedKeyDrift(Collection<MenuSpec> specs, Set<String> pagedSources) {
        List<String> drift = new ArrayList<>();
        for (MenuSpec spec : specs) {
            for (Map.Entry<String, MenuItemSpec> entry : spec.items().entrySet()) {
                entry.getValue().list().ifPresent(list -> {
                    boolean pages = list.pageSize() != 0 || !list.sorts().isEmpty();
                    if (pages && !pagedSources.contains(list.source().id())) {
                        drift.add("menu '" + spec.title() + "' item '" + entry.getKey() + "' list source '"
                                + list.source().id()
                                + "' declares page-size/sorts but is not registered as a paged source");
                    }
                });
            }
        }
        return drift;
    }

    /**
     * A one-item menu whose single list item is backed by {@code source}, optionally carrying a {@code page-size} and a
     * {@code sorts} line. The template is inert so the only thing under test is the list source and its paged knobs.
     */
    private static String listMenu(String source, String pageSizeLine, String sortsLine) {
        return """
                title = "Browse"
                rows = 6
                items {
                  warps {
                    slots = ["0-44"]
                    list {
                      source = "%s"
                      %s
                      %s
                      template { material = STONE, name = "entry" }
                    }
                  }
                }
                """.formatted(source, pageSizeLine, sortsLine);
    }

    /**
     * Parses every engine spec shipped under a module's {@code gui/} folder. The same enumeration the sibling shipped
     * spec guards walk. A parse failure propagates as the drift it is. A layout {@code .conf} carries no {@code items}
     * block, so those geometry files are skipped rather than parsed as specs.
     */
    private static List<MenuSpec> loadAll(Path modulesDir) {
        MenuSpecLoader loader = new MenuSpecLoader();
        List<MenuSpec> specs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(modulesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .filter(PagedListSourceDriftTest::inGuiFolder)
                    .filter(PagedListSourceDriftTest::isEngineSpec)
                    .sorted()
                    .forEach(p -> specs.add(loader.load(p)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return specs;
    }

    /** True when {@code conf} sits directly in a {@code gui/} folder, where every built-in menu spec lives. */
    private static boolean inGuiFolder(Path conf) {
        Path parent = conf.getParent();
        return parent != null && parent.getFileName().toString().equals("gui");
    }

    /** True when {@code conf} is an engine menu spec: it declares an {@code items} block; a layout conf does not. */
    private static boolean isEngineSpec(Path conf) {
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(conf).build().load();
            return !root.node("items").virtual();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
