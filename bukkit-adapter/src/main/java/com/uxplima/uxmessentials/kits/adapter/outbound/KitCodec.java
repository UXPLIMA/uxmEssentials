package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.domain.ItemDisplay;
import com.uxplima.uxmessentials.kits.domain.KitAction;
import com.uxplima.uxmessentials.kits.domain.KitActionType;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads and writes one kit as a {@link KitDefinition}. Each kit lives in its own {@code <id>.conf} under
 * {@code modules/kits/kits/}, whose root node <em>is</em> the kit:
 *
 * <pre>{@code
 * # modules/kits/kits/starter.conf
 * cooldown = 0          # seconds between claims; 0 = no cooldown
 * one-time = true       # claimable once per player
 * permission = false    # require uxmessentials.kit.<id>
 * cost = 0              # claim price; charged only when economy is wired
 * items = [ "<base64>", { data = "<base64>", amount = 16 } ]
 * requirements = [      # placeholder claim conditions; evaluated through PlaceholderAPI before the cost
 *   "%player_level% >= 10",
 *   "%essentials_playtime_seconds% >= 3600"
 * ]
 * requirements-material = "BARRIER"   # browse-menu icon shown when the viewer fails the requirements
 * requirements-name = "<red>Locked"   # browse-menu name for that state
 * requirements-lore = [ "<gray>Level up to unlock" ]  # browse-menu lore for that state
 * }</pre>
 *
 * <p>Each {@code requirements} entry is split into a {@code KitRequirement(left, operator, right)} on the first
 * of the operators {@code == != >= <= > <}; spacing is tolerated and a malformed entry (no operator or a blank
 * side) is skipped rather than failing the kit. The operands stay opaque raw strings here, the adapter never
 * resolves the placeholders; the {@code RequirementEvaluator} does, on the claim/render thread.
 *
 * The same codec also reads each entry of the previous {@code kits.conf} monolith, a {@code kits { <id> {
 * ... } }} tree, which the repository imports once on first load and splits into per-file kits. An item is
 * either a bare Base64 string (its serialized form carries the amount) or a {@code {data, amount}} map for
 * readability. A read that cannot produce a valid definition returns empty so the repository skips the kit
 * rather than failing the whole load.
 */
@NullMarked
final class KitCodec {

    private KitCodec() {}

