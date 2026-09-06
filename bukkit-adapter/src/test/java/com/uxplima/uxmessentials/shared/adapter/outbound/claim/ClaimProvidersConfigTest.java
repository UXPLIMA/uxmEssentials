package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * The typo-key guard on {@link ClaimProvidersConfig#from}. A key an operator writes under {@code claims.providers}
 * that matches no registered provider disables nothing, the safe direction, but is silent, so the operator's
 * intended disable never takes without any signal. {@code from} must warn once per unrecognised key, naming it, so
 * that misfire is visible; a key that does match a provider must stay quiet and still take effect.
 */
class ClaimProvidersConfigTest {

    @Test
    void anUnknownKeyLogsOneWarningNamingIt() {
        RecordingLogger log = new RecordingLogger();

        ClaimProvidersConfig.from(new Providers(Map.of("lnads", false)), log);

        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0).args()).contains("lnads");
        assertThat(log.warnings.get(0).message()).contains("unknown_key");
    }

    @Test
    void anUnknownKeyWarnsEvenWhenSetTrue() {
        // The value is irrelevant: any key that matches no provider is a typo the operator should hear about.
        RecordingLogger log = new RecordingLogger();

        ClaimProvidersConfig.from(new Providers(Map.of("lnads", true)), log);

        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0).args()).contains("lnads");
    }

    @Test
    void anUnknownCombineTokenWarnsAndFallsBackToAnyLand() {
        // A mistyped combine must not silently loosen the gate from all-land to any-land without a word.
        RecordingLogger log = new RecordingLogger();

        ClaimProvidersConfig config = ClaimProvidersConfig.from(new Providers(Map.of(), "all_land"), log);

        assertThat(config.combine()).isEqualTo(ClaimProvidersConfig.CombineMode.ANY_LAND);
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0).args()).contains("all_land");
        assertThat(log.warnings.get(0).message()).contains("claim_combine_unknown");
    }

    @Test
    void aKnownCombineTokenDoesNotWarn() {
        RecordingLogger log = new RecordingLogger();

        ClaimProvidersConfig config = ClaimProvidersConfig.from(new Providers(Map.of(), "all-land"), log);

        assertThat(config.combine()).isEqualTo(ClaimProvidersConfig.CombineMode.ALL_LAND);
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aKnownKeyDoesNotWarnAndStillTakesEffect() {
        RecordingLogger log = new RecordingLogger();

        ClaimProvidersConfig config = ClaimProvidersConfig.from(new Providers(Map.of("lands", false)), log);

        assertThat(log.warnings).isEmpty();
        assertThat(config.enabled("lands")).isFalse();
        assertThat(config.enabled("worldguard")).isTrue();
    }

    @Test
    void aKnownDisableSurvivesAlongsideAnUnknownKey() {
        RecordingLogger log = new RecordingLogger();
        Map<String, Boolean> written = new LinkedHashMap<>();
        written.put("lands", false);
        written.put("lnads", false);

        ClaimProvidersConfig config = ClaimProvidersConfig.from(new Providers(written), log);

        assertThat(config.enabled("lands")).isFalse();
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0).args()).contains("lnads");
    }

    /** A {@link ConfigStore} exposing exactly the given {@code claims.providers} keys and their boolean values. */
    private record Providers(Map<String, Boolean> providers, String combine) implements ConfigStore {

        private static final String PREFIX = "claims.providers.";

        Providers(Map<String, Boolean> providers) {
            this(providers, "any-land");
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            if (path.startsWith(PREFIX)) {
                Boolean value = providers.get(path.substring(PREFIX.length()));
                return value != null ? value : fallback;
            }
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return "claims.combine".equals(path) ? combine : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }

        @Override
        public List<String> getKeys(String path) {
            return "claims.providers".equals(path) ? List.copyOf(providers.keySet()) : List.of();
        }
    }

    /** Captures every {@code warn} call so the typo path can be asserted on. */
    private static final class RecordingLogger implements Logger {

        private final List<Warning> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(new Warning(message, Arrays.asList(args)));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private record Warning(String message, List<Object> args) {}
}
