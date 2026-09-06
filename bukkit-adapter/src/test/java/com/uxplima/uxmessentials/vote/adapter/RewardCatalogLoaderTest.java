package com.uxplima.uxmessentials.vote.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vote.domain.reward.ItemReward;
import com.uxplima.uxmessentials.vote.domain.reward.MilestoneReward;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import com.uxplima.uxmessentials.vote.domain.reward.StreakReward;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parses a sample {@code rewards} block and asserts the resulting {@link RewardCatalog}: that per-vote specs
 * carry their chance and items, that per-site keys land under their service name, that a first-vote spec is
 * read, and that both milestone shapes ({@code at} and {@code every}) and both streak shapes are parsed.
 * Also checks the tolerant defaults. An absent block and malformed entries yield an empty/clean catalog
 * rather than failing.
 */
class RewardCatalogLoaderTest {

    private static final String SAMPLE = """
            enabled = true
            rewards {
              per-vote = [
                {
                  chance = 75
                  commands = [ "eco give {player} 250" ]
                  messages = [ "<green>Thanks {player}" ]
                  items = [ { material = "DIAMOND", amount = 3, name = "<aqua>Vote Diamond", lore = [ "<gray>nice" ] } ]
                }
              ]
              per-site {
                "PlanetMinecraft" = [ { commands = [ "give {player} emerald 2" ] } ]
              }
              first-vote = [ { messages = [ "<green>Welcome {player}" ] } ]
              milestones = [
                { at = 100, broadcast = [ "<gold>{player} hit 100" ] }
                { every = 25, commands = [ "crate give {player} vote 1" ] }
              ]
              streaks = [
                { at = 7, broadcast = [ "<gold>{player} voted 7 days running" ] }
                { every = 30, commands = [ "crate give {player} streak 1" ] }
                { commands = [ "no-length" ] }
              ]
            }
            """;

    @Test
    void parsesEverySectionOfASampleConfig(@TempDir Path dir) throws IOException {
        RewardCatalog catalog = load(dir, SAMPLE);

        assertThat(catalog.perVote()).hasSize(1);
        RewardSpec perVote = catalog.perVote().get(0);
        assertThat(perVote.chancePercent()).isEqualTo(75);
        assertThat(perVote.commands()).containsExactly("eco give {player} 250");
        assertThat(perVote.items()).hasSize(1);
        ItemReward item = perVote.items().get(0);
        assertThat(item.material()).isEqualTo("DIAMOND");
        assertThat(item.amount()).isEqualTo(3);
        assertThat(item.name()).contains("<aqua>Vote Diamond");
        assertThat(item.lore()).containsExactly("<gray>nice");
    }

    @Test
    void perSiteIsKeyedByServiceNameCaseInsensitively(@TempDir Path dir) throws IOException {
        RewardCatalog catalog = load(dir, SAMPLE);

        assertThat(catalog.perSite()).containsKey("planetminecraft");
        // forSite matches case-insensitively, so the original casing from a Votifier event resolves too.
        assertThat(catalog.forSite("PLANETMINECRAFT")).hasSize(1);
        assertThat(catalog.forSite("PlanetMinecraft").get(0).commands()).containsExactly("give {player} emerald 2");
    }

    @Test
    void firstVoteSpecIsParsed(@TempDir Path dir) throws IOException {
        RewardCatalog catalog = load(dir, SAMPLE);

        assertThat(catalog.firstVote()).hasSize(1);
        assertThat(catalog.firstVote().get(0).messages()).containsExactly("<green>Welcome {player}");
    }

    @Test
    void bothMilestoneShapesAreParsed(@TempDir Path dir) throws IOException {
        RewardCatalog catalog = load(dir, SAMPLE);

        assertThat(catalog.milestones()).hasSize(2);
        MilestoneReward atMilestone = catalog.milestones().get(0);
        assertThat(atMilestone.at()).hasValue(100L);
        assertThat(atMilestone.every()).isEmpty();
        assertThat(atMilestone.matches(100L)).isTrue();

        MilestoneReward everyMilestone = catalog.milestones().get(1);
        assertThat(everyMilestone.every()).hasValue(25L);
        assertThat(everyMilestone.at()).isEmpty();
        assertThat(everyMilestone.matches(50L)).isTrue();
    }

    @Test
    void bothStreakShapesAreParsedAndMalformedSkipped(@TempDir Path dir) throws IOException {
        RewardCatalog catalog = load(dir, SAMPLE);

        // The third streak entry has neither at nor every, so it is skipped; only the two valid ones remain.
        assertThat(catalog.streaks()).hasSize(2);
        StreakReward atStreak = catalog.streaks().get(0);
        assertThat(atStreak.at()).hasValue(7L);
        assertThat(atStreak.every()).isEmpty();
        assertThat(atStreak.matches(7L)).isTrue();
        assertThat(atStreak.reward().broadcasts()).containsExactly("<gold>{player} voted 7 days running");

        StreakReward everyStreak = catalog.streaks().get(1);
        assertThat(everyStreak.every()).hasValue(30L);
        assertThat(everyStreak.at()).isEmpty();
        assertThat(everyStreak.matches(60L)).isTrue();
        assertThat(everyStreak.reward().commands()).containsExactly("crate give {player} streak 1");
    }

    @Test
    void absentRewardsBlockYieldsEmptyCatalog(@TempDir Path dir) throws IOException {
        RewardCatalog catalog = load(dir, "enabled = true\n");

        assertThat(catalog.perVote()).isEmpty();
        assertThat(catalog.perSite()).isEmpty();
        assertThat(catalog.firstVote()).isEmpty();
        assertThat(catalog.milestones()).isEmpty();
        assertThat(catalog.streaks()).isEmpty();
    }

    @Test
    void malformedEntriesAreSkippedNotFatal(@TempDir Path dir) throws IOException {
        // A milestone with neither at nor every, and an item with no material, are both dropped.
        RewardCatalog catalog = load(dir, """
                rewards {
                  per-vote = [ { commands = [ "ok" ], items = [ { amount = 2 }, { material = "STONE" } ] } ]
                  milestones = [ { commands = [ "no-threshold" ] } ]
                }
                """);

        assertThat(catalog.milestones()).isEmpty();
        assertThat(catalog.perVote()).hasSize(1);
        // The item with no material is dropped; the valid STONE survives.
        assertThat(catalog.perVote().get(0).items()).hasSize(1);
        assertThat(catalog.perVote().get(0).items().get(0).material()).isEqualTo("STONE");
    }

    @Test
    void absentFileYieldsEmptyCatalog(@TempDir Path dir) {
        RewardCatalog catalog = RewardCatalogLoader.loadFrom(dir.resolve("missing.conf"), new NoLog());

        assertThat(catalog).usingRecursiveComparison().isEqualTo(RewardCatalog.empty());
    }

    private RewardCatalog load(Path dir, String hocon) throws IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return RewardCatalogLoader.loadFrom(file, new NoLog());
    }

    /** Swallows log output; the loader never logs on the happy path, and we do not assert on logs here. */
    private static final class NoLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