    /** Parse the node {@code node} for id {@code id} into a definition, or empty when it is malformed. */
    static Optional<KitDefinition> read(String id, ConfigurationNode node, Logger log) {
        try {
            KitId kitId = KitId.of(id);
            List<KitItem> items = readItems(node.node("items"));
            Duration cooldown =
                    Duration.ofSeconds(Math.max(0L, node.node("cooldown").getLong(0L)));
            boolean oneTime = node.node("one-time").getBoolean(false);
            boolean permission = node.node("permission").getBoolean(false);
            KitCost cost = readCost(node.node("cost"));

            Optional<String> displayName =
                    Optional.ofNullable(node.node("display-name").getString());
            Optional<String> displayMaterial =
                    Optional.ofNullable(node.node("display-material").getString());
            List<String> displayLore = strings(node.node("display-lore"));
            List<String> commands = strings(node.node("commands"));
            Optional<String> sound = Optional.ofNullable(node.node("sound").getString());
            Optional<String> particles =
                    Optional.ofNullable(node.node("particles").getString());

            boolean firstJoin = node.node("first-join").getBoolean(false);
            boolean autoEquip = node.node("auto-equip").getBoolean(false);

            Optional<String> categoryId =
                    Optional.ofNullable(node.node("category-id").getString());
            BigDecimal claimMoney = BigDecimal.ZERO;
            String claimMoneyCurrency = "default";
            String rawClaimMoney = node.node("claim-money").getString("");
            if (rawClaimMoney != null && !rawClaimMoney.isBlank()) {
                try {
                    java.util.List<String> parts = com.google.common.base.Splitter.on(' ')
                            .omitEmptyStrings()
                            .trimResults()
                            .splitToList(rawClaimMoney.strip());
                    if (!parts.isEmpty()) {
                        claimMoney = new BigDecimal(parts.get(0));
                        if (parts.size() > 1) {
                            claimMoneyCurrency = parts.get(1).toLowerCase(java.util.Locale.ROOT);
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore parsing errors for malformed numbers
                }
            }
            String explicitCurrency = node.node("claim-money-currency").getString("");
            if (explicitCurrency != null && !explicitCurrency.isBlank()) {
                claimMoneyCurrency = explicitCurrency.strip().toLowerCase(java.util.Locale.ROOT);
            }
            int priority = node.node("priority").getInt(0);

            Optional<String> noPermissionMaterial =
                    Optional.ofNullable(node.node("no-permission-material").getString());
            Optional<String> noPermissionName =
                    Optional.ofNullable(node.node("no-permission-name").getString());
            List<String> noPermissionLore = strings(node.node("no-permission-lore"));

            Optional<String> cooldownMaterial =
                    Optional.ofNullable(node.node("cooldown-material").getString());
            Optional<String> cooldownName =
                    Optional.ofNullable(node.node("cooldown-name").getString());
            List<String> cooldownLore = strings(node.node("cooldown-lore"));

            Optional<String> claimedMaterial =
                    Optional.ofNullable(node.node("claimed-material").getString());
            Optional<String> claimedName =
                    Optional.ofNullable(node.node("claimed-name").getString());
            List<String> claimedLore = strings(node.node("claimed-lore"));

            Optional<String> unaffordableMaterial =
                    Optional.ofNullable(node.node("unaffordable-material").getString());
            Optional<String> unaffordableName =
                    Optional.ofNullable(node.node("unaffordable-name").getString());
            List<String> unaffordableLore = strings(node.node("unaffordable-lore"));

            java.util.Map<String, Duration> permissionCooldowns = new java.util.HashMap<>();
            ConfigurationNode cooldownsNode = node.node("permission-cooldowns");
            if (!cooldownsNode.virtual() && cooldownsNode.isMap()) {
                for (java.util.Map.Entry<Object, ? extends ConfigurationNode> entry :
                        cooldownsNode.childrenMap().entrySet()) {
                    String group = String.valueOf(entry.getKey());
                    long seconds = entry.getValue().getLong(-1L);
                    if (seconds >= 0) {
                        permissionCooldowns.put(group, Duration.ofSeconds(seconds));
                    }
                }
            }

            String rawCustomPermission = node.node("permission-node").getString("");
            Optional<String> customPermission = rawCustomPermission == null || rawCustomPermission.isBlank()
                    ? Optional.empty()
                    : Optional.of(rawCustomPermission.strip());
            List<com.uxplima.uxmessentials.kits.domain.KitVariant> variants = readVariants(node.node("variants"));
            boolean preview = node.node("preview").getBoolean(true);
            boolean closeOnClaim = node.node("close-on-claim").getBoolean(false);
            com.uxplima.uxmessentials.kits.domain.KitSchedule schedule = readSchedule(node.node("schedule"));
            int stockLimit = Math.max(0, node.node("stock").getInt(0));
            boolean parsePlaceholders = node.node("parse-placeholders").getBoolean(false);
            com.uxplima.uxmessentials.kits.domain.KitFullPolicy onFull =
                    com.uxplima.uxmessentials.kits.domain.KitFullPolicy.parse(
                            node.node("on-full").getString());
            boolean unlockOnce = node.node("unlock-once").getBoolean(false);

            List<com.uxplima.uxmessentials.kits.domain.KitRequirement> requirements =
                    readRequirements(node.node("requirements"));
            Optional<String> requirementsMaterial =
                    Optional.ofNullable(node.node("requirements-material").getString());
            Optional<String> requirementsName =
                    Optional.ofNullable(node.node("requirements-name").getString());
            List<String> requirementsLore = strings(node.node("requirements-lore"));

            List<KitAction> claimActions = readClaimActions(id, node, commands, sound, particles, log);
            List<KitAction> denyActions = readActions(node.node("deny-actions"));

            return Optional.of(KitDefinition.builder()
                    .id(kitId)
                    .items(items)
                    .cooldown(cooldown)
                    .oneTime(oneTime)
                    .permission(permission)
                    .cost(cost)
                    .display(new ItemDisplay(displayMaterial, displayName, displayLore))
                    .commands(commands)
                    .sound(sound)
                    .particles(particles)
                    .firstJoin(firstJoin)
                    .autoEquip(autoEquip)
                    .categoryId(categoryId)
                    .claimMoney(claimMoney)
                    .claimMoneyCurrency(claimMoneyCurrency)
                    .permissionCooldowns(permissionCooldowns)
                    .priority(priority)
                    .noPermission(new ItemDisplay(noPermissionMaterial, noPermissionName, noPermissionLore))
                    .cooldownDisplay(new ItemDisplay(cooldownMaterial, cooldownName, cooldownLore))
                    .claimed(new ItemDisplay(claimedMaterial, claimedName, claimedLore))
                    .unaffordable(new ItemDisplay(unaffordableMaterial, unaffordableName, unaffordableLore))
                    .customPermission(customPermission)
                    .variants(variants)
                    .preview(preview)
                    .closeOnClaim(closeOnClaim)
                    .requirements(requirements)
                    .requirementsDisplay(new ItemDisplay(requirementsMaterial, requirementsName, requirementsLore))
                    .claimActions(claimActions)
                    .denyActions(denyActions)
                    .schedule(schedule)
                    .stockLimit(stockLimit)
                    .parsePlaceholders(parsePlaceholders)
                    .onFull(onFull)
                    .unlockOnce(unlockOnce)
                    .build());
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    /** Write {@code definition} into {@code node} in the documented shape. */
    static void write(ConfigurationNode node, KitDefinition definition) throws ConfigurateException {
        node.node("cooldown").set(definition.cooldownSeconds());
        node.node("one-time").set(definition.oneTime());
        node.node("permission").set(definition.permission());
        node.node("cost")
                .set(definition.cost().amount().toPlainString() + " "
                        + definition.cost().currencyId());
        node.node("first-join").set(definition.firstJoin());
        node.node("auto-equip").set(definition.autoEquip());

        if (definition.categoryId().isPresent()) {
            node.node("category-id").set(definition.categoryId().get());
        } else {
            node.node("category-id").set(null);
        }
        node.node("claim-money").set(definition.claimMoney().toPlainString() + " " + definition.claimMoneyCurrency());
        node.node("priority").set(definition.priority());

        node.node("permission-node").set(definition.customPermission().orElse(null));
        node.node("preview").set(definition.preview() ? null : false);
        node.node("close-on-claim").set(definition.closeOnClaim() ? true : null);
        writeVariants(node.node("variants"), definition.variants());

        node.node("no-permission-material")
                .set(definition.noPermission().material().orElse(null));
        node.node("no-permission-name").set(definition.noPermission().name().orElse(null));
        node.node("no-permission-lore")
                .set(
                        definition.noPermission().lore().isEmpty()
                                ? null
                                : definition.noPermission().lore());

        node.node("cooldown-material")
                .set(definition.cooldownDisplay().material().orElse(null));
        node.node("cooldown-name").set(definition.cooldownDisplay().name().orElse(null));
        node.node("cooldown-lore")
                .set(
                        definition.cooldownDisplay().lore().isEmpty()
                                ? null
                                : definition.cooldownDisplay().lore());

        node.node("claimed-material").set(definition.claimed().material().orElse(null));
        node.node("claimed-name").set(definition.claimed().name().orElse(null));
        node.node("claimed-lore")
                .set(
                        definition.claimed().lore().isEmpty()
                                ? null
                                : definition.claimed().lore());

        node.node("unaffordable-material")
                .set(definition.unaffordable().material().orElse(null));
        node.node("unaffordable-name").set(definition.unaffordable().name().orElse(null));
        node.node("unaffordable-lore")
                .set(
                        definition.unaffordable().lore().isEmpty()
                                ? null
                                : definition.unaffordable().lore());

        writeRequirements(node.node("requirements"), definition.requirements());
        node.node("requirements-material")
                .set(definition.requirementsDisplay().material().orElse(null));
        node.node("requirements-name")
                .set(definition.requirementsDisplay().name().orElse(null));
        node.node("requirements-lore")
                .set(
                        definition.requirementsDisplay().lore().isEmpty()
                                ? null
                                : definition.requirementsDisplay().lore());

        writeSchedule(node.node("schedule"), definition.schedule());
        node.node("stock").set(definition.stockLimit() > 0 ? definition.stockLimit() : null);
        node.node("parse-placeholders").set(definition.parsePlaceholders() ? true : null);
        node.node("on-full")
                .set(
                        definition.onFull() == com.uxplima.uxmessentials.kits.domain.KitFullPolicy.DROP
                                ? null
                                : definition.onFull().token());
        node.node("unlock-once").set(definition.unlockOnce() ? true : null);

        node.node("permission-cooldowns").set(null);
        if (!definition.permissionCooldowns().isEmpty()) {
            ConfigurationNode cooldownsNode = node.node("permission-cooldowns");
            for (java.util.Map.Entry<String, Duration> entry :
                    definition.permissionCooldowns().entrySet()) {
                cooldownsNode.node(entry.getKey()).set(entry.getValue().toSeconds());
            }
        }

        if (definition.display().name().isPresent()) {
            node.node("display-name").set(definition.display().name().get());
        } else {
            node.node("display-name").set(null);
        }
        if (definition.display().material().isPresent()) {
            node.node("display-material").set(definition.display().material().get());
        } else {
            node.node("display-material").set(null);
        }
        if (!definition.display().lore().isEmpty()) {
            node.node("display-lore").set(definition.display().lore());
        } else {
            node.node("display-lore").set(null);
        }

        writeClaimAndDenyActions(node, definition);

        writeItems(node.node("items"), definition.items());
    }

    /**
     * Write the typed action engine. A kit whose claim actions are exactly the mapping of its legacy
     * {@code commands}/{@code sound}/{@code particles} keys is written back in the legacy shape, so a kit the
     * GUI editor only ever touched through its command list round-trips unchanged and editor edits persist. Any
     * other claim actions (an authored {@code claim-actions} block) and any deny actions are written in the new
     * {@code claim-actions}/{@code deny-actions} shape, and the legacy keys are then cleared so the two never
     * both appear. Re-reading either shape yields the identical kit.
     */
    private static void writeClaimAndDenyActions(ConfigurationNode node, KitDefinition definition)
            throws ConfigurateException {
        node.node("commands").set(null);
        node.node("sound").set(null);
        node.node("particles").set(null);
        node.node("claim-actions").set(null);
        node.node("deny-actions").set(null);
        if (isLegacyShape(definition)) {
            writeLegacyClaimKeys(node, definition);
        } else if (definition.hasClaimActions()) {
            writeActions(node.node("claim-actions"), definition.claimActions());
        }
        if (definition.hasDenyActions()) {
            writeActions(node.node("deny-actions"), definition.denyActions());
        }
    }

    /** Whether a kit's claim actions are exactly the mapping of its legacy keys (so it writes the legacy shape). */
    private static boolean isLegacyShape(KitDefinition definition) {
        return definition
                .claimActions()
                .equals(mapLegacyClaimActions(definition.commands(), definition.sound(), definition.particles()));
    }

    /** Write the legacy {@code commands}/{@code sound}/{@code particles} keys for a kit still in the old shape. */
    private static void writeLegacyClaimKeys(ConfigurationNode node, KitDefinition definition)
            throws ConfigurateException {
        if (!definition.commands().isEmpty()) {
            node.node("commands").set(definition.commands());
        }
        definition.sound().ifPresent(value -> setQuietly(node.node("sound"), value));
        definition.particles().ifPresent(value -> setQuietly(node.node("particles"), value));
    }

    private static void setQuietly(ConfigurationNode node, String value) {
        try {
            node.set(value);
        } catch (ConfigurateException unreachable) {
            // A scalar String set on a leaf node cannot fail; rethrow so a future change surfaces loudly.
            throw new IllegalStateException("failed to write kit action key", unreachable);
        }
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value.strip());
            }
        }
        return List.copyOf(values);
    }

    private static List<KitItem> readItems(ConfigurationNode node) {
        List<KitItem> items = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readItem(child).ifPresent(items::add);
        }
        return items;
    }

