package com.uxplima.uxmessentials.moderation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the warn-escalation and silent-broadcast config parse in {@link ModerationSettings}. Each
 * {@code count:action:duration} rung becomes an {@link EscalationStep}; a timed action without a duration, an
 * unknown action or a non-numeric count is skipped (logged) rather than failing the module; {@code
 * broadcast.silent-by-default} surfaces as a flag.
 */
class ModerationSettingsEscalationTest {

    @Test
    void parsesACountActionDurationRung() {
        ModerationSettings settings = settings(Map.of("warnings.actions", List.of("3:tempmute:1h")));

        WarnEscalation ladder = settings.warnEscalation();
        Optional<EscalationStep> step = ladder.stepFor(3);
        assertThat(step).isPresent();
        assertThat(step.get().atCount()).isEqualTo(3);
        assertThat(step.get().action()).isEqualTo(EscalationAction.TEMPMUTE);
        assertThat(step.get().duration()).contains(Duration.ofHours(1));
    }

    @Test
    void parsesAnUntimedRungWithNoDuration() {
        ModerationSettings settings = settings(Map.of("warnings.actions", List.of("5:ban")));

        Optional<EscalationStep> step = settings.warnEscalation().stepFor(5);
        assertThat(step).isPresent();
        assertThat(step.get().action()).isEqualTo(EscalationAction.BAN);
        assertThat(step.get().duration()).isEmpty();
    }

    @Test
    void skipsMalformedRungsButKeepsTheGoodOnes() {
        ModerationSettings settings = settings(Map.of(
                "warnings.actions",
                List.of(
                        "3:tempban", // timed action with no duration → skipped
                        "x:mute", // non-numeric count → skipped
                        "4:notanaction", // unknown action → skipped
                        "6:kick"))); // good

        WarnEscalation ladder = settings.warnEscalation();
        assertThat(ladder.steps()).hasSize(1);
        assertThat(ladder.stepFor(6)).isPresent();
        assertThat(ladder.stepFor(3)).isEmpty();
    }

    @Test
    void anEmptyOrAllMalformedListYieldsTheNoneLadder() {
        ModerationSettings settings = settings(Map.of("warnings.actions", List.of("garbage")));

        assertThat(settings.warnEscalation().steps()).isEmpty();
    }

    @Test
    void silentByDefaultDefaultsToFalseAndReadsTheFlag() {
        assertThat(settings(Map.of()).silentByDefault()).isFalse();
        assertThat(settings(Map.of("broadcast.silent-by-default", true)).silentByDefault())
                .isTrue();
    }

    private static ModerationSettings settings(Map<String, Object> values) {
        return new ModerationSettings(new MapConfig(values), new NoopLogger());
    }

    /** A flat path-keyed {@link ConfigStore} over an explicit value map, for the parse tests. */
    private record MapConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            Object value = values.get(path);
            return value instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            Object value = values.get(path);
            return value instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            Object value = values.get(path);
            return value instanceof Integer i ? i : fallback;
        }

        @Override
        @SuppressWarnings("unchecked") // the test supplies List<String> values under the list keys.
        public List<String> getStringList(String path, List<String> fallback) {
            Object value = values.get(path);
            return value instanceof List<?> list ? List.copyOf((List<String>) list) : fallback;
        }
    }

    /** A {@link Logger} that drops every line: the parse tests assert on the result, not the log. */
    private static final class NoopLogger implements Logger {
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
