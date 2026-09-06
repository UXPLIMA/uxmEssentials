package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the display icons the {@code /kit list} browse menu shows, one per kit and one per category, resolving
 * each icon's player-relative name, material, and lore against the viewer's permission, cooldown, one-time, and
 * affordability state. The renderer holds no menu or scheduler state: it reads the kit/category and the viewer
 * and returns an {@link ItemStack}, so it is a pure presentation collaborator the menu view delegates to. Every
 * line resolves from a {@link MessageKey} in the viewer's locale unless the kit carries its own override text,
 * and cost/cooldown placeholders (plus any PlaceholderAPI tokens) are substituted before MiniMessage parsing.
 */
@NullMarked
final class KitIconRenderer {

    private final Messages messages;
    private final KitAccess access;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;
    private final Clock clock;

    KitIconRenderer(Messages messages, KitAccess access, GuiLayout layout, MiniMessage miniMessage, Clock clock) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.access = Objects.requireNonNull(access, "access");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    ItemStack categoryIcon(PlayerRef viewer, KitCategory category) {
        Component name =
                text(viewer, KitsMessageKey.KIT_MENU_CATEGORY_NAME, Map.of("category", category.displayName()));
        List<Component> loreLines = new ArrayList<>();
        if (!category.displayLore().isEmpty()) {
            for (String customLine : category.displayLore()) {
                loreLines.add(miniMessage.deserialize(customLine));
            }
        } else {
            loreLines.add(text(viewer, KitsMessageKey.KIT_MENU_CATEGORY_LORE, Map.of()));
        }

        return ItemBuilder.of(categoryMaterial(category))
                .name(Tiles.blankName())
                .lore(Tiles.titled(name, loreLines))
                .build();
    }

    ItemStack icon(PlayerRef viewer, KitDefinition base) {
        KitDefinition kit = access.resolveVariant(viewer, base);
        return ItemBuilder.of(resolveMaterial(viewer, kit))
                .name(Tiles.blankName())
                .lore(Tiles.titled(resolveName(viewer, kit), resolveLore(viewer, kit)))
                .build();
    }

    /**
     * The engine-rendered kit browse menu draws a kit's tile from these three source strings rather than from a
     * pre-built {@link ItemStack}: the icon's material name, its name as a MiniMessage source, and its full lore
     * as the rendered lines joined by {@code \n} for the engine's multi-line placeholder to expand back. They are
     * the exact name/material/lore the {@link #icon} path builds, the same resolveMaterial/resolveName/resolveLore
     * passes feed both, so a tile drawn through the engine matches the tile the old view drew icon for icon. The
     * variant is resolved once here so the browse menu hands the same already-resolved kit to a later claim.
     */
    String kitMaterialName(PlayerRef viewer, KitDefinition variant) {
        return resolveMaterial(viewer, variant).name();
    }

    String kitNameSource(PlayerRef viewer, KitDefinition variant) {
        return nameSource(viewer, variant);
    }

    /** The kit tile's full lore as its catalog/override source lines joined by {@code \n}, for the engine to split. */
    String kitLoreSource(PlayerRef viewer, KitDefinition variant) {
        return String.join("\n", loreSource(viewer, variant));
    }

    /** The viewer-relative variant a tile (and a later claim) acts on, resolved once so both agree. */
    KitDefinition variantOf(PlayerRef viewer, KitDefinition base) {
        return access.resolveVariant(viewer, base);
    }

    String categoryMaterialName(KitCategory category) {
        return categoryMaterial(category).name();
    }

    String categoryNameSource(PlayerRef viewer, KitCategory category) {
        return messages.resolve(
                viewer, KitsMessageKey.KIT_MENU_CATEGORY_NAME, Map.of("category", category.displayName()));
    }

