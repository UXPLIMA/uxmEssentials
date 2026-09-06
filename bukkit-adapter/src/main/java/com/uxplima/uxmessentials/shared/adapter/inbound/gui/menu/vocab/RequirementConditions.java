package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The requirement slice of the menu condition vocabulary: the ways a spec can gate an item's visibility (its
 * {@code view} list) or a click on what the viewer actually holds, money, experience, an inventory item, a bit of
 * PDC meta, a free slot. Registered once at startup into the shared {@link MenuBindings} alongside the generic
 * {@link MenuVocabulary} conditions ({@code perm}, {@code has-prev/next}, {@code papi-compare}, {@code expr}), so a
 * disk-loaded spec resolves {@code has-money} / {@code has-item} / {@code has-empty-slots} the same way a
 * code-registered feature menu does.
 *
 * <p>A valued condition is written {@code has-money:100} in config; the parser leaves that whole (it is registry
 * blind), and the runtime's registry-aware split ({@code Ref.resolve} at the validation, view and click sites)
 * re-keys it to the registered head {@code has-money} with {@code 100} carried as {@code value}. So every handler
 * here reads its argument from {@code value}, exactly as an action does.
 *
 * <p><strong>Every condition fails closed.</strong> A requirement that cannot be verified must not pass, an
 * offline viewer, a malformed argument, an unknown material, or anything a lookup unexpectedly throws all answer
 * {@code false}. That is the safe default for a gate: better to hide an item or deny a click than to grant on a
 * value we could not read. The viewer's live {@link Player} is resolved from the open context's UUID; a viewer who
 * logged off in the gap resolves to {@code null} and the condition is {@code false}.
 *
 * <p>Threading: these run on the render/entity thread. {@code view} during the renderer's populate, a click gate on
 * the viewer's entity thread: where reading the viewer's own inventory, experience and PDC is Folia-safe. No
 * foreign player is touched. Nothing here produces player-facing text (a condition returns a boolean), so no
 * {@code MessageKey} is involved; the only text is a diagnostic {@code log} line when a lookup throws.
 */
public final class RequirementConditions {

    /** Splits an argument on runs of whitespace; the input is stripped first so no empty leading/trailing token slips in. */
    private static final Splitter WHITESPACE = Splitter.on(Pattern.compile("\\s+"));

    private RequirementConditions() {}

    /**
     * Register the requirement conditions into {@code bindings}. {@code currencies} is the Phase-0 multi-currency
     * façade {@code has-money} resolves a back-end through; {@code playerMeta} is the PDC accessor {@code has-meta}
     * reads; {@code log} is the operator console logger a fail-closed condition warns through when a lookup throws.
     * Left separate from {@link MenuVocabulary#registerConditions} so that method's existing call-sites stay
     * untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Currencies currencies, PlayerMeta playerMeta, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(playerMeta, "playerMeta");
        Objects.requireNonNull(log, "log");
        bindings.condition("has-money", closed("has-money", log, (ctx, args) -> hasMoney(ctx, args, currencies)));
        bindings.condition("has-exp", closed("has-exp", log, RequirementConditions::hasExp));
        bindings.condition("has-level", closed("has-level", log, RequirementConditions::hasLevel));
        bindings.condition("has-item", closed("has-item", log, RequirementConditions::hasItem));
        bindings.condition("has-meta", closed("has-meta", log, (ctx, args) -> hasMeta(ctx, args, playerMeta)));
        bindings.condition("has-empty-slots", closed("has-empty-slots", log, RequirementConditions::hasEmptySlots));
        bindings.condition("check-inventory", closed("check-inventory", log, RequirementConditions::checkInventory));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a {@code false}
     * result rather than escaping into the render or click path. A condition that cannot be evaluated must fail
     * closed, the same discipline every gate here already applies to a bad argument.
     */
    private static BiPredicate<MenuContext, Map<String, String>> closed(
            String condition, Logger log, BiPredicate<MenuContext, Map<String, String>> body) {
        return (ctx, args) -> {
            try {
                return body.test(ctx, args);
            } catch (RuntimeException failure) {
                log.warn("event=menu_requirement_failed condition={} value={}", condition, value(args));
                return false;
            }
        };
    }

    /** {@code has-money:<amount> [currency-spec]}: whether the viewer holds at least {@code amount} of that currency. */
    private static boolean hasMoney(MenuContext ctx, Map<String, String> args, Currencies currencies) {
        Player player = viewer(ctx);
        if (player == null) {
            return false;
        }
        return EconomyActions.MoneyArg.parse(value(args))
                .map(arg -> currencies.resolve(arg.currency()).has(player.getUniqueId(), arg.amount()))
                .orElse(false);
    }

    /** {@code has-exp:<amount>}: whether the viewer's total experience points meet the threshold. */
    private static boolean hasExp(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        OptionalInt amount = parseInt(value(args));
        return player != null && amount.isPresent() && player.getTotalExperience() >= amount.getAsInt();
    }

    /** {@code has-level:<amount>}: whether the viewer's experience level meets the threshold. */
    private static boolean hasLevel(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        OptionalInt amount = parseInt(value(args));
        return player != null && amount.isPresent() && player.getLevel() >= amount.getAsInt();
    }

