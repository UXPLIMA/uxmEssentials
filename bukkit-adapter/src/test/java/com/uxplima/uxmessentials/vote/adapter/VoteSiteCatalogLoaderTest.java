package com.uxplima.uxmessentials.vote.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link VoteSiteCatalogLoader}: the structured {@code sites} block parses correctly,
 * per-site cooldown overrides work, the module-level default is applied when absent, and the
 * legacy {@code vote-links} back-compat path seeds the catalog when {@code sites} is absent.
 */
class VoteSiteCatalogLoaderTest {

    private static final Logger NO_LOG = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @Test
    void parsesStructuredSitesBlock(@TempDir Path dir) throws IOException {
        String hocon = """
                default-cooldown-minutes = 1440
                sites = [
                  { name = "PlanetMinecraft", url = "https://planetminecraft.com", cooldown-minutes = 720 }
                  { name = "minecraft-mp" }
                ]
                """;
        VoteSiteCatalog catalog = load(dir, hocon);

        assertThat(catalog.sites()).hasSize(2);

        VoteSiteSpec pmc = catalog.sites().get(0);
        assertThat(pmc.name()).isEqualTo("PlanetMinecraft");
        assertThat(pmc.url()).contains("https://planetminecraft.com");
        assertThat(pmc.cooldown()).isEqualTo(Duration.ofHours(12));

        // Second site has no cooldown-minutes → falls back to default 1440.
        VoteSiteSpec mp = catalog.sites().get(1);
        assertThat(mp.name()).isEqualTo("minecraft-mp");
        assertThat(mp.url()).isEmpty();
        assertThat(mp.cooldown()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void parsesServiceFieldAndDefaultsToNameWhenAbsent(@TempDir Path dir) throws IOException {
        String hocon = """
                sites = [
                  { name = "PlanetMinecraft", service = "PMC", url = "https://planetminecraft.com" }
                  { name = "minecraft-mp" }
                ]
                """;
        VoteSiteCatalog catalog = load(dir, hocon);

        assertThat(catalog.sites()).hasSize(2);

        // Explicit service is kept separate from the display name.
        VoteSiteSpec pmc = catalog.sites().get(0);
        assertThat(pmc.name()).isEqualTo("PlanetMinecraft");
        assertThat(pmc.service()).isEqualTo("PMC");

        // Absent service defaults to the display name.
        VoteSiteSpec mp = catalog.sites().get(1);
        assertThat(mp.name()).isEqualTo("minecraft-mp");
        assertThat(mp.service()).isEqualTo("minecraft-mp");
    }

    @Test
    void appliesDefaultCooldownWhenSiteCooldownAbsent(@TempDir Path dir) throws IOException {
        String hocon = """
                default-cooldown-minutes = 60
                sites = [
                  { name = "TestSite" }
                ]
                """;
        VoteSiteCatalog catalog = load(dir, hocon);

        assertThat(catalog.sites()).hasSize(1);
        assertThat(catalog.sites().get(0).cooldown()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void legacyVoteLinksBackCompatSeedsTheCatalog(@TempDir Path dir) throws IOException {
        String hocon = """
                default-cooldown-minutes = 1440
                sites = []
                vote-links = [
                  "https://www.planetminecraft.com/server/myserver/"
                  "https://minecraft-mp.com/server/123/"
                ]
                """;
        VoteSiteCatalog catalog = load(dir, hocon);

        assertThat(catalog.sites()).hasSize(2);
        // Names are derived from the domain.
        assertThat(catalog.sites().get(0).name()).isEqualTo("planetminecraft.com");
        assertThat(catalog.sites().get(1).name()).isEqualTo("minecraft-mp.com");
        // URLs are preserved exactly.
        assertThat(catalog.sites().get(0).url()).contains("https://www.planetminecraft.com/server/myserver/");
        // All get the default cooldown.
        assertThat(catalog.sites().get(0).cooldown()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void emptySitesAndNoLinksYieldsEmptyCatalog(@TempDir Path dir) throws IOException {
        String hocon = "sites = []\nvote-links = []\n";
        VoteSiteCatalog catalog = load(dir, hocon);

        assertThat(catalog.sites()).isEmpty();
    }

    @Test
    void absentFileYieldsEmptyCatalog(@TempDir Path dir) {
        VoteSiteCatalog catalog = VoteSiteCatalogLoader.loadFrom(dir.resolve("nonexistent.conf"), NO_LOG);

        assertThat(catalog.sites()).isEmpty();
    }

    @Test
    void structuredSitesTakePrecedenceOverLegacyLinks(@TempDir Path dir) throws IOException {
        String hocon = """
                sites = [
                  { name = "PMC", url = "https://pmc.example", cooldown-minutes = 1440 }
                ]
                vote-links = [ "https://legacy.example" ]
                """;
        VoteSiteCatalog catalog = load(dir, hocon);

        // sites block wins, only one site, not two.
        assertThat(catalog.sites()).hasSize(1);
        assertThat(catalog.sites().get(0).name()).isEqualTo("PMC");
    }

    @Test
    void siteWithoutNameIsSkipped(@TempDir Path dir) throws IOException {
        String hocon = """
                sites = [
                  { url = "https://pmc.example", cooldown-minutes = 1440 }
                  { name = "ValidSite" }
                ]
                """;
        VoteSiteCatalog catalog = load(dir, hocon);

        // The entry without a name is skipped.
        assertThat(catalog.sites()).hasSize(1);
        assertThat(catalog.sites().get(0).name()).isEqualTo("ValidSite");
    }

    // --- helpers ---

    private static VoteSiteCatalog load(Path dir, String hocon) throws IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return VoteSiteCatalogLoader.loadFrom(file, NO_LOG);
    }
}
