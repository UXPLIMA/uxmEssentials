package com.uxplima.uxmessentials.nametags.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class NametagContentCodecTest {

    private static final Logger LOG = new NoopLogger();

    @Test
    void parsesTheFormatsBlockWithAppearanceAndVisibility(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                refresh-ticks = 40
                formats {
                  staff {
                    condition = "permission:uxmessentials.staff"
                    priority = 10
                    lines = [ "<red>[Staff]", "<white>{player}" ]
                    appearance {
                      billboard = "FIXED"
                      scale = 1.5
                      y-offset = 0.5
                      see-through = true
                      glow-color = "#FF5555"
                      line-width = 200
                      view-range = 2.0
                    }
                    visibility {
                      show-when = "world:world"
                      hide-while-sneaking = true
                      respect-vanish = false
                      viewer-distance = 64
                    }
                  }
                  default {
                    condition = ""
                    priority = 0
                    lines = [ "<gray>{player}" ]
                  }
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, LOG);

        // 40 ticks at 50ms each is two seconds; the global cadence is read from the top-level refresh-ticks.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(parsed.formats().formats()).hasSize(2);

        NametagFormat staff = select(parsed, "uxmessentials.staff"::equals, "world");
        assertThat(staff.name()).isEqualTo("staff");
        assertThat(staff.priority()).isEqualTo(10);
        assertThat(staff.lines()).containsExactly("<red>[Staff]", "<white>{player}");
        assertThat(staff.appearance().billboard()).isEqualTo("FIXED");
        assertThat(staff.appearance().scale()).isEqualTo(1.5);
        assertThat(staff.appearance().yOffset()).isEqualTo(0.5);
        assertThat(staff.appearance().seeThrough()).isTrue();
        assertThat(staff.appearance().glowColor()).contains("#FF5555");
        assertThat(staff.appearance().lineWidth()).hasValue(200);
        assertThat(staff.appearance().viewRange()).hasValue(2.0);
        assertThat(staff.visibility().hideWhileSneaking()).isTrue();
        assertThat(staff.visibility().respectVanish()).isFalse();
        // viewer-distance is the cull block radius, parsed separately from the appearance view-range multiplier.
        assertThat(staff.visibility().viewerDistance()).hasValue(64.0);

        NametagFormat fallback = select(parsed, n -> false, "world");
        assertThat(fallback.name()).isEqualTo("default");
        assertThat(fallback.lines()).containsExactly("<gray>{player}");
        // An unauthored appearance/visibility falls back to the safe defaults.
        assertThat(fallback.appearance().billboard()).isEqualTo("CENTER");
        assertThat(fallback.visibility().respectVanish()).isTrue();
        // An unauthored viewer-distance is empty so the renderer applies its default cull radius.
        assertThat(fallback.visibility().viewerDistance()).isEmpty();
    }

    @Test
    void keepsAZeroViewerDistanceButTreatsANegativeOneAsAbsent(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                formats {
                  show-all {
                    condition = ""
                    priority = 0
                    lines = [ "<white>{player}" ]
                    visibility { viewer-distance = 0 }
                  }
                  typo {
                    condition = "permission:uxmessentials.staff"
                    priority = 10
                    lines = [ "<red>{player}" ]
                    visibility { viewer-distance = -5 }
                  }
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, LOG);

        NametagFormat showAll = select(parsed, n -> false, "world");
        // 0 is kept verbatim: it disables the renderer's distance cull (show to all online).
        assertThat(showAll.visibility().viewerDistance()).hasValue(0.0);
        NametagFormat typo = select(parsed, "uxmessentials.staff"::equals, "world");
        // A negative authored value is treated as absent so a typo falls back to the default radius, never blanking
        // nearby nametags.
        assertThat(typo.visibility().viewerDistance()).isEmpty();
    }

    @Test
    void aFormatWithNoLinesIsDropped(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                formats {
                  empty { condition = "", priority = 0, lines = [] }
                  real { condition = "", priority = 0, lines = [ "<white>{player}" ] }
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, LOG);

        assertThat(parsed.formats().formats()).hasSize(1);
        assertThat(parsed.formats().formats().get(0).name()).isEqualTo("real");
    }

    @Test
    void backCompatWrapsTheTopLevelNametagAsOneDefaultFormat(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                enabled = true
                refresh-ticks = 40
                nametag {
                  lines = [ "<gold>{player}", "<gray>%player_level%" ]
                  appearance { billboard = "VERTICAL", scale = 0.8, y-offset = 0.4 }
                  visibility { hide-while-sneaking = true }
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, LOG);

        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(parsed.formats().formats()).hasSize(1);
        NametagFormat only = parsed.formats().formats().get(0);
        assertThat(only.name()).isEqualTo("default");
        assertThat(only.condition()).isEqualTo(DisplayCondition.always());
        assertThat(only.priority()).isZero();
        assertThat(only.lines()).containsExactly("<gold>{player}", "<gray>%player_level%");
        assertThat(only.appearance().billboard()).isEqualTo("VERTICAL");
        assertThat(only.appearance().scale()).isEqualTo(0.8);
        assertThat(only.visibility().hideWhileSneaking()).isTrue();
        // The back-compat default matches every wearer (always-true condition).
        assertThat(parsed.formats().select(ctx(n -> false, "world"))).isPresent();
    }

    @Test
    void anEmptyRootYieldsInertContent(@TempDir Path dir) throws Exception {
        NametagContentCodec.Parsed parsed = NametagContentCodec.read(load(dir, "enabled = false\n"), LOG);

        assertThat(parsed.formats().formats()).isEmpty();
        assertThat(parsed.formats().select(ctx(n -> true, "world"))).isEmpty();
        assertThat(parsed.refreshInterval()).isPositive();
        // The vanilla-name hide defaults on, so out of the box one custom nametag shows without a double name.
        assertThat(parsed.hideVanillaName()).isTrue();
    }

    @Test
    void hideVanillaNameDefaultsOnAndIsOverridableToOff(@TempDir Path dir) throws Exception {
        ConfigurationNode unset = load(dir, """
                formats { default { condition = "", lines = [ "<white>{player}" ] } }
                """);
        assertThat(NametagContentCodec.read(unset, LOG).hideVanillaName()).isTrue();

        ConfigurationNode off = load(dir, """
                hide-vanilla-name = false
                formats { default { condition = "", lines = [ "<white>{player}" ] } }
                """);
        assertThat(NametagContentCodec.read(off, LOG).hideVanillaName()).isFalse();
    }

    @Test
    void aBlankNametagBlockYieldsNoFormats(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                refresh-ticks = 0
                nametag {
                  lines = []
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, LOG);

        assertThat(parsed.formats().formats()).isEmpty();
        // A non-positive refresh-ticks still falls back to one second rather than busy-spinning.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(1L));
    }

    @Test
    void anInvalidAppearanceFallsBackToDefaultsRatherThanDroppingTheFormat(@TempDir Path dir) throws Exception {
        // A non-positive scale violates the domain invariant; the codec must catch it and fall back to the default
        // appearance rather than failing the whole format (a single bad value never blanks the nametag).
        RecordingLogger log = new RecordingLogger();
        ConfigurationNode root = load(dir, """
                formats {
                  broken { condition = "", priority = 0, lines = [ "<white>{player}" ], appearance { scale = -2.0 } }
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, log);

        assertThat(parsed.formats().formats()).hasSize(1);
        NametagFormat broken = parsed.formats().formats().get(0);
        assertThat(broken.appearance().scale()).isEqualTo(1.0); // the safe default, not the bad -2.0
        assertThat(log.warnings).anyMatch(w -> w.contains("nametag_appearance_invalid"));
    }

    @Test
    void parsesTheTopLevelAnimationsBlockSharedWithTheOtherHudModules(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(dir, """
                enabled = true
                animations {
                  blink { type = "FRAMES", frames = [ "ON", "OFF" ], interval-ticks = 10 }
                  bogus { type = "WOBBLE", frames = [ "x" ] }
                }
                formats {
                  default { condition = "", priority = 0, lines = [ "<gray>%anim_blink%" ] }
                }
                """);

        NametagContentCodec.Parsed parsed = NametagContentCodec.read(root, new RecordingLogger());

        // The valid animation is parsed; the unknown type is skipped (the shared AnimationDef.parseAll behaviour).
        assertThat(parsed.animations()).extracting(d -> d.spec().name()).containsExactly("blink");
        AnimationDef blink = parsed.animations().get(0);
        assertThat(blink.spec().type()).isEqualTo(AnimationSpec.AnimationType.FRAMES);
        assertThat(blink.spec().frames()).containsExactly("ON", "OFF");
    }

    private static NametagFormat select(
            NametagContentCodec.Parsed parsed, Predicate<String> hasPermission, String world) {
        Optional<NametagFormat> selected = parsed.formats().select(ctx(hasPermission, world));
        assertThat(selected).isPresent();
        return selected.get();
    }

    private static ConditionContext ctx(Predicate<String> hasPermission, String world) {
        Function<String, String> resolve = s -> Map.<String, String>of().getOrDefault(s, s);
        return new ConditionContext(hasPermission, world, "SURVIVAL", resolve);
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

    /** A logger that captures formatted {@code warn} lines so a test can assert a skip/fallback was reported. */
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
