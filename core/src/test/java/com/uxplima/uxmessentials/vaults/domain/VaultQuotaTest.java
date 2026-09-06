package com.uxplima.uxmessentials.vaults.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.event.VaultContentsChanged;
import org.junit.jupiter.api.Test;

/**
 * The vaults domain quota and aggregate rules, asserted in isolation from the application and adapter: the
 * {@code VaultAmount} count gate (a concrete cap allows indices up to it, the unlimited sentinel allows any),
 * the {@code VaultSize} clamp into the renderable {@code [1, 6]} row range, the {@code VaultId} index
 * validation, and the {@code Vault} transitions (an open at an unchanged size returns the same aggregate, a
 * contents save raises {@code VaultContentsChanged}). The model is pure, no clock or repository, so every
 * rule holds here.
 */
class VaultQuotaTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Instant NOW = Instant.parse("2026-05-31T12:00:00Z");

    @Test
    void amountCapAllowsIndicesUpToItAndRefusesBeyond() {
        VaultAmount three = VaultAmount.of(3);

        assertThat(three.allows(1)).isTrue();
        assertThat(three.allows(3)).isTrue();
        assertThat(three.allows(4)).isFalse();
    }

    @Test
    void unlimitedAmountAllowsAnyIndex() {
        VaultAmount unlimited = VaultAmount.noLimit();

        assertThat(unlimited.allows(1)).isTrue();
        assertThat(unlimited.allows(9_999)).isTrue();
    }

    @Test
    void amountRejectsANegativeCap() {
        assertThatThrownBy(() -> VaultAmount.of(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeClampsIntoTheRenderableRowRange() {
        assertThat(VaultSize.ofClamped(0).rows()).isEqualTo(VaultSize.MIN_ROWS);
        assertThat(VaultSize.ofClamped(4).rows()).isEqualTo(4);
        assertThat(VaultSize.ofClamped(99).rows()).isEqualTo(VaultSize.MAX_ROWS);
    }

    @Test
    void sizeSlotsAreNinePerRow() {
        assertThat(new VaultSize(6).slots()).isEqualTo(54);
        assertThat(new VaultSize(1).slots()).isEqualTo(9);
    }

    @Test
    void sizeRejectsAnOutOfRangeRowCount() {
        assertThatThrownBy(() -> new VaultSize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VaultSize(7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vaultIdRejectsANonPositiveIndex() {
        assertThatThrownBy(() -> VaultId.of(OWNER, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VaultId.of(OWNER, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openAtTheSameSizeReturnsTheSameAggregate() {
        Vault vault = Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), NOW);

        assertThat(vault.openedAt(new VaultSize(3))).isSameAs(vault);
    }

    @Test
    void openAtADifferentSizeAdoptsTheNewHeightKeepingContents() {
        Vault vault =
                Vault.of(VaultId.of(OWNER, 1), new VaultSize(3), VaultContents.of(new byte[] {1, 2}), null, null, NOW);

        Vault wider = vault.openedAt(new VaultSize(6));

        assertThat(wider.size().rows()).isEqualTo(6);
        assertThat(wider.contents()).isEqualTo(vault.contents());
    }

    @Test
    void savingContentsRaisesVaultContentsChangedAndStampsLastTouched() {
        Vault vault = Vault.allocate(VaultId.of(OWNER, 2), new VaultSize(6), NOW);
        Instant later = NOW.plusSeconds(60);

        Vault.Change change = vault.withContents(OWNER, VaultContents.of(new byte[] {9}), later);

        assertThat(change.result().lastTouched()).isEqualTo(later);
        assertThat(change.result().contents()).isEqualTo(VaultContents.of(new byte[] {9}));
        assertThat(change.event()).isEqualTo(new VaultContentsChanged(OWNER, 2, later));
    }

    @Test
    void emptyContentsRoundTripToEmpty() {
        assertThat(VaultContents.of(new byte[0]).isEmpty()).isTrue();
        assertThat(VaultContents.empty().payload()).isEmpty();
        assertThat(VaultContents.of(new byte[] {1}).payload())
                .get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .containsExactly(1);
    }
}
