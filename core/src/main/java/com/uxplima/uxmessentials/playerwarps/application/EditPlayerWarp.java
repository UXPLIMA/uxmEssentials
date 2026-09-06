package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.ReservedWarpNames;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * The single-warp edit verbs a {@code /pwarp manage} menu and the {@code /pwarp} subcommands drive: rename, display
 * name, description, icon, category, access, password, price, and move. Each verb follows the same shape, resolve
 * the warp by its global name ({@link PlayerWarpError#NOT_FOUND} when absent), gate the actor through
 * {@link WarpAuthorization#allows} on the verb's {@link WarpCapability} ({@link PlayerWarpError#NO_PERMISSION} when
 * denied), apply the immutable transition, persist it, and notify, so authority is enforced uniformly and no verb
 * re-implements the ownership check.
 *
 * <p>The edit timestamp always comes from the injected {@link Clock}, never the domain. Two verbs carry extra rules:
 * a price change to a different currency is refused while the earnings bank still holds funds
 * ({@link PlayerWarpError#CURRENCY_LOCKED}. Withdraw first, so a settlement is never split across currencies), and a
 * rename onto a name another warp already holds is refused ({@link PlayerWarpError#NAME_TAKEN}). Password writes go
 * through the {@link PlayerWarpPasswordStore} seam and never touch the plaintext beyond handing it to that store
 * it is never rendered back to the actor or logged. Setting a password does not by itself flip the access axis to
 * {@link WarpAccess#PASSWORD}; the two are independent operations the manage UI happens to pair.
 */
public final class EditPlayerWarp {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final Notifier notifier;
    private final PlayerWarpPasswordStore passwords;
    private final Clock clock;

    public EditPlayerWarp(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            Notifier notifier,
            PlayerWarpPasswordStore passwords,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Set (or clear, with an empty value) {@code name}'s display name. Gate {@link WarpCapability#EDIT_METADATA}. */
    public Result<Unit, PlayerWarpError> setDisplayName(
            PlayerRef actor, PlayerWarpName name, Optional<DisplayName> value) {
        Objects.requireNonNull(value, "value");
        return transition(
                actor,
                name,
                WarpCapability.EDIT_METADATA,
                warp -> warp.withDisplayName(value, clock.instant()),
                PlayerwarpsMessageKey.PWARP_DISPLAY_NAME_SET);
    }

    /** Set (or clear) {@code name}'s description. Gate {@link WarpCapability#EDIT_METADATA}. */
    public Result<Unit, PlayerWarpError> setDescription(
            PlayerRef actor, PlayerWarpName name, Optional<WarpDescription> value) {
        Objects.requireNonNull(value, "value");
        return transition(
                actor,
                name,
                WarpCapability.EDIT_METADATA,
                warp -> warp.withDescription(value, clock.instant()),
                PlayerwarpsMessageKey.PWARP_DESCRIPTION_SET);
    }

    /** Set (or clear) {@code name}'s browse icon. Gate {@link WarpCapability#EDIT_METADATA}. */
    public Result<Unit, PlayerWarpError> setIcon(PlayerRef actor, PlayerWarpName name, Optional<IconSpec> value) {
        Objects.requireNonNull(value, "value");
        return transition(
                actor,
                name,
                WarpCapability.EDIT_METADATA,
                warp -> warp.withIcon(value, clock.instant()),
                PlayerwarpsMessageKey.PWARP_ICON_SET);
    }

    /** File {@code name} under a browse category (or none). Gate {@link WarpCapability#EDIT_METADATA}. */
    public Result<Unit, PlayerWarpError> setCategory(
            PlayerRef actor, PlayerWarpName name, Optional<String> categoryId) {
        Objects.requireNonNull(categoryId, "categoryId");
        return transition(
                actor,
                name,
                WarpCapability.EDIT_METADATA,
                warp -> warp.withCategoryId(categoryId, clock.instant()),
                PlayerwarpsMessageKey.PWARP_CATEGORY_SET);
    }

    /** Set {@code name}'s access axis. Gate {@link WarpCapability#EDIT_ACCESS}. */
    public Result<Unit, PlayerWarpError> setAccess(PlayerRef actor, PlayerWarpName name, WarpAccess access) {
        Objects.requireNonNull(access, "access");
        return transition(
                actor,
                name,
                WarpCapability.EDIT_ACCESS,
                warp -> warp.withAccess(access, clock.instant()),
                PlayerwarpsMessageKey.PWARP_ACCESS_SET);
    }

    /**
     * Store {@code plaintext} as {@code name}'s password through the password store. Gate
     * {@link WarpCapability#EDIT_ACCESS}. The plaintext is handed straight to the store and never rendered back or
     * logged; the feedback names only the warp.
     */
    public Result<Unit, PlayerWarpError> setPassword(PlayerRef actor, PlayerWarpName name, String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        return gate(actor, name, WarpCapability.EDIT_ACCESS, warp -> {
            passwords.set(warp.id().orElseThrow(), plaintext);
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_PASSWORD_SET, Map.of("warp", name.value()));
            return Result.ok();
        });
    }

    /** Clear {@code name}'s password. Gate {@link WarpCapability#EDIT_ACCESS}. */
    public Result<Unit, PlayerWarpError> clearPassword(PlayerRef actor, PlayerWarpName name) {
        return gate(actor, name, WarpCapability.EDIT_ACCESS, warp -> {
            passwords.clear(warp.id().orElseThrow());
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_PASSWORD_CLEARED, Map.of("warp", name.value()));
            return Result.ok();
        });
    }

    /**
     * Set {@code name}'s entry price. Gate {@link WarpCapability#EDIT_PRICE}. Changing to a different currency while
     * the earnings bank still holds funds is refused ({@link PlayerWarpError#CURRENCY_LOCKED}) so a later payout is
     * never denominated in two currencies: the owner withdraws first, then re-prices.
     */
    public Result<Unit, PlayerWarpError> setPrice(PlayerRef actor, PlayerWarpName name, WarpCost price) {
        Objects.requireNonNull(price, "price");
        return gate(actor, name, WarpCapability.EDIT_PRICE, warp -> {
            boolean currencyChanged = !warp.price().currencyId().equals(price.currencyId());
            if (currencyChanged && !warp.earnings().isZero()) {
                notifier.send(actor, PlayerWarpError.CURRENCY_LOCKED.messageKey(), Map.of("warp", name.value()));
                return Result.err(PlayerWarpError.CURRENCY_LOCKED);
            }
            repository.save(warp.withPrice(price, clock.instant()));
            notifier.send(
                    actor,
                    PlayerwarpsMessageKey.PWARP_PRICE_SET,
                    Map.of("warp", name.value(), "price", price.amount().toPlainString()));
            return Result.ok();
        });
    }

    /** Re-anchor {@code name} at {@code at}. Gate {@link WarpCapability#MOVE}. */
    public Result<Unit, PlayerWarpError> moveHere(PlayerRef actor, PlayerWarpName name, Position at) {
        Objects.requireNonNull(at, "at");
        return transition(
                actor,
                name,
                WarpCapability.MOVE,
                warp -> warp.movedTo(at, clock.instant()),
                PlayerwarpsMessageKey.PWARP_MOVED);
    }

    /**
     * Rename {@code name} to {@code newName}. Gate {@link WarpCapability#RENAME}. A name another warp already holds
     * is refused ({@link PlayerWarpError#NAME_TAKEN}); renaming to the current name is the same warp, so it is not a
     * collision. The surrogate id is unchanged, so the row is renamed in place.
     */
    public Result<Unit, PlayerWarpError> rename(PlayerRef actor, PlayerWarpName name, PlayerWarpName newName) {
        Objects.requireNonNull(newName, "newName");
        return gate(actor, name, WarpCapability.RENAME, warp -> {
            if (ReservedWarpNames.isReserved(newName)) {
                notifier.send(actor, PlayerWarpError.RESERVED_NAME.messageKey(), Map.of("warp", newName.value()));
                return Result.err(PlayerWarpError.RESERVED_NAME);
            }
            if (!newName.equals(name) && repository.existsByName(newName)) {
                notifier.send(actor, PlayerWarpError.NAME_TAKEN.messageKey(), Map.of("warp", newName.value()));
                return Result.err(PlayerWarpError.NAME_TAKEN);
            }
            repository.save(warp.renamed(newName, clock.instant()));
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_RENAMED, Map.of("warp", newName.value()));
            return Result.ok();
        });
    }

    /** Resolve, gate, apply the transition, save, and notify: the shape every metadata/access/move verb shares. */
    private Result<Unit, PlayerWarpError> transition(
            PlayerRef actor,
            PlayerWarpName name,
            WarpCapability capability,
            UnaryOperator<PlayerWarp> change,
            PlayerwarpsMessageKey feedback) {
        return gate(actor, name, capability, warp -> {
            repository.save(change.apply(warp));
            notifier.send(actor, feedback, Map.of("warp", name.value()));
            return Result.ok();
        });
    }

    /**
     * Resolve {@code name} and gate {@code actor} on {@code capability}, then run {@code body} against the live
     * warp. A missing warp yields {@link PlayerWarpError#NOT_FOUND}; a denied actor yields
     * {@link PlayerWarpError#NO_PERMISSION}. Both send the matching feedback so the caller renders nothing itself.
     */
    private Result<Unit, PlayerWarpError> gate(
            PlayerRef actor,
            PlayerWarpName name,
            WarpCapability capability,
            Function<PlayerWarp, Result<Unit, PlayerWarpError>> body) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (!authorization.allows(warp, actor.uuid(), capability)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        return body.apply(warp);
    }
}
