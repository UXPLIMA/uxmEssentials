package com.uxplima.uxmessentials.kits.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitActionRunner;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitAction;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.event.KitClaimed;
import com.uxplima.uxmessentials.kits.domain.event.KitClaiming;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /kit <name>}: grant a player a kit's items, gating on the per-kit permission, the one-time stamp,
 * the cooldown, and, only when an economy provider is present, the per-kit cost. The kit is resolved by id,
 * run through the {@link KitAccess} gate, and only then are the items handed to the {@link KitGranter}. The
 * cooldown clock and the one-time mark are recorded after the grant, never before, so a failed grant leaves
 * the player able to retry.
 *
 * <p>The staff give form ({@code /kit <name> <player>}, {@code uxmessentials.kit.others}) targets another
 * player: the gate and the rate limit then apply to the <em>recipient</em>, which is what makes a staff give
 * respect a one-time kit's consumed state. A self-claim passes the same player as actor and recipient.
 *
 * <p>The kit's typed {@link KitAction action engine} is sequenced here, the one place that knows both the gate
 * result and the moment of the grant: on a refused claim the kit's deny actions run for the recipient; on a
 * successful claim the before-items claim actions run, then the items are granted, then the after-items claim
 * actions run, so an operator can clear the inventory before the grant and fire a celebration after it. The
 * actual effects (sound, title, firework, commands, the {@code wait-ticks} delay) are the
 * {@link KitActionRunner}'s job; this orchestrator only decides what runs and when.
 *
 * <p>A kit set to {@code on-full: deny} is space-checked before it is charged: once the cheap gates pass, the
 * granter is asked whether the recipient's inventory has room, and if not the claim is refused exactly like any
 * other gate. The deny actions run and the player keeps the claim, with no cooldown, one-time stamp, stock
 * decrement, charge, or reward deposit applied. The check sits ahead of the irreversible reserve-and-charge
 * step precisely so a space refusal leaves nothing to compensate.
 */
public final class ClaimKit {

    private final KitRepository repository;
    private final KitAccess access;
    private final KitGranter granter;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final Optional<KitEconomy> economy;
    private final Optional<KitActionRunner> actions;
    private final DomainGate gate;

    /** Wire the claim path with no action runner: a kit's claim/deny actions are then recorded but not run. */
    public ClaimKit(
            KitRepository repository,
            KitAccess access,
            KitGranter granter,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            Optional<KitEconomy> economy) {
        this(repository, access, granter, notifier, events, clock, economy, Optional.empty());
    }

    /** The use case with nothing outside the plugin able to refuse a claim. The form the pure tests use. */
    public ClaimKit(
            KitRepository repository,
            KitAccess access,
            KitGranter granter,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            Optional<KitEconomy> economy,
            Optional<KitActionRunner> actions) {
        this(repository, access, granter, notifier, events, clock, economy, actions, DomainGate.allowAll());
    }

    public ClaimKit(
            KitRepository repository,
            KitAccess access,
            KitGranter granter,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            Optional<KitEconomy> economy,
            Optional<KitActionRunner> actions,
            DomainGate gate) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.granter = Objects.requireNonNull(granter, "granter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    /** Claim the kit {@code id} for {@code who} themselves. */
    public Result<Unit, KitError> claim(PlayerRef who, KitId id) {
        return claimFor(who, who, id);
    }

    /** Give the kit {@code id} to {@code recipient}, triggered by {@code actor} (a staff give). */
    public Result<Unit, KitError> claimFor(PlayerRef actor, PlayerRef recipient, KitId id) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(id, "id");
        Optional<KitDefinition> kit = repository.find(id);
        if (kit.isEmpty()) {
            notifier.send(actor, KitError.NOT_FOUND.messageKey(), Map.of("kit", id.value()));
            return Result.err(KitError.NOT_FOUND);
        }
        return admitAndGrant(actor, recipient, kit.get());
    }