    /** The category tile's lore: its own display lore when set, else the catalog drill-in hint, joined by {@code \n}. */
    String categoryLoreSource(PlayerRef viewer, KitCategory category) {
        if (!category.displayLore().isEmpty()) {
            return String.join("\n", category.displayLore());
        }
        return messages.resolve(viewer, KitsMessageKey.KIT_MENU_CATEGORY_LORE, Map.of());
    }

    /** The category icon's material, factored out so both the ItemStack path and the browse strings agree. */
    private Material categoryMaterial(KitCategory category) {
        if (category.displayMaterial().isPresent()) {
            try {
                Material parsed =
                        Material.matchMaterial(category.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
                if (parsed != null && !parsed.isAir()) {
                    return parsed;
                }
            } catch (IllegalArgumentException absent) {
                // Keep default
            }
        }
        return Material.BOOK;
    }

    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds <= 0) {
            return "0s";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private java.time.Duration remainingCooldown(PlayerRef viewer, KitDefinition kit) {
        var res = access.remaining(viewer, kit);
        if (res.isErr()) {
            return res.errorOrThrow();
        }
        return java.time.Duration.ZERO;
    }

    private String processPlaceholders(PlayerRef viewer, KitDefinition kit, String text) {
        String processed = text;
        processed = processed.replace("%cost%", kit.cost().amount().toPlainString());
        if (processed.contains("%cooldown%")) {
            processed = processed.replace("%cooldown%", formatDuration(remainingCooldown(viewer, kit)));
        }
        if (PlaceholderApiSupport.isPresent()) {
            processed = PlaceholderApiSupport.messageBridge(viewer.uuid()).apply(processed);
        }
        return processed;
    }

    /** The player-relative display state that selects which name/material/lore override a kit icon shows. */
    private enum DisplayState {
        UNAVAILABLE,
        NO_PERMISSION,
        CLAIMED,
        ON_COOLDOWN,
        REQUIREMENTS,
        OUT_OF_STOCK,
        UNAFFORDABLE,
        NORMAL
    }

    private DisplayState stateOf(PlayerRef viewer, KitDefinition kit) {
        // The icon mirrors the claim gate order: the schedule window is checked first (in ClaimKit, before
        // anything else), then the per-kit gates, then stock (reserved before the cost), then affordability.
        if (!kit.isAvailableAt(LocalDateTime.now(clock))) {
            return DisplayState.UNAVAILABLE;
        }
        if (!access.hasPermission(viewer, kit)) {
            return DisplayState.NO_PERMISSION;
        }
        if (access.hasClaimedOneTime(viewer, kit)) {
            return DisplayState.CLAIMED;
        }
        if (access.isOnCooldown(viewer, kit)) {
            return DisplayState.ON_COOLDOWN;
        }
        if (!access.meetsRequirements(viewer, kit)) {
            return DisplayState.REQUIREMENTS;
        }
        if (access.isOutOfStock(kit)) {
            return DisplayState.OUT_OF_STOCK;
        }
        if (!access.canAfford(viewer, kit)) {
            return DisplayState.UNAFFORDABLE;
        }
        return DisplayState.NORMAL;
    }

    private Component resolveName(PlayerRef viewer, KitDefinition kit) {
        return StyledText.render(nameSource(viewer, kit));
    }

    /**
     * The kit icon's name as the MiniMessage source string the engine renders, factored out of {@link #resolveName}
     * so the engine-rendered browse tile and the old ItemStack path resolve the identical text. An operator's
     * per-state override is free text with its cost/cooldown/PAPI tokens substituted; otherwise the per-state
     * catalog name is resolved in the viewer's locale, again with those tokens substituted.
     */
    private String nameSource(PlayerRef viewer, KitDefinition kit) {
        DisplayState state = stateOf(viewer, kit);
        Optional<String> nameOpt =
                switch (state) {
                    case NO_PERMISSION -> kit.noPermission().name();
                    case CLAIMED -> kit.claimed().name();
                    case ON_COOLDOWN -> kit.cooldownDisplay().name();
                    case REQUIREMENTS -> kit.requirementsDisplay().name();
                    case UNAFFORDABLE -> kit.unaffordable().name();
                    case NORMAL -> kit.display().name();
                    // The two locked states force their own name so the icon always reads as closed/sold out.
                    case UNAVAILABLE, OUT_OF_STOCK -> Optional.empty();
                };
        if (nameOpt.isPresent()) {
            return processPlaceholders(viewer, kit, nameOpt.get());
        }
        KitsMessageKey defaultKey =
                switch (state) {
                    case UNAVAILABLE -> KitsMessageKey.KIT_MENU_UNAVAILABLE_NAME;
                    case OUT_OF_STOCK -> KitsMessageKey.KIT_MENU_OUT_OF_STOCK_NAME;
                    default -> KitsMessageKey.KIT_MENU_ENTRY_NAME;
                };
        String rawName =
                messages.resolve(viewer, defaultKey, Map.of("kit", kit.id().value()));
        return processPlaceholders(viewer, kit, rawName);
    }

    private Material resolveMaterial(PlayerRef viewer, KitDefinition kit) {
        DisplayState state = stateOf(viewer, kit);
        // The locked states ignore any per-kit material override so they always show the closed/sold-out icon.
        switch (state) {
            case UNAVAILABLE -> {
                return Material.CLOCK;
            }
            case OUT_OF_STOCK -> {
                return Material.BARRIER;
            }
            default -> {}
        }
        Optional<String> matOpt =
                switch (state) {
                    case NO_PERMISSION -> kit.noPermission().material();
                    case CLAIMED -> kit.claimed().material();
                    case ON_COOLDOWN -> kit.cooldownDisplay().material();
                    case REQUIREMENTS -> kit.requirementsDisplay().material();
                    case UNAFFORDABLE -> kit.unaffordable().material();
                    case NORMAL -> kit.display().material();
                    case UNAVAILABLE, OUT_OF_STOCK -> Optional.empty(); // handled above
                };

        if (matOpt.isPresent()) {
            try {
                Material mat = Material.matchMaterial(matOpt.get().toUpperCase(java.util.Locale.ROOT));
                if (mat != null && !mat.isAir()) {
                    return mat;
                }
            } catch (IllegalArgumentException absent) {
                // fallback
            }
        }

        // Fallback to default material resolution
        if (kit.items().isEmpty()) {
            return layout.fallbackIcon();
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? layout.fallbackIcon() : type;
    }

    private List<Component> resolveLore(PlayerRef viewer, KitDefinition kit) {
        List<Component> lines = new ArrayList<>();
        for (String line : loreSource(viewer, kit)) {
            lines.add(StyledText.render(line));
        }
        return lines;
    }

    /**
     * The kit icon's full lore as the catalog/override source lines, factored out of {@link #resolveLore} so the
     * engine-rendered browse tile (which joins these with {@code \n} and re-splits per line through its multi-line
     * placeholder) and the old ItemStack path resolve the identical lore. The order is the old view's: the
     * per-state or display lore, then a status line per requirement, then the conditional cooldown/one-time/cost
     * lines and the claim hint, and finally the preview hint, unless the kit is locked, which shows its own lore.
     */
    private List<String> loreSource(PlayerRef viewer, KitDefinition kit) {
        DisplayState state = stateOf(viewer, kit);
        if (state == DisplayState.UNAVAILABLE || state == DisplayState.OUT_OF_STOCK) {
            return lockedLoreSource(viewer, kit, state);
        }
        List<String> stateLore =
                switch (state) {
                    case NO_PERMISSION -> kit.noPermission().lore();
                    case CLAIMED -> kit.claimed().lore();
                    case ON_COOLDOWN -> kit.cooldownDisplay().lore();
                    case REQUIREMENTS -> kit.requirementsDisplay().lore();
                    case UNAFFORDABLE -> kit.unaffordable().lore();
                    case NORMAL -> List.of();
                    case UNAVAILABLE, OUT_OF_STOCK -> List.of(); // handled above
                };
        boolean hasOverride = !stateLore.isEmpty();
        List<String> rawLore = hasOverride ? stateLore : kit.display().lore();

        List<String> lines = new ArrayList<>();
        for (String line : rawLore) {
            lines.add(processPlaceholders(viewer, kit, line));
        }

        // One ✔/✘ status line per requirement, evaluated for the viewer. The PlaceholderAPI resolution behind
        // access.meetsRequirement runs on the icon-build thread (the viewer's entity thread), never off it.
        appendRequirementStatus(viewer, kit, lines);

        // Only append default status lore lines if we did not use a state override lore
        if (!hasOverride) {
            lines.add(resolve(
                    viewer,
                    KitsMessageKey.KIT_MENU_LORE_COOLDOWN,
                    Map.of("seconds", Long.toString(kit.cooldownSeconds()))));
            if (kit.isOneTime()) {
                lines.add(resolve(viewer, KitsMessageKey.KIT_MENU_LORE_ONETIME, Map.of()));
            }
            if (kit.hasCost()) {
                lines.add(resolve(
                        viewer,
                        KitsMessageKey.KIT_MENU_LORE_COST,
                        Map.of("amount", kit.cost().amount().toPlainString())));
            }
            lines.add(resolve(
                    viewer,
                    KitsMessageKey.KIT_MENU_LORE_CLAIMABLE,
                    Map.of("kit", kit.id().value())));
        }
        if (kit.preview()) {
            lines.add(resolve(viewer, KitsMessageKey.KIT_MENU_PREVIEW_HINT, Map.of()));
        }
        return lines;
    }

    /**
     * The lore source for a kit the viewer cannot claim because its rotation window is closed or its global stock
     * is spent: the kit's own display lore for context, then the single locked-reason line, then the preview hint
     * when the kit allows a preview. No cooldown/cost/claimable lines: none of them apply to a locked kit.
     */
    private List<String> lockedLoreSource(PlayerRef viewer, KitDefinition kit, DisplayState state) {
        List<String> lines = new ArrayList<>();
        for (String line : kit.display().lore()) {
            lines.add(processPlaceholders(viewer, kit, line));
        }
        KitsMessageKey reason = state == DisplayState.UNAVAILABLE
                ? KitsMessageKey.KIT_MENU_LORE_UNAVAILABLE
                : KitsMessageKey.KIT_MENU_LORE_OUT_OF_STOCK;
        lines.add(resolve(viewer, reason, Map.of("kit", kit.id().value())));
        if (kit.preview()) {
            lines.add(resolve(viewer, KitsMessageKey.KIT_MENU_PREVIEW_HINT, Map.of()));
        }
        return lines;
    }

    /**
     * Append one status line per claim requirement, each carrying a ✔ (met) or ✘ (unmet) symbol and the raw
     * condition text. The per-requirement verdict comes from {@link KitAccess#meetsRequirement}, whose
     * PlaceholderAPI lookup runs on the same (viewer entity / region) thread the icon is built on, so this never
     * touches PlaceholderAPI off-thread. With no evaluator wired every condition reads as unmet (fail-closed).
     */
    private void appendRequirementStatus(PlayerRef viewer, KitDefinition kit, List<String> lines) {
        if (!kit.hasRequirements()) {
            return;
        }
        for (com.uxplima.uxmessentials.kits.domain.KitRequirement requirement : kit.requirements()) {
            boolean met = access.meetsRequirement(viewer, requirement);
            KitsMessageKey key = met ? KitsMessageKey.KIT_REQUIREMENT_MET : KitsMessageKey.KIT_REQUIREMENT_UNMET;
            lines.add(resolve(viewer, key, Map.of("condition", requirement.asText())));
        }
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(resolve(viewer, key, placeholders));
    }

    private String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }
}
