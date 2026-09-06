package com.uxplima.uxmessentials.itemworld.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.itemworld.domain.AmountSpec;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import com.uxplima.uxmessentials.itemworld.domain.ItemWorldError;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The {@code /give}//{@code /item}//{@code /more} cap policy: validate the requested item and amount against
 * the configured caps before the adapter materialises a stack, and decide whether the give is bulk enough to
 * audit. This is the one real rule in the item-utils group, everything else is an ACL-thin adapter call, so
 * it lives in the application layer and is unit-testable in {@code :core}.
 *
 * <p>The amount cap comes from {@link ItemworldConfig#giveCap()} (or {@link ItemworldConfig#moreCap()} for
 * {@code /more}); an over-cap or non-positive amount is rejected with {@link ItemWorldError#AMOUNT_OUT_OF_RANGE}
 * rather than truncated, and a blank/malformed item id with {@link ItemWorldError#UNKNOWN_ITEM}. An amount at
 * or above {@link ItemworldConfig#giveAuditThreshold()} marks the give as audit-worthy.
 */
public final class GiveCapPolicy {

    private final ItemworldConfig config;

    public GiveCapPolicy(ItemworldConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Validate a {@code /give}//{@code /item} request: parse the item id and clamp the amount to the give cap. */
    public Result<Validated, ItemWorldError> validateGive(String rawItem, int requestedAmount) {
        return validate(rawItem, requestedAmount, config.giveCap());
    }

    /** Validate a {@code /more} stack-fill amount against the {@code more-cap}; the item is the held one. */
    public Result<AmountSpec, ItemWorldError> validateMore(int requestedAmount) {
        return AmountSpec.of(requestedAmount, config.moreCap())
                .<Result<AmountSpec, ItemWorldError>>map(Result::ok)
                .orElseGet(() -> Result.err(ItemWorldError.AMOUNT_OUT_OF_RANGE));
    }

    private Result<Validated, ItemWorldError> validate(String rawItem, int requestedAmount, int cap) {
        Optional<ItemQuery> item = ItemQuery.parse(rawItem);
        if (item.isEmpty()) {
            return Result.err(ItemWorldError.UNKNOWN_ITEM);
        }
        Optional<AmountSpec> amount = AmountSpec.of(requestedAmount, cap);
        if (amount.isEmpty()) {
            return Result.err(ItemWorldError.AMOUNT_OUT_OF_RANGE);
        }
        boolean auditWorthy = requestedAmount >= config.giveAuditThreshold();
        return Result.ok(new Validated(item.get(), amount.get(), auditWorthy));
    }

    /**
     * A validated give: the resolved item query, the clamped amount, and whether the give is bulk enough to
     * audit ({@link ItemworldConfig#giveAuditThreshold()}).
     *
     * @param item the validated item id to materialise
     * @param amount the validated, in-cap amount
     * @param auditWorthy whether the amount met the bulk-audit threshold
     */
    public record Validated(ItemQuery item, AmountSpec amount, boolean auditWorthy) {
        public Validated {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
