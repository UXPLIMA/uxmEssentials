package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link HologramRenderer#maySee}. The per-player decision the renderer's allowed-viewer set is built
 * from. An {@code ALL} hologram is visible to everyone; a {@code PERMISSION} hologram is visible only to the
 * players the {@link Permissions} port says hold its node, so the restricted viewer set is exactly the
 * node-holders among the candidates; a {@code MANUAL} hologram is visible only to the players in its explicit
 * shown-viewer set, hidden from everyone else.
 */
class HologramVisibilityViewerTest {

    private static final PlayerRef VIP = new PlayerRef(UUID.randomUUID(), "Vip");
    private static final PlayerRef PLAIN = new PlayerRef(UUID.randomUUID(), "Plain");
    private static final String NODE = "uxmessentials.hologram.see.vip";

    @Test
    void everyoneSeesAnAllHologram() {
        Permissions noOne = grantingTo(null);

        assertThat(HologramRenderer.maySee(noOne, Visibility.everyone(), VIP, Set.of()))
                .isTrue();
        assertThat(HologramRenderer.maySee(noOne, Visibility.everyone(), PLAIN, Set.of()))
                .isTrue();
    }

    @Test
    void onlyNodeHoldersSeeAPermissionHologram() {
        Permissions onlyVip = grantingTo(VIP);
        Visibility gated = Visibility.restrictedTo(NODE);

        assertThat(HologramRenderer.maySee(onlyVip, gated, VIP, Set.of())).isTrue();
        assertThat(HologramRenderer.maySee(onlyVip, gated, PLAIN, Set.of())).isFalse();
    }

    @Test
    void theRestrictedViewerSetIsExactlyTheNodeHolders() {
        Permissions onlyVip = grantingTo(VIP);
        Visibility gated = Visibility.restrictedTo(NODE);

        List<PlayerRef> visible = List.of(VIP, PLAIN).stream()
                .filter(who -> HologramRenderer.maySee(onlyVip, gated, who, Set.of()))
                .toList();

        assertThat(visible).containsExactly(VIP);
    }

    @Test
    void onlyTheManualViewerSetSeesAManualHologram() {
        Permissions noOne = grantingTo(null);
        Visibility manual = Visibility.manual();
        Set<UUID> shown = Set.of(VIP.uuid());

        assertThat(HologramRenderer.maySee(noOne, manual, VIP, shown)).isTrue();
        assertThat(HologramRenderer.maySee(noOne, manual, PLAIN, shown)).isFalse();
    }

    @Test
    void aManualHologramWithNoShownViewersIsHiddenFromEveryone() {
        Permissions noOne = grantingTo(null);
        Visibility manual = Visibility.manual();

        assertThat(HologramRenderer.maySee(noOne, manual, VIP, Set.of())).isFalse();
        assertThat(HologramRenderer.maySee(noOne, manual, PLAIN, Set.of())).isFalse();
    }

    @Test
    void theManualViewerSetIsIgnoredForAllAndPermissionModes() {
        Permissions onlyVip = grantingTo(VIP);
        Set<UUID> shownPlain = Set.of(PLAIN.uuid());

        // ALL: everyone sees it regardless of the manual set.
        assertThat(HologramRenderer.maySee(onlyVip, Visibility.everyone(), PLAIN, shownPlain))
                .isTrue();
        // PERMISSION: the node decides, not the manual set: Plain is in the set but lacks the node.
        assertThat(HologramRenderer.maySee(onlyVip, Visibility.restrictedTo(NODE), PLAIN, shownPlain))
                .isFalse();
    }

    /** A {@link Permissions} fake that grants exactly the matching node to {@code holder} (everyone else: false). */
    private static Permissions grantingTo(@Nullable PlayerRef holder) {
        return new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                return holder != null && who.uuid().equals(holder.uuid()) && node.equals(NODE);
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                return QuotaResult.limited(configDefault);
            }
        };
    }
}
