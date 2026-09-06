package com.uxplima.uxmessentials.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link Version}. The SemVer value object the update checker compares the running plugin
 * version against the latest release tag with. The load-bearing properties: a {@code v}-prefixed tag parses, a
 * pre-release or build suffix is ignored, a missing minor/patch reads as zero, ordering is major-then-minor-then-
 * patch, and a non-numeric body parses to empty rather than guessing a version that could fire a false update
 * notice.
 */
class VersionTest {

    @Test
    void parsesPlainTriple() {
        assertThat(Version.parse("2.7.0")).contains(new Version(2, 7, 0));
    }

    @Test
    void stripsLeadingVPrefix() {
        assertThat(Version.parse("v2.7.0")).contains(new Version(2, 7, 0));
        assertThat(Version.parse("V1.0.0")).contains(new Version(1, 0, 0));
    }

    @Test
    void ignoresPreReleaseAndBuildMetadata() {
        assertThat(Version.parse("2.7.0-SNAPSHOT")).contains(new Version(2, 7, 0));
        assertThat(Version.parse("2.7.0+build.42")).contains(new Version(2, 7, 0));
        assertThat(Version.parse("v3.1.4-rc.1+sha.abc")).contains(new Version(3, 1, 4));
    }

    @Test
    void defaultsMissingMinorAndPatchToZero() {
        assertThat(Version.parse("2")).contains(new Version(2, 0, 0));
        assertThat(Version.parse("2.5")).contains(new Version(2, 5, 0));
        assertThat(Version.parse("v3")).contains(new Version(3, 0, 0));
    }

    @Test
    void toleratesSurroundingWhitespace() {
        assertThat(Version.parse("  1.2.3  ")).contains(new Version(1, 2, 3));
    }

    @Test
    void rejectsNonNumericOrEmptyBodies() {
        assertThat(Version.parse("")).isEmpty();
        assertThat(Version.parse("v")).isEmpty();
        assertThat(Version.parse("not-a-version")).isEmpty();
        assertThat(Version.parse("<html>404</html>")).isEmpty();
        assertThat(Version.parse("1.x.0")).isEmpty();
    }

    @Test
    void ordersByMajorThenMinorThenPatch() {
        assertThat(new Version(2, 0, 0).isNewerThan(new Version(1, 9, 9))).isTrue();
        assertThat(new Version(1, 2, 0).isNewerThan(new Version(1, 1, 9))).isTrue();
        assertThat(new Version(1, 1, 2).isNewerThan(new Version(1, 1, 1))).isTrue();
    }

    @Test
    void equalVersionsAreNotNewer() {
        Version one = new Version(1, 2, 3);
        assertThat(one.isNewerThan(new Version(1, 2, 3))).isFalse();
        assertThat(new Version(1, 2, 3)).isEqualTo(one);
    }

    @Test
    void olderVersionIsNotNewer() {
        assertThat(new Version(1, 0, 0).isNewerThan(new Version(2, 0, 0))).isFalse();
        assertThat(new Version(1, 2, 3).isNewerThan(new Version(1, 2, 4))).isFalse();
    }

    @Test
    void rendersCanonicalTriple() {
        assertThat(new Version(2, 7, 0)).hasToString("2.7.0");
    }

    @Test
    void rejectsNegativeComponents() {
        assertThatThrownBy(() -> new Version(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesNewerAndOlderViaCompareTo() {
        assertThat(new Version(1, 0, 0)).usingDefaultComparator().isLessThan(new Version(1, 0, 1));
        assertThat(Optional.of(new Version(3, 0, 0))).contains(new Version(3, 0, 0));
    }
}
