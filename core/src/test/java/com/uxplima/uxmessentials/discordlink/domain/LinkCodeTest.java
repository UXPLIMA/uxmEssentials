package com.uxplima.uxmessentials.discordlink.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class LinkCodeTest {

    @Test
    void normalisesToUppercaseAndStrips() {
        assertThat(LinkCode.of("  abc234  ").value()).isEqualTo("ABC234");
    }

    @Test
    void rejectsBlankOrShortOrOverlongOrAmbiguousInput() {
        assertThatThrownBy(() -> LinkCode.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinkCode.of("AB2")).isInstanceOf(IllegalArgumentException.class); // too short
        assertThatThrownBy(() -> LinkCode.of("ABCDE234567")).isInstanceOf(IllegalArgumentException.class); // too long
        // O, I, 0 and 1 are not in the accepted shape: a code carrying one is rejected.
        assertThatThrownBy(() -> LinkCode.of("ABCDEF0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinkCode.of("ABCDEFI")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedCodesAvoidAmbiguousCharactersAndAreValid() {
        RandomGenerator rng = new java.util.Random(42L);
        for (int i = 0; i < 500; i++) {
            String code = LinkCode.generate(rng).value();
            assertThat(code).matches("^[A-HJ-NP-Z2-9]{6,8}$");
            assertThat(code)
                    .doesNotContain("O")
                    .doesNotContain("I")
                    .doesNotContain("0")
                    .doesNotContain("1");
        }
    }
}
