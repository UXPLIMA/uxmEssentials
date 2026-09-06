package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of the {@code has-item} argument grammar {@code <material> [amount] [name:<exact>]}. The
 * parse is Bukkit-free. It carries the material token verbatim (resolution to a real {@code Material} happens in the
 * condition), so this exercises the amount defaulting and the optional display-name suffix in isolation.
 */
class RequirementConditionsParseTest {

    @Test
    void materialOnlyDefaultsToOneAndNoNameMatch() {
        RequirementConditions.ItemMatch match = RequirementConditions.ItemMatch.parse("DIAMOND");
        assertThat(match).isNotNull();
        assertThat(match.materialToken()).isEqualTo("DIAMOND");
        assertThat(match.amount()).isEqualTo(1);
        assertThat(match.name()).isNull();
    }

    @Test
    void materialAndAmountAreBothRead() {
        RequirementConditions.ItemMatch match = RequirementConditions.ItemMatch.parse("DIAMOND 3");
        assertThat(match).isNotNull();
        assertThat(match.materialToken()).isEqualTo("DIAMOND");
        assertThat(match.amount()).isEqualTo(3);
        assertThat(match.name()).isNull();
    }

    @Test
    void aNameSuffixIsCapturedWholeIncludingSpaces() {
        RequirementConditions.ItemMatch match = RequirementConditions.ItemMatch.parse("DIAMOND 2 name:Cool Sword");
        assertThat(match).isNotNull();
        assertThat(match.materialToken()).isEqualTo("DIAMOND");
        assertThat(match.amount()).isEqualTo(2);
        assertThat(match.name()).isEqualTo("Cool Sword");
    }

    @Test
    void aNameSuffixWithoutAnAmountKeepsTheDefaultCount() {
        RequirementConditions.ItemMatch match = RequirementConditions.ItemMatch.parse("PAPER name:Ticket");
        assertThat(match).isNotNull();
        assertThat(match.materialToken()).isEqualTo("PAPER");
        assertThat(match.amount()).isEqualTo(1);
        assertThat(match.name()).isEqualTo("Ticket");
    }

    @Test
    void aNonNumericAmountFallsBackToOne() {
        RequirementConditions.ItemMatch match = RequirementConditions.ItemMatch.parse("DIAMOND lots");
        assertThat(match).isNotNull();
        assertThat(match.amount())
                .as("an unparsable amount defaults to one rather than aborting the match")
                .isEqualTo(1);
    }

    @Test
    void aBlankArgumentIsNull() {
        assertThat(RequirementConditions.ItemMatch.parse("   ")).isNull();
    }

    @Test
    void aNameOnlyArgumentWithNoMaterialIsNull() {
        assertThat(RequirementConditions.ItemMatch.parse("name:Just a name")).isNull();
    }
}