    /** The inventory slot the off-hand maps to in a {@code PlayerInventory}; {@code offhand = true} is its alias. */
    private static final int OFF_HAND_SLOT = 40;

    private static Optional<KitItem> readItem(ConfigurationNode child) {
        if (child.isMap()) {
            String data = child.node("data").getString("");
            int amount = Math.max(1, child.node("amount").getInt(1));
            Optional<Integer> slot = readSlot(child);
            return data.isBlank() ? Optional.empty() : Optional.of(KitItem.of(data, amount, slot));
        }
        String raw = child.getString("");
        return raw.isBlank() ? Optional.empty() : Optional.of(KitItem.of(raw, 1, Optional.empty()));
    }

    private static Optional<Integer> readSlot(ConfigurationNode child) {
        if (child.node("offhand").getBoolean(false)) {
            return Optional.of(OFF_HAND_SLOT);
        }
        return child.node("slot").virtual()
                ? Optional.empty()
                : Optional.of(child.node("slot").getInt());
    }

    /**
     * Read the optional {@code schedule} block gating when the kit may be claimed:
     *
     * <pre>{@code
     * schedule {
     *   days = [ "FRIDAY", "SATURDAY", "SUNDAY" ]   # weekday names; absent/empty = every day
     *   daily-start = "20:00"                        # HH:mm daily window start (may wrap past midnight)
     *   daily-end = "23:00"                          # HH:mm daily window end
     *   from = "2026-06-01T00:00"                    # ISO local date-time absolute window start
     *   until = "2026-07-01T00:00"                   # ISO local date-time absolute window end
     * }
     * }</pre>
     *
     * <p>An absent block (or one whose every key is malformed) yields {@link KitSchedule#always()} so the kit is
     * claimable at any time, the back-compatible default. Each constraint is tolerant: an unrecognised day name or
     * an unparseable time/date-time is skipped rather than failing the kit's load.
     */
    private static com.uxplima.uxmessentials.kits.domain.KitSchedule readSchedule(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return com.uxplima.uxmessentials.kits.domain.KitSchedule.always();
        }
        java.util.Set<java.time.DayOfWeek> days = readDays(node.node("days"));
        Optional<java.time.LocalTime> start = parseTime(node.node("daily-start").getString());
        Optional<java.time.LocalTime> end = parseTime(node.node("daily-end").getString());
        Optional<java.time.LocalDateTime> from = parseDateTime(node.node("from").getString());
        Optional<java.time.LocalDateTime> until =
                parseDateTime(node.node("until").getString());
        return new com.uxplima.uxmessentials.kits.domain.KitSchedule(days, start, end, from, until);
    }

    private static java.util.Set<java.time.DayOfWeek> readDays(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return java.util.Set.of();
        }
        java.util.EnumSet<java.time.DayOfWeek> days = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
        for (ConfigurationNode child : node.childrenList()) {
            parseDay(child.getString()).ifPresent(days::add);
        }
        return days;
    }

    private static Optional<java.time.DayOfWeek> parseDay(@org.jspecify.annotations.Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(java.time.DayOfWeek.valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException notADay) {
            return Optional.empty();
        }
    }

    private static Optional<java.time.LocalTime> parseTime(@org.jspecify.annotations.Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(java.time.LocalTime.parse(raw.strip()));
        } catch (java.time.format.DateTimeParseException notATime) {
            return Optional.empty();
        }
    }

    private static Optional<java.time.LocalDateTime> parseDateTime(@org.jspecify.annotations.Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(java.time.LocalDateTime.parse(raw.strip()));
        } catch (java.time.format.DateTimeParseException notADateTime) {
            return Optional.empty();
        }
    }

    private static void writeSchedule(
            ConfigurationNode node, com.uxplima.uxmessentials.kits.domain.KitSchedule schedule)
            throws ConfigurateException {
        if (schedule.isAlways()) {
            node.set(null);
            return;
        }
        node.node("days")
                .set(
                        schedule.days().isEmpty()
                                ? null
                                : schedule.days().stream().map(Enum::name).toList());
        node.node("daily-start")
                .set(schedule.dailyStart().map(java.time.LocalTime::toString).orElse(null));
        node.node("daily-end")
                .set(schedule.dailyEnd().map(java.time.LocalTime::toString).orElse(null));
        node.node("from")
                .set(schedule.from().map(java.time.LocalDateTime::toString).orElse(null));
        node.node("until")
                .set(schedule.until().map(java.time.LocalDateTime::toString).orElse(null));
    }

    private static List<com.uxplima.uxmessentials.kits.domain.KitRequirement> readRequirements(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<com.uxplima.uxmessentials.kits.domain.KitRequirement> requirements = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String entry = child.getString();
            if (entry == null || entry.isBlank()) {
                continue;
            }
            // A malformed entry (no recognised operator or a blank side) is skipped, not fatal.
            com.uxplima.uxmessentials.kits.domain.KitRequirement.parse(entry).ifPresent(requirements::add);
        }
        return List.copyOf(requirements);
    }

    /**
     * Resolve a kit's effective claim actions. The new {@code claim-actions} block, when present, wins outright:
     * it is the typed, ordered action engine that supersedes the flat {@code commands}/{@code sound}/{@code
     * particles} keys, and when both are present the legacy keys are ignored with a one-line warn. When no new
     * block is authored the legacy keys are mapped into actions so an unmodified kit behaves exactly as before
     * {@code commands} become {@code CONSOLE_COMMAND} actions in order, then {@code sound} a {@code SOUND} action,
     * then {@code particles} a {@code PARTICLE} action.
     */
    private static List<KitAction> readClaimActions(
            String id,
            ConfigurationNode node,
            List<String> commands,
            Optional<String> sound,
            Optional<String> particles,
            Logger log) {
        ConfigurationNode claimNode = node.node("claim-actions");
        boolean hasNewBlock = !claimNode.virtual() && claimNode.isList();
        boolean hasLegacy = !commands.isEmpty() || sound.isPresent() || particles.isPresent();
        if (hasNewBlock) {
            if (hasLegacy) {
                log.warn("kit " + id + ": claim-actions present, ignoring legacy commands/sound/particles keys");
            }
            return readActions(claimNode);
        }
        return mapLegacyClaimActions(commands, sound, particles);
    }

    /** Map the legacy {@code commands}/{@code sound}/{@code particles} keys into ordered claim actions. */
    private static List<KitAction> mapLegacyClaimActions(
            List<String> commands, Optional<String> sound, Optional<String> particles) {
        List<KitAction> mapped = new ArrayList<>();
        for (String command : commands) {
            mapped.add(KitAction.of(KitActionType.CONSOLE_COMMAND, command));
        }
        sound.ifPresent(value -> mapped.add(KitAction.of(KitActionType.SOUND, value)));
        particles.ifPresent(value -> mapped.add(KitAction.of(KitActionType.PARTICLE, value)));
        return List.copyOf(mapped);
    }

    /** Read an ordered {@code claim-actions}/{@code deny-actions} list; a malformed entry is skipped, not fatal. */
    private static List<KitAction> readActions(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<KitAction> actions = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readAction(child).ifPresent(actions::add);
        }
        return List.copyOf(actions);
    }

    private static Optional<KitAction> readAction(ConfigurationNode child) {
        String type = child.node("type").getString("");
        String value = child.node("value").getString("");
        if (type == null || type.isBlank() || value == null) {
            return Optional.empty();
        }
        boolean beforeItems = child.node("before-items").getBoolean(false);
        boolean countAsItem = child.node("count-as-item").getBoolean(false);
        return KitAction.parse(type, value, beforeItems, countAsItem);
    }

    private static void writeActions(ConfigurationNode node, List<KitAction> actions) throws ConfigurateException {
        node.set(null);
        for (KitAction action : actions) {
            ConfigurationNode child = node.appendListNode();
            child.node("type").set(action.type().token());
            child.node("value").set(action.value());
            child.node("before-items").set(action.beforeItems() ? true : null);
            child.node("count-as-item").set(action.countAsItem() ? true : null);
        }
    }

    private static List<com.uxplima.uxmessentials.kits.domain.KitVariant> readVariants(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return List.of();
        }
        List<com.uxplima.uxmessentials.kits.domain.KitVariant> variants = new ArrayList<>();
        for (java.util.Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            readVariant(entry.getValue()).ifPresent(variants::add);
        }
        return List.copyOf(variants);
    }

    private static Optional<com.uxplima.uxmessentials.kits.domain.KitVariant> readVariant(ConfigurationNode node) {
        String permission = node.node("permission").getString("");
        if (permission == null || permission.isBlank()) {
            return Optional.empty();
        }
        List<KitItem> items = readItems(node.node("items"));
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Optional<Duration> cooldown = node.node("cooldown").virtual()
                ? Optional.empty()
                : Optional.of(
                        Duration.ofSeconds(Math.max(0L, node.node("cooldown").getLong(0L))));
        Optional<KitCost> cost =
                node.node("cost").virtual() ? Optional.empty() : Optional.of(readCost(node.node("cost")));
        return Optional.of(
                new com.uxplima.uxmessentials.kits.domain.KitVariant(permission.strip(), items, cooldown, cost));
    }

    private static void writeVariants(
            ConfigurationNode node, List<com.uxplima.uxmessentials.kits.domain.KitVariant> variants)
            throws ConfigurateException {
        node.set(null);
        int index = 0;
        for (com.uxplima.uxmessentials.kits.domain.KitVariant variant : variants) {
            ConfigurationNode child = node.node("tier" + index++);
            child.node("permission").set(variant.permission());
            if (variant.cooldown().isPresent()) {
                child.node("cooldown").set(variant.cooldown().get().toSeconds());
            }
            if (variant.cost().isPresent()) {
                child.node("cost")
                        .set(variant.cost().get().amount().toPlainString() + " "
                                + variant.cost().get().currencyId());
            }
            writeItems(child.node("items"), variant.items());
        }
    }

    private static void writeRequirements(
            ConfigurationNode node, List<com.uxplima.uxmessentials.kits.domain.KitRequirement> requirements)
            throws ConfigurateException {
        if (requirements.isEmpty()) {
            node.set(null);
            return;
        }
        node.setList(
                String.class,
                requirements.stream()
                        .map(com.uxplima.uxmessentials.kits.domain.KitRequirement::asText)
                        .toList());
    }

    private static void writeItems(ConfigurationNode node, List<KitItem> items) throws ConfigurateException {
        node.set(null);
        for (KitItem item : items) {
            ConfigurationNode child = node.appendListNode();
            child.node("data").set(item.data());
            child.node("amount").set(item.amount());
            if (item.slot().isPresent()) {
                if (item.slot().get() == OFF_HAND_SLOT) {
                    child.node("offhand").set(true);
                } else {
                    child.node("slot").set(item.slot().get());
                }
            }
        }
    }

    private static KitCost readCost(ConfigurationNode node) {
        String raw = node.getString("");
        if (raw.isBlank()) {
            return KitCost.free();
        }
        try {
            java.util.List<String> parts = com.google.common.base.Splitter.on(' ')
                    .omitEmptyStrings()
                    .trimResults()
                    .splitToList(raw.strip());
            if (parts.isEmpty()) {
                return KitCost.free();
            }
            BigDecimal amount = new BigDecimal(parts.get(0));
            if (amount.signum() <= 0) {
                return KitCost.free();
            }
            String currency = parts.size() > 1 ? parts.get(1).toLowerCase(java.util.Locale.ROOT) : "default";
            return KitCost.of(amount, currency);
        } catch (Exception notANumber) {
            return KitCost.free();
        }
    }
}
