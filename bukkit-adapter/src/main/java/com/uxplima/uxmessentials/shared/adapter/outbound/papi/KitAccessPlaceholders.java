package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * {@link KitsPlaceholders} over the kits context's {@link KitRepository} catalog and the {@link KitAccess}
 * gate. Every per-kit answer is read through the same {@link KitAccess} the {@code /kit} claim path uses, its
 * permission check, its cooldown clock (keyed per kit id), and its one-time-claim store, so what a
 * placeholder reports matches what a claim would enforce. The usable-ids list is the {@link ListKits} filter
 * the {@code /kit list} surface renders.
 *
 * <p>An id that no kit carries yields {@link Optional#empty()}; a malformed id (rejected by {@link KitId#of})
 * is treated the same way rather than propagating the validation error onto the placeholder surface. A ready
 * kit returns {@link Duration#ZERO} for the cooldown.
 */
@NullMarked
public final class KitAccessPlaceholders implements KitsPlaceholders {

    private final KitRepository repository;
    private final KitAccess access;
    private final ListKits listKits;

    public KitAccessPlaceholders(KitRepository repository, KitAccess access, ListKits listKits) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.listKits = Objects.requireNonNull(listKits, "listKits");
    }

    @Override
    public Optional<Duration> cooldownRemaining(PlayerRef who, String kitId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kitId, "kitId");
        return lookup(kitId).map(kit -> {
            Result<Unit, Duration> gate = access.remaining(who, kit);
            return gate.isErr() ? gate.errorOrThrow() : Duration.ZERO;
        });
    }

    @Override
    public Optional<Boolean> available(PlayerRef who, String kitId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kitId, "kitId");
        return lookup(kitId)
                .map(kit -> access.hasPermission(who, kit)
                        && !access.isOnCooldown(who, kit)
                        && !access.hasClaimedOneTime(who, kit));
    }

    @Override
    public Optional<Boolean> hasPermission(PlayerRef who, String kitId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kitId, "kitId");
        return lookup(kitId).map(kit -> access.hasPermission(who, kit));
    }

    @Override
    public Optional<BigDecimal> cost(String kitId) {
        Objects.requireNonNull(kitId, "kitId");
        return lookup(kitId).map(kit -> kit.cost().amount());
    }

    @Override
    public Optional<Integer> claimsLeft(PlayerRef who, String kitId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kitId, "kitId");
        // A one-time kit grants exactly one claim ever (zero once consumed); a repeatable kit is unlimited (-1).
        return lookup(kitId).map(kit -> {
            if (!kit.isOneTime()) {
                return -1;
            }
            return access.hasClaimedOneTime(who, kit) ? 0 : 1;
        });
    }

    @Override
    public List<String> usableIds(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return listKits.available(who).stream().map(kit -> kit.id().value()).toList();
    }

    private Optional<KitDefinition> lookup(String kitId) {
        try {
            return repository.find(KitId.of(kitId));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
