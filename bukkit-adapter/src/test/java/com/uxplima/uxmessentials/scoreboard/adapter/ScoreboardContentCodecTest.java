package com.uxplima.uxmessentials.scoreboard.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class ScoreboardContentCodecTest {

    private static final Logger LOG = new NoopLogger();

    private static ConditionContext ctx(Predicate<String> hasPermission, String world, String gamemode) {
        Function<String, String> resolve = s -> Map.<String, String>of().getOrDefault(s, s);
        return new ConditionContext(hasPermission, world, gamemode, resolve);
    }

    @Test
    void backCompatWrapsTheTopLevelScoreboardBlockAsOneDefaultBoard(@TempDir Path dir) throws Exception {
        // The historical single-board shape: no boards{} block, just a top-level scoreboard{} block.
        ConfigurationNode root = load(dir, """
                enabled = true
                scoreboard {
                  title = "<gold>My Server"
                  lines = [ "<gray>Online: <white>%server_online%", "" ]
                  hide-score-numbers = false
                  refresh-ticks = 40
                  world-blacklist = [ "world_the_end", "world_nether" ]
                }
                """);

        ScoreboardContentCodec.Parsed parsed = ScoreboardContentCodec.read(root, LOG);

        // 40 ticks at 50ms each is two seconds: the global cadence comes from scoreboard.refresh-ticks here.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(parsed.boards().boards()).hasSize(1);
        SidebarBoard board = parsed.boards().boards().get(0);
        assertThat(board.name()).isEqualTo("default");
        // The implicit default board carries an always-true condition.
        assertThat(board.condition().matches(ctx(n -> false, "world", "SURVIVAL")))
                .isTrue();
        assertThat(board.priority()).isZero();
        DisplayContent content = board.content();
        assertThat(content.title()).contains("<gold>My Server");
        assertThat(content.lines()).containsExactly("<gray>Online: <white>%server_online%", "");
        assertThat(content.hideScoreNumbers()).isFalse();
        assertThat(content.worldBlacklist()).containsExactlyInAnyOrder("world_the_end", "world_nether");
    }

    @Test
    void parsesNamedBoardsWithConditionsAndPriorities(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                enabled = true
                refresh-ticks = 10
                boards {
                  staff {
                    condition = "permission:uxmessentials.staff"
                    priority = 10
                    title = "<red>Staff"
                    lines = [ "<gray>Mode: <white>staff" ]
                    hide-score-numbers = true
                  }
                  default {
                    condition = ""
                    priority = 0
                    title = "<gold>Server"
                    lines = [ "<gray>Welcome" ]
                  }
                }
                """);

        ScoreboardContentCodec.Parsed parsed = ScoreboardContentCodec.read(root, LOG);

        // The global cadence is the top-level refresh-ticks (10 ticks = 500ms).
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofMillis(500L));
        SidebarConfig boards = parsed.boards();
        // HOCON does not preserve declaration order, so the codec emits the boards sorted by name for a deterministic
        // tie-break: both boards are present regardless of how they were declared.
        assertThat(boards.boards()).extracting(SidebarBoard::name).containsExactly("default", "staff");

        // A staff sees the staff board; a non-staff falls through to the always-true default.
        assertThat(boards.select(ctx("uxmessentials.staff"::equals, "world", "SURVIVAL")))
                .map(SidebarBoard::name)
                .contains("staff");
        assertThat(boards.select(ctx(n -> false, "world", "SURVIVAL")))
                .map(SidebarBoard::name)
                .contains("default");
    }

    @Test
    void anEmptyRootYieldsNoBoards(@TempDir Path dir) throws Exception {
        ScoreboardContentCodec.Parsed parsed = ScoreboardContentCodec.read(load(dir, "enabled = false\n"), LOG);

        assertThat(parsed.boards().boards()).isEmpty();
        assertThat(parsed.refreshInterval()).isPositive();
    }

    @Test
    void aBlankSingleBoardYieldsNoBoards(@TempDir Path dir) throws Exception {
        // The shipped inert default: a scoreboard block with no title and no lines authors nothing.
        ScoreboardContentCodec.Parsed parsed =
                ScoreboardContentCodec.read(load(dir, "scoreboard {\n  title = \"\"\n  lines = []\n}\n"), LOG);

        assertThat(parsed.boards().boards()).isEmpty();
    }

    @Test
    void candidateLinesAreRetainedUntilViewerFilteringAndANonPositiveCadenceFallsBack(@TempDir Path dir)
            throws Exception {
        StringBuilder lines = new StringBuilder("lines = [ ");
        for (int i = 0; i < DisplayContent.MAX_LINES + 5; i++) {
            lines.append("\"line ").append(i).append("\", ");
        }
        lines.append("]");
        ConfigurationNode root =
                load(dir, "scoreboard {\n  title = \"<gold>T\"\n  " + lines + "\n  refresh-ticks = 0\n}\n");

        ScoreboardContentCodec.Parsed parsed = ScoreboardContentCodec.read(root, LOG);

        SidebarBoard board = parsed.boards().boards().get(0);
        assertThat(board.content().lines()).hasSize(DisplayContent.MAX_LINES + 5);
        // An absent hide-score-numbers defaults to true, the modern look.
        assertThat(board.content().hideScoreNumbers()).isTrue();
        // A non-positive refresh-ticks falls back to one second (20 ticks) rather than busy-spinning.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(1L));
    }

    @Test
    void parsesTypedLinesWithStableIdsConditionsAndPerLineFormats(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                scoreboard {
                  title = "<gold>Server"
                  lines = [
                    { id = "heading", text = "<yellow>Profile", number-format = "blank" },
                    {
                      id = "balance"
                      text = "<gray>Balance"
                      condition = "permission:uxmessentials.balance"
                      hide-when-empty = true
                      number-format = { type = "fixed", text = "<gold>{coins}" }
                    }
                  ]
                }
                """);

        DisplayContent content = ScoreboardContentCodec.read(root, LOG)
                .boards()
                .boards()
                .getFirst()
                .content();

        assertThat(content.lineDefinitions()).extracting(l -> l.id()).containsExactly("heading", "balance");
        assertThat(content.lineDefinitions().get(1).hideWhenEmpty()).isTrue();
        assertThat(content.lineDefinitions().get(1).numberFormat())
                .isEqualTo(new com.uxplima.uxmessentials.scoreboard.domain.SidebarNumberFormat.Fixed("<gold>{coins}"));
        assertThat(content.lineDefinitions()
                        .get(1)
                        .condition()
                        .matches(ctx("uxmessentials.balance"::equals, "world", "SURVIVAL")))
                .isTrue();
    }

    @Test
    void legacyRightTextShorthandBecomesAFixedNumberFormat(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                scoreboard {
                  title = "T"
                  lines = [ "<gray>Balance||<gold>{coins}", "" ]
                }
                """);

        DisplayContent content = ScoreboardContentCodec.read(root, LOG)
                .boards()
                .boards()
                .getFirst()
                .content();

        assertThat(content.lineDefinitions().getFirst().text()).isEqualTo("<gray>Balance");
        assertThat(content.lineDefinitions().getFirst().numberFormat())
                .isEqualTo(new com.uxplima.uxmessentials.scoreboard.domain.SidebarNumberFormat.Fixed("<gold>{coins}"));
        assertThat(content.lineDefinitions().get(1).text()).isEmpty();
        assertThat(content.lineDefinitions().get(1).hideWhenEmpty()).isFalse();
    }

    @Test
    void parsesFramesAndScrollAnimationsAndSkipsAnUnknownType(@TempDir Path dir) throws Exception {
        RecordingLogger log = new RecordingLogger();
        ConfigurationNode root = load(dir, """
                enabled = true
                animations {
                  blink { type = "FRAMES", frames = [ "ON", "OFF" ], interval-ticks = 10 }
                  marquee { type = "SCROLL", frames = [ "welcome to the server" ], interval-ticks = 2, window = 8, separator = " * " }
                  bogus { type = "WOBBLE", frames = [ "x" ] }
                }
                scoreboard { title = "<gold>T", lines = [ "<gray>L" ] }
                """);

        ScoreboardContentCodec.Parsed parsed = ScoreboardContentCodec.read(root, log);

        // The two valid animations are parsed (sorted by HOCON's alphabetical key order); the unknown type is skipped.
        assertThat(parsed.animations()).extracting(d -> d.spec().name()).containsExactlyInAnyOrder("blink", "marquee");
        AnimationDef blink = byName(parsed.animations(), "blink");
        assertThat(blink.spec().type()).isEqualTo(AnimationSpec.AnimationType.FRAMES);
        assertThat(blink.spec().frames()).containsExactly("ON", "OFF");
        assertThat(blink.spec().intervalTicks()).isEqualTo(10);
        AnimationDef marquee = byName(parsed.animations(), "marquee");
        assertThat(marquee.spec().type()).isEqualTo(AnimationSpec.AnimationType.SCROLL);
        assertThat(marquee.scroll()).hasValueSatisfying(s -> {
            assertThat(s.window()).isEqualTo(8);
            assertThat(s.separator()).isEqualTo(" * ");
        });
        // The skip is logged once with the bad type and name so an operator can spot the typo.
        assertThat(log.warnings).anyMatch(w -> w.contains("animation_skipped") && w.contains("bogus"));
    }

    private static AnimationDef byName(List<AnimationDef> defs, String name) {
        return defs.stream()
                .filter(d -> d.spec().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ConfigurationNode load(Path dir, String hocon) throws ConfigurateException, IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return HoconConfigurationLoader.builder().path(file).build().load();
    }

    private static class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A logger that captures formatted {@code warn} lines so a test can assert a skip was reported. */
    private static final class RecordingLogger extends NoopLogger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void warn(String message, Object... args) {
            warnings.add(format(message, args));
        }

        private static String format(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                int at = out.indexOf("{}");
                if (at < 0) {
                    break;
                }
                out = out.substring(0, at) + arg + out.substring(at + 2);
            }
            return out;
        }
    }
}
