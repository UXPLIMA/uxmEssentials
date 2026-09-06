package com.uxplima.uxmessentials.kits.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One operator-curated kit: its {@link KitId}, the {@link KitItem stacks} it grants, the cooldown between
 * claims, whether it may be claimed only once ({@code oneTime}), whether it is gated behind the per-kit
 * permission node, and the optional {@link KitCost}. Each kit is defined in its own
 * {@code modules/kits/kits/<id>.conf} file and loaded into this value object; a kit is never mutated in
 * place: an edit produces a new definition the repository overwrites the kit's file with.
 *
 * <p>The cooldown is the default tier when the player holds no numbered {@code uxmessentials.kit.cooldown.
 * <seconds>} node; the {@code Cooldowns} port resolves the effective wait per claim against it. The
 * {@code oneTime} flag is enforced by a persisted claim stamp (PDC), independent of the cooldown clock: a
 * one-time kit is consumed forever after the first claim, a repeatable kit is merely rate-limited by its
 * cooldown. The {@code permission} flag, when set, requires {@link #permissionNode()} on top of the base
 * {@code uxmessentials.kit.use} command node; {@code customPermission}, when present, is consulted in place
 * of the default per-kit node so several kits can share one permission.
 *
 * <p>A kit may carry per-rank {@link KitVariant variants}: at claim, list, and preview time the best variant
 * whose permission the viewer holds replaces the base items (and optionally the cooldown and cost), so one
 * {@code /kit daily} can hand richer loot to higher ranks. The {@code preview} flag lets the browse menu open
 * a read-only preview of the kit on right-click (default on; disable it for mystery kits); {@code closeOnClaim}
 * closes the browse menu after a successful claim instead of refreshing it.
 *
 * @param id the kit's canonical id
 * @param items the stacks the kit grants, in definition order
 * @param cooldown the default wait between claims when no tier node matches; {@link Duration#ZERO} for none
 * @param oneTime whether the kit may be claimed only once per player (a persisted one-time stamp)
 * @param permission whether the kit additionally requires the per-kit permission node
 * @param cost the price to claim the kit; {@link KitCost#free()} when there is no charge
 * @param display the normal browse-menu icon override (material, MiniMessage name, lore); empty for the catalog default
 * @param commands commands to execute on successful claim
 * @param sound sound to play on successful claim
 * @param particles particles to spawn on successful claim
 * @param customPermission a permission node to gate the kit with in place of {@code uxmessentials.kit.<id>}
 * @param variants per-rank variants, ordered best-first; empty for today's single-variant behaviour
 * @param preview whether the browse menu may open a read-only preview of this kit (default on)
 * @param closeOnClaim whether a successful browse-menu claim closes the menu instead of refreshing it
 * @param requirements opaque claim conditions (placeholder comparisons) evaluated before the cost; empty for none
 * @param requirementsDisplay the browse-menu icon override shown when the viewer fails the requirements; empty for the default
 * @param claimActions ordered typed actions run on a successful claim (before/after the item grant); empty for none
 * @param denyActions ordered typed actions run when a claim is refused by any gate; empty for none
 * @param schedule the optional availability window gating when the kit may be claimed; {@link KitSchedule#always()} for none
 * @param stockLimit the global cap on how many times the kit may ever be claimed across all players; {@code 0} for unlimited
 * @param parsePlaceholders whether the recipient's PlaceholderAPI tokens are resolved in each granted item's name and lore
 * @param onFull what the grant does when the inventory cannot hold everything: drop the overflow or deny the claim
 * @param unlockOnce whether the kit's {@code cost} is a one-time unlock fee: charged on the first claim, then waived
 *     forever once the player has unlocked it. Distinct from {@code oneTime} (consumed forever) and from a per-claim cost
 */
public record KitDefinition(
        KitId id,
        List<KitItem> items,
        Duration cooldown,
        boolean oneTime,
        boolean permission,
        KitCost cost,
        ItemDisplay display,
        List<String> commands,
        Optional<String> sound,
        Optional<String> particles,
        boolean firstJoin,
        boolean autoEquip,
        Optional<String> categoryId,
        java.math.BigDecimal claimMoney,
        String claimMoneyCurrency,
        java.util.Map<String, Duration> permissionCooldowns,
        int priority,
        ItemDisplay noPermission,
        ItemDisplay cooldownDisplay,
        ItemDisplay claimed,
        ItemDisplay unaffordable,
        Optional<String> customPermission,
        List<KitVariant> variants,
        boolean preview,
        boolean closeOnClaim,
        List<KitRequirement> requirements,
        ItemDisplay requirementsDisplay,
        List<KitAction> claimActions,
        List<KitAction> denyActions,
        KitSchedule schedule,
        int stockLimit,
        boolean parsePlaceholders,
        KitFullPolicy onFull,
        boolean unlockOnce) {

    public KitDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(particles, "particles");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(claimMoney, "claimMoney");
        Objects.requireNonNull(claimMoneyCurrency, "claimMoneyCurrency");
        Objects.requireNonNull(permissionCooldowns, "permissionCooldowns");
        Objects.requireNonNull(noPermission, "noPermission");
        Objects.requireNonNull(cooldownDisplay, "cooldownDisplay");
        Objects.requireNonNull(claimed, "claimed");
        Objects.requireNonNull(unaffordable, "unaffordable");
        Objects.requireNonNull(customPermission, "customPermission");
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(requirementsDisplay, "requirementsDisplay");
        Objects.requireNonNull(claimActions, "claimActions");
        Objects.requireNonNull(denyActions, "denyActions");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(onFull, "onFull");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("kit cooldown must not be negative: " + cooldown);
        }
        stockLimit = Math.max(0, stockLimit);
        items = List.copyOf(items);
        commands = List.copyOf(commands);
        permissionCooldowns = java.util.Map.copyOf(permissionCooldowns);
        variants = List.copyOf(variants);
        requirements = List.copyOf(requirements);
        claimActions = List.copyOf(claimActions);
        denyActions = List.copyOf(denyActions);
    }

    /** A new builder with every component at its default; set only what the kit needs and call {@code build()}. */
    public static KitDefinitionBuilder builder() {
        return new KitDefinitionBuilder();
    }

    /** A builder seeded from this kit, so a caller can change one component and rebuild the rest unchanged. */
    public KitDefinitionBuilder toBuilder() {
        return new KitDefinitionBuilder(this);
    }

    /** A free, repeatable, ungated kit with the given items and cooldown. */
    public static KitDefinition repeatable(KitId id, List<KitItem> items, Duration cooldown) {
        return builder().id(id).items(items).cooldown(cooldown).build();
    }

    /**
     * A copy of this kit with its {@code items} swapped for {@code newItems} and every other setting
     * cooldown, one-time, permission, cost, every display override, commands, sound, particles, first-join,
     * auto-equip, category, claim money, per-permission cooldowns, priority, custom permission, variants,
     * preview, close-on-claim, requirements, and the claim/deny actions: carried through unchanged. The item
     * editor uses this so editing a kit's stacks never silently wipes its other configuration.
     */
    public KitDefinition withItems(List<KitItem> newItems) {
        Objects.requireNonNull(newItems, "newItems");
        return toBuilder().items(newItems).build();
    }

    /** A copy of this kit with its one-time flag set to {@code value}, every other setting preserved. */
    public KitDefinition withOneTime(boolean value) {
        return toBuilder().oneTime(value).build();
    }

    /** A copy of this kit with its permission flag set to {@code value}, every other setting preserved. */
    public KitDefinition withPermission(boolean value) {
        return toBuilder().permission(value).build();
    }

    /** A copy of this kit with its cooldown set to {@code value}, every other setting preserved. */
    public KitDefinition withCooldown(Duration value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().cooldown(value).build();
    }

    /** A copy of this kit with its cost set to {@code value}, every other setting preserved. */
    public KitDefinition withCost(KitCost value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().cost(value).build();
    }

    /** A copy of this kit with its display name set to {@code value}, every other setting preserved. */
    public KitDefinition withDisplayName(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder()
                .display(new ItemDisplay(display.material(), value, display.lore()))
                .build();
    }

    /** A copy of this kit with its display material set to {@code value}, every other setting preserved. */
    public KitDefinition withDisplayMaterial(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder()
                .display(new ItemDisplay(value, display.name(), display.lore()))
                .build();
    }

    /** A copy of this kit with its display lore set to {@code value}, every other setting preserved. */
    public KitDefinition withDisplayLore(List<String> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder()
                .display(new ItemDisplay(display.material(), display.name(), value))
                .build();
    }

    /** A copy of this kit with its claim commands set to {@code value}, every other setting preserved. */
    public KitDefinition withCommands(List<String> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().commands(value).build();
    }

    /** A copy of this kit with its first-join flag set to {@code value}, every other setting preserved. */
    public KitDefinition withFirstJoin(boolean value) {
        return toBuilder().firstJoin(value).build();
    }

    /** A copy of this kit with its auto-equip flag set to {@code value}, every other setting preserved. */
    public KitDefinition withAutoEquip(boolean value) {
        return toBuilder().autoEquip(value).build();
    }

    /** A copy of this kit with its category set to {@code value}, every other setting preserved. */
    public KitDefinition withCategoryId(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().categoryId(value).build();
    }

    /** A copy of this kit with its preview flag set to {@code value}, every other setting preserved. */
    public KitDefinition withPreview(boolean value) {
        return toBuilder().preview(value).build();
    }

    /** A copy of this kit with its close-on-claim flag set to {@code value}, every other setting preserved. */
    public KitDefinition withCloseOnClaim(boolean value) {
        return toBuilder().closeOnClaim(value).build();
    }

    /** A copy of this kit with its custom permission set to {@code value}, every other setting preserved. */
    public KitDefinition withCustomPermission(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().customPermission(value).build();
    }

    /** A copy of this kit with its variants set to {@code value}, every other setting preserved. */
    public KitDefinition withVariants(List<KitVariant> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().variants(value).build();
    }

    /** A copy of this kit with its claim requirements set to {@code value}, every other setting preserved. */
    public KitDefinition withRequirements(List<KitRequirement> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().requirements(value).build();
    }

    /** A copy of this kit with its claim actions set to {@code value}, every other setting preserved. */
    public KitDefinition withClaimActions(List<KitAction> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().claimActions(value).build();
    }

    /** A copy of this kit with its deny actions set to {@code value}, every other setting preserved. */
    public KitDefinition withDenyActions(List<KitAction> value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().denyActions(value).build();
    }

    /** A copy of this kit with its availability schedule set to {@code value}, every other setting preserved. */
    public KitDefinition withSchedule(KitSchedule value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().schedule(value).build();
    }

    /** A copy of this kit with its global stock limit set to {@code value} ({@code 0} = unlimited), else preserved. */
    public KitDefinition withStockLimit(int value) {
        return toBuilder().stockLimit(Math.max(0, value)).build();
    }

    /** A copy of this kit with its parse-placeholders flag set to {@code value}, every other setting preserved. */
    public KitDefinition withParsePlaceholders(boolean value) {
        return toBuilder().parsePlaceholders(value).build();
    }

    /** A copy of this kit with its inventory-full policy set to {@code value}, every other setting preserved. */
    public KitDefinition withOnFull(KitFullPolicy value) {
        Objects.requireNonNull(value, "value");
        return toBuilder().onFull(value).build();
    }

    /** A copy of this kit with its buy-to-unlock flag set to {@code value}, every other setting preserved. */
    public KitDefinition withUnlockOnce(boolean value) {
        return toBuilder().unlockOnce(value).build();
    }

    /** True when claiming this kit consumes it forever (a one-time kit). */
    public boolean isOneTime() {
        return oneTime;
    }

    /** True when this kit requires a permission node beyond the base command node. */
    public boolean requiresPermission() {
        return permission;
    }

    /**
     * The permission node that gates this kit: the kit's {@code customPermission} when set, else the default
     * per-kit node {@code uxmessentials.kit.<id>}. Resolved here so several kits can share one permission.
     */
    public String permissionNode() {
        return customPermission.orElseGet(() -> id.permissionNode());
    }

    /** True when the kit sets a cost the economy gate should charge (a non-free price). */
    public boolean hasCost() {
        return !cost.isFree();
    }

    /** True when the kit carries claim requirements the gate must evaluate before charging. */
    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }

    /** True when the kit runs any action on a successful claim. */
    public boolean hasClaimActions() {
        return !claimActions.isEmpty();
    }

    /** True when the kit runs any action when a claim is refused. */
    public boolean hasDenyActions() {
        return !denyActions.isEmpty();
    }

    /** True when the kit restricts when it may be claimed (a non-empty {@link KitSchedule}). */
    public boolean isScheduled() {
        return !schedule.isAlways();
    }

    /** Whether the kit's schedule admits a claim at {@code now} (server-local); always true for an unscheduled kit. */
    public boolean isAvailableAt(LocalDateTime now) {
        return schedule.isAvailableAt(now);
    }

    /** True when the kit caps how many times it may ever be claimed across all players (a positive stock limit). */
    public boolean hasStockLimit() {
        return stockLimit > 0;
    }

    /** The cooldown in whole seconds, the unit the {@code Cooldowns} port resolves tiers in. */
    public long cooldownSeconds() {
        return cooldown.toSeconds();
    }
}
