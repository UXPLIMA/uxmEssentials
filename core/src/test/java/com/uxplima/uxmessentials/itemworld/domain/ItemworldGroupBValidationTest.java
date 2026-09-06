package com.uxplima.uxmessentials.itemworld.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.PowertoolPolicy;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Boundary-validation coverage for the itemworld group-B domain value objects and policies: the caps, clamps
 * and enum parses that protect the server before any adapter call (docs/10-feature-modules.md §15.10). These
 * are the pure {@code :core} rules the Brigadier commands lean on: a spawn count clamped to the configured cap,
 * a purge radius clamped to the ceiling, the time/weather keyword/enum parses, and the powertool bind/clear
 * shape. None of this touches Bukkit.
 */
class ItemworldGroupBValidationTest {

    @Test
    void spawnMobCountIsCappedAndOutOfRangeIsRejected() {
        assertThat(MobSpec.of("zombie", 10, 64)).map(MobSpec::amount).contains(10);
        assertThat(MobSpec.of("zombie", 64, 64)).isPresent(); // at the cap is allowed
        assertThat(MobSpec.of("zombie", 65, 64)).isEmpty(); // over the cap is rejected, never truncated
        assertThat(MobSpec.of("zombie", 0, 64)).isEmpty(); // non-positive is rejected
        assertThat(MobSpec.of("  ", 1, 64)).isEmpty(); // blank id is rejected
        assertThat(MobSpec.of("ZOMBIE", 1, 64)).map(MobSpec::typeId).contains("zombie"); // normalised lowercase
    }

    @Test
    void spawnMobFallsBackToDefaultCapWhenConfiguredCapIsUnset() {
        assertThat(MobSpec.of("zombie", MobSpec.DEFAULT_CAP, 0)).isPresent();
        assertThat(MobSpec.of("zombie", MobSpec.DEFAULT_CAP + 1, 0)).isEmpty();
    }

    @Test
    void purgeRadiusIsClampedToTheCeilingAndCategoriesAreModelled() {
        PurgePolicy policy = new PurgePolicy(configWith(256));

        PurgeSelection butcher = policy.butcher(1_000_000);
        assertThat(butcher.radius()).isEqualTo(256); // clamped, so a million-block sweep cannot stall the server
        assertThat(butcher.scope()).isEqualTo(PurgeSelection.Scope.RADIUS);
        assertThat(butcher.category()).isEqualTo(PurgeSelection.Category.MONSTERS);

        PurgeSelection killAllNamed = policy.killAll("cow");
        assertThat(killAllNamed.scope()).isEqualTo(PurgeSelection.Scope.WORLD);
        assertThat(killAllNamed.category()).isEqualTo(PurgeSelection.Category.NAMED_TYPE);
        assertThat(killAllNamed.typeId()).contains("cow");

        PurgeSelection killAllEverything = policy.killAll("");
        assertThat(killAllEverything.category()).isEqualTo(PurgeSelection.Category.ALL_ENTITIES);
        assertThat(killAllEverything.typeId()).isEmpty();
    }

    @Test
    void removeRequiresATypeAndClampsItsRadius() {
        PurgePolicy policy = new PurgePolicy(configWith(128));

        assertThat(policy.remove("  ", 10).isErr()).isTrue(); // a blank type is rejected
        var ok = policy.remove("zombie", 9999);
        assertThat(ok.isOk()).isTrue();
        assertThat(ok.orElseThrow().radius()).isEqualTo(128); // clamped to the configured ceiling
        assertThat(ok.orElseThrow().typeId()).contains("zombie");
    }

    @Test
    void timeParsesKeywordsAndBoundsAbsoluteSets() {
        assertThat(TimeSpec.parse(TimeSpec.Mode.SET, "day"))
                .map(TimeSpec::ticks)
                .contains(1_000L);
        assertThat(TimeSpec.parse(TimeSpec.Mode.SET, "night"))
                .map(TimeSpec::ticks)
                .contains(13_000L);
        assertThat(TimeSpec.parse(TimeSpec.Mode.SET, "6000"))
                .map(TimeSpec::ticks)
                .contains(6_000L);
        assertThat(TimeSpec.parse(TimeSpec.Mode.SET, "24000")).isEmpty(); // a set must be within a day
        assertThat(TimeSpec.parse(TimeSpec.Mode.SET, "notatime")).isEmpty();
        assertThat(TimeSpec.parse(TimeSpec.Mode.ADD, "100"))
                .map(TimeSpec::ticks)
                .contains(100L);
        assertThat(TimeSpec.day().mode()).isEqualTo(TimeSpec.Mode.SET);
    }

    @Test
    void weatherParsesItsClosedKindSetAndValidatesDuration() {
        assertThat(WeatherSpec.parse("sun", Optional.empty()))
                .map(WeatherSpec::kind)
                .contains(WeatherSpec.Kind.CLEAR);
        assertThat(WeatherSpec.parse("rain", Optional.empty()))
                .map(WeatherSpec::kind)
                .contains(WeatherSpec.Kind.RAIN);
        assertThat(WeatherSpec.parse("storm", Optional.empty()))
                .map(WeatherSpec::kind)
                .contains(WeatherSpec.Kind.THUNDER);
        assertThat(WeatherSpec.parse("hurricane", Optional.empty())).isEmpty();
        assertThat(WeatherSpec.parse("rain", Optional.of(-1))).isEmpty(); // a negative duration is rejected
        assertThat(WeatherSpec.parse("rain", Optional.of(60)))
                .flatMap(WeatherSpec::durationSeconds)
                .contains(60);
    }

    @Test
    void powertoolBindClearsOnBlankAndRejectsAnEmptyHand() {
        PowertoolPolicy policy = new PowertoolPolicy();

        var bound = policy.bind(Optional.of("minecraft:stick"), "/spawn");
        assertThat(bound.isOk()).isTrue();
        assertThat(bound.orElseThrow().isClear()).isFalse();
        assertThat(bound.orElseThrow().firstCommand()).contains("spawn"); // the leading slash is stripped

        var cleared = policy.bind(Optional.of("minecraft:stick"), "  ");
        assertThat(cleared.orElseThrow().isClear()).isTrue();

        var emptyHand = policy.bind(Optional.empty(), "/spawn");
        assertThat(emptyHand.isErr()).isTrue();
        assertThat(emptyHand.errorOrThrow()).isEqualTo(ItemWorldError.NO_ITEM_IN_HAND);
    }

    @Test
    void enchantLevelIsClampedAndReportsTheClamp() {
        Optional<EnchantSpec> clamped = EnchantSpec.of("sharpness", 99_999, 5);
        assertThat(clamped).isPresent();
        assertThat(clamped.orElseThrow().level()).isEqualTo(5);
        assertThat(clamped.orElseThrow().clamped()).isTrue();
        assertThat(clamped.orElseThrow().enchantId()).isEqualTo("minecraft:sharpness");

        Optional<EnchantSpec> within = EnchantSpec.of("sharpness", 3, 5);
        assertThat(within.orElseThrow().clamped()).isFalse();
        assertThat(EnchantSpec.of("  ", 1, 5)).isEmpty();
    }

    private static ItemworldConfig configWith(int purgeMaxRadius) {
        return ItemworldConfig.from(new RadiusConfig(purgeMaxRadius));
    }

    /** A minimal {@link ConfigStore} that only answers the purge-max-radius key the policy reads. */
    private record RadiusConfig(int purgeMaxRadius) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return "purge-max-radius".equals(path) ? purgeMaxRadius : fallback;
        }
    }
}