    /**
     * {@code has-item:<material> [amount] [name:<exact display name>]}. Whether the viewer's main storage holds at
     * least {@code amount} (default 1) matching stacks. The core match is the material; an optional {@code name:}
     * suffix additionally requires each counted stack's plain-text display name to equal the given text (which may
     * itself carry spaces). An unknown material or an unparsable argument is {@code false}.
     */
    private static boolean hasItem(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        ItemMatch match = ItemMatch.parse(value(args));
        if (player == null || match == null) {
            return false;
        }
        Material material = Material.matchMaterial(match.materialToken());
        if (material == null) {
            return false;
        }
        int have = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() != material) {
                continue;
            }
            if (match.name() != null && !displayNameEquals(stack, match.name())) {
                continue;
            }
            have += stack.getAmount();
        }
        return have >= match.amount();
    }

    /**
     * {@code has-meta:<key> [value]}. Whether the viewer's PDC holds {@code key}, and, when a value is given, whether
     * the stored value equals it exactly (spaces preserved, mirroring how {@code meta-set} stores it). A blank key is
     * {@code false}.
     */
    private static boolean hasMeta(MenuContext ctx, Map<String, String> args, PlayerMeta playerMeta) {
        Player player = viewer(ctx);
        if (player == null) {
            return false;
        }
        String raw = value(args).strip();
        if (raw.isEmpty()) {
            return false;
        }
        String[] parts = raw.split("\\s+", 2);
        String key = parts[0];
        if (parts.length == 1) {
            return playerMeta.has(player, key);
        }
        return playerMeta.get(player, key).map(parts[1]::equals).orElse(false);
    }

    /** {@code has-empty-slots:<count>}: whether the viewer's main storage has at least {@code count} empty slots. */
    private static boolean hasEmptySlots(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        OptionalInt count = parseInt(value(args));
        if (player == null || count.isEmpty()) {
            return false;
        }
        int empty = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                empty++;
            }
        }
        return empty >= count.getAsInt();
    }

    /**
     * {@code check-inventory:<slot> <material>}, whether the item in raw slot {@code slot} of the viewer's inventory
     * is present and of that material. A missing operand, a non-numeric slot, an unknown material, or a slot out of
     * range is {@code false}.
     */
    private static boolean checkInventory(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        if (player == null) {
            return false;
        }
        List<String> parts = WHITESPACE.splitToList(value(args).strip());
        if (parts.size() < 2) {
            return false;
        }
        OptionalInt slot = parseInt(parts.get(0));
        Material material = Material.matchMaterial(parts.get(1));
        if (slot.isEmpty() || material == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int index = slot.getAsInt();
        if (index < 0 || index >= inventory.getSize()) {
            return false;
        }
        ItemStack stack = inventory.getItem(index);
        return stack != null && stack.getType() == material;
    }

    /** The live {@link Player} for the open context's viewer, or {@code null} when that player is offline. */
    private static @Nullable Player viewer(MenuContext ctx) {
        return Bukkit.getPlayer(ctx.viewer().uuid());
    }

    /** The single positional argument the runtime's split carried onto the condition ref, or empty when it had none. */
    private static String value(Map<String, String> args) {
        return args.getOrDefault("value", "");
    }

    /** Whether {@code stack}'s plain-text display name equals {@code expected}; a nameless item never matches. */
    private static boolean displayNameEquals(ItemStack stack, String expected) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        Component name = meta.displayName();
        return name != null
                && PlainTextComponentSerializer.plainText().serialize(name).equals(expected);
    }

    /** The whole number in {@code token}, or empty when it is blank or not an integer. */
    private static OptionalInt parseInt(String token) {
        try {
            return OptionalInt.of(Integer.parseInt(token.strip()));
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    /**
     * A parsed {@code has-item} argument: the material token, a required count (default 1, never below 1), and an
     * optional exact display-name match. The grammar is {@code <material> [amount] [name:<exact>]}, {@code name:}
     * and everything after it (which may contain spaces) is the display name, and the material plus optional amount
     * precede it. Parsing is Bukkit-free (the material token is resolved to a {@link Material} by the caller, so an
     * unknown material fails there) so a plain-JUnit test can exercise the grammar; a blank or nameless-only argument
     * yields {@code null}.
     */
    public record ItemMatch(
            String materialToken, int amount, @Nullable String name) {

        public ItemMatch {
            Objects.requireNonNull(materialToken, "materialToken");
        }

        public static @Nullable ItemMatch parse(String value) {
            Objects.requireNonNull(value, "value");
            String work = value.strip();
            String name = null;
            int marker = work.indexOf("name:");
            if (marker >= 0) {
                String candidate = work.substring(marker + "name:".length()).strip();
                name = candidate.isEmpty() ? null : candidate;
                work = work.substring(0, marker).strip();
            }
            if (work.isEmpty()) {
                return null;
            }
            List<String> parts = WHITESPACE.splitToList(work);
            int amount = parts.size() > 1 ? parseInt(parts.get(1)).orElse(1) : 1;
            return new ItemMatch(parts.get(0), Math.max(1, amount), name);
        }
    }
}
