package com.uxplima.uxmessentials.vote.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Verifies that the {@code voteparty} block parses into a {@link PartyConfig} correctly. The test
 * mirrors the production parsing logic in {@code VoteWiring} (package-private, so duplicated here)
 * and exercises threshold, onlyVoters, escalateBy, reset schedule, announceAt, the legacy flat
 * {@code rewards} list, and the {@code streak.grace-days} read that flows into {@code HandleVote}.
 *
 * <p>No MockBukkit is needed: the test is a pure-Java Configurate HOCON parse.
 */
class PartyConfigLoaderTest {

    private static final String FULL_CONFIG = """
            voteparty {
              threshold = 50
              only-voters = true
              escalate-by = 5
              reset = "weekly"
              announce-at = ["10", "25", "40"]
              rewards = ["eco give {player} 500", "give {player} diamond 1"]
              sound = ""
              particle = ""
            }
            """;

    private static final String MINIMAL_CONFIG = """
            voteparty {
              threshold = 10
            }
            """;

    @Test
    void parsesFullVotepartyBlock() throws ConfigurateException {
        PartyConfig cfg = parsePartyConfig(storeFrom(FULL_CONFIG));

        assertThat(cfg.baseThreshold()).isEqualTo(50);
        assertThat(cfg.onlyVoters()).isTrue();
        assertThat(cfg.escalateBy()).isEqualTo(5);
        assertThat(cfg.resetSchedule()).isEqualTo(PartyResetSchedule.WEEKLY);
        assertThat(cfg.announceAt()).containsExactlyInAnyOrder(10, 25, 40);
    }

    @Test
    void legacyFlatRewardsListWrapsAsCommandSpec() throws ConfigurateException {
        PartyConfig cfg = parsePartyConfig(storeFrom(FULL_CONFIG));

        assertThat(cfg.reward().commands()).containsExactly("eco give {player} 500", "give {player} diamond 1");
    }

    @Test
    void minimalistConfigFallsBackToDefaults() throws ConfigurateException {
        PartyConfig cfg = parsePartyConfig(storeFrom(MINIMAL_CONFIG));

        assertThat(cfg.baseThreshold()).isEqualTo(10);
        assertThat(cfg.onlyVoters()).isFalse();
        assertThat(cfg.escalateBy()).isEqualTo(0);
        assertThat(cfg.resetSchedule()).isEqualTo(PartyResetSchedule.NONE);
        assertThat(cfg.announceAt()).isEmpty();
        assertThat(cfg.reward().commands()).isEmpty();
    }

    @Test
    void resetDailyParsesCorrectly() throws ConfigurateException {
        ConfigStore store = storeFrom("voteparty { threshold = 5, reset = \"daily\" }");
        assertThat(parsePartyConfig(store).resetSchedule()).isEqualTo(PartyResetSchedule.DAILY);
    }

    @Test
    void resetNoneParsesCorrectly() throws ConfigurateException {
        ConfigStore store = storeFrom("voteparty { threshold = 5, reset = \"none\" }");
        assertThat(parsePartyConfig(store).resetSchedule()).isEqualTo(PartyResetSchedule.NONE);
    }

    @Test
    void unknownResetValueFallsBackToNone() throws ConfigurateException {
        ConfigStore store = storeFrom("voteparty { threshold = 5, reset = \"monthly\" }");
        assertThat(parsePartyConfig(store).resetSchedule()).isEqualTo(PartyResetSchedule.NONE);
    }

    @Test
    void streakGraceDaysParsesConfiguredValue() throws ConfigurateException {
        ConfigStore store = storeFrom("streak { grace-days = 2 }");
        assertThat(parseStreakGraceDays(store)).isEqualTo(2);
    }

    @Test
    void streakGraceDaysDefaultsToZeroWhenAbsent() throws ConfigurateException {
        ConfigStore store = storeFrom("voteparty { threshold = 5 }");
        assertThat(parseStreakGraceDays(store)).isEqualTo(0);
    }

    @Test
    void streakGraceDaysClampsNegativeToZero() throws ConfigurateException {
        ConfigStore store = storeFrom("streak { grace-days = -3 }");
        assertThat(parseStreakGraceDays(store)).isEqualTo(0);
    }

    // --- helpers ---

    /** Mirrors the production read in {@code VoteWiring.wire(...)} for the streak grace window. */
    private static int parseStreakGraceDays(ConfigStore config) {
        return Math.max(0, config.getInt("streak.grace-days", 0));
    }

    private static ConfigStore storeFrom(String hocon) throws ConfigurateException {
        ConfigurationNode node = HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
        return new NodeConfigStore(node);
    }

    /** Mirrors the production parsing logic in {@code VoteWiring.partyConfig(ConfigStore)}. */
    private static PartyConfig parsePartyConfig(ConfigStore config) {
        List<String> legacyCommands = config.getStringList("voteparty.rewards", List.of());
        RewardSpec reward;
        if (legacyCommands.isEmpty()) {
            reward = new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        } else {
            reward = new RewardSpec(100, Optional.empty(), legacyCommands, List.of(), List.of(), List.of(), Set.of());
        }
        int threshold = Math.max(1, config.getInt("voteparty.threshold", 25));
        boolean onlyVoters = config.getBoolean("voteparty.only-voters", false);
        int escalateBy = Math.max(0, config.getInt("voteparty.escalate-by", 0));
        PartyResetSchedule resetSchedule = parseResetSchedule(config.getString("voteparty.reset", "none"));
        List<Integer> announceAt = parseAnnounceAt(config.getStringList("voteparty.announce-at", List.of()));
        return new PartyConfig(reward, threshold, onlyVoters, escalateBy, resetSchedule, announceAt);
    }

    private static PartyResetSchedule parseResetSchedule(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT).strip()) {
            case "daily" -> PartyResetSchedule.DAILY;
            case "weekly" -> PartyResetSchedule.WEEKLY;
            default -> PartyResetSchedule.NONE;
        };
    }

    private static List<Integer> parseAnnounceAt(List<String> raw) {
        List<Integer> result = new ArrayList<>();
        for (String s : raw) {
            try {
                result.add(Integer.parseInt(s.strip()));
            } catch (NumberFormatException ignored) {
                // tolerate malformed entries
            }
        }
        return List.copyOf(result);
    }

    /**
     * A {@link ConfigStore} backed by a Configurate {@link ConfigurationNode}. Path components are
     * dot-separated (e.g. {@code "voteparty.threshold"} reads {@code root.node("voteparty","threshold")}).
     */
    private record NodeConfigStore(ConfigurationNode root) implements ConfigStore {

        private ConfigurationNode resolve(String path) {
            String[] parts = path.split("\\.", -1);
            return root.node((Object[]) parts);
        }

        @Override
        public int getInt(String path, int fallback) {
            return resolve(path).getInt(fallback);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return resolve(path).getBoolean(fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            @Nullable String value = resolve(path).getString();
            return value != null ? value : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            ConfigurationNode node = resolve(path);
            if (node.virtual() || !node.isList()) {
                return fallback;
            }
            List<String> result = new ArrayList<>();
            for (ConfigurationNode child : node.childrenList()) {
                @Nullable String v = child.getString();
                if (v != null) {
                    result.add(v);
                }
            }
            return result.isEmpty() ? fallback : List.copyOf(result);
        }
    }
}