    private Result<Unit, KitError> admitAndGrant(PlayerRef actor, PlayerRef recipient, KitDefinition kit) {
        if (!kit.isAvailableAt(LocalDateTime.now(clock))) {
            deny(actor, recipient, kit, KitError.UNAVAILABLE);
            return Result.err(KitError.UNAVAILABLE);
        }
        Result<Unit, KitError> gated = access.admissible(recipient, kit);
        if (gated.isErr()) {
            deny(actor, recipient, kit, gated.errorOrThrow());
            return gated;
        }
        KitDefinition granted = access.resolveVariant(recipient, kit);
        if (deniesForFullInventory(recipient, granted)) {
            deny(actor, recipient, kit, KitError.INVENTORY_FULL);
            return Result.err(KitError.INVENTORY_FULL);
        }
        // Before the charge and before a single item is placed, so a refused claim costs neither money nor a
        // cooldown and leaves the recipient's inventory exactly as it was.
        if (!gate.allows(new KitClaiming(kit.id(), recipient, actor))) {
            deny(actor, recipient, kit, KitError.VETOED);
            return Result.err(KitError.VETOED);
        }
        Result<Unit, KitError> charged = access.reserveAndCharge(recipient, kit);
        if (charged.isErr()) {
            deny(actor, recipient, kit, charged.errorOrThrow());
            return charged;
        }
        if (!granter.preGrant(recipient, granted)) {
            deny(actor, recipient, kit, KitError.CANCELLED);
            return Result.err(KitError.CANCELLED);
        }
        runActions(recipient, granted, beforeItems(granted));
        granter.grant(recipient, granted);
        runActions(recipient, granted, afterItems(granted));
        return completeClaim(actor, recipient, kit);
    }

    /** Record the cooldown/one-time stamp, deposit any claim reward, publish the event, and notify the recipient. */
    private Result<Unit, KitError> completeClaim(PlayerRef actor, PlayerRef recipient, KitDefinition kit) {
        access.recordClaim(recipient, kit);
        if (economy.isPresent() && kit.claimMoney().compareTo(java.math.BigDecimal.ZERO) > 0) {
            economy.get().deposit(recipient, kit.claimMoney(), kit.claimMoneyCurrency());
        }
        events.publish(new KitClaimed(kit.id(), recipient, actor, clock.instant()));
        notifier.send(
                recipient, KitsMessageKey.KIT_CLAIMED, Map.of("kit", kit.id().value()));
        return Result.ok();
    }

    /**
     * Whether a kit set to {@code on-full: deny} must refuse {@code recipient} for want of inventory space. The
     * check runs before the kit is admitted, ahead of any stock reservation or charge, so a space refusal
     * leaves nothing to compensate; a {@code drop} kit always returns false here and overflows at grant time.
     */
    private boolean deniesForFullInventory(PlayerRef recipient, KitDefinition granted) {
        return granted.onFull() == com.uxplima.uxmessentials.kits.domain.KitFullPolicy.DENY
                && !granter.hasRoomFor(recipient, granted);
    }

    /** Notify the actor of the failure and run the kit's deny actions for the refused recipient. */
    private void deny(PlayerRef actor, PlayerRef recipient, KitDefinition kit, KitError error) {
        notifier.send(actor, error.messageKey(), Map.of("kit", kit.id().value()));
        runActions(recipient, kit, kit.denyActions());
    }

    private void runActions(PlayerRef who, KitDefinition kit, List<KitAction> toRun) {
        if (!toRun.isEmpty()) {
            actions.ifPresent(runner -> runner.run(who, kit, toRun));
        }
    }

    private static List<KitAction> beforeItems(KitDefinition kit) {
        return kit.claimActions().stream().filter(KitAction::beforeItems).toList();
    }

    private static List<KitAction> afterItems(KitDefinition kit) {
        return kit.claimActions().stream().filter(a -> !a.beforeItems()).toList();
    }
}
