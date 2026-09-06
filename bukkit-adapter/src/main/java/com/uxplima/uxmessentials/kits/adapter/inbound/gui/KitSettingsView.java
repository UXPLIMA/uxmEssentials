package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the per-kit settings panel with the menu engine and opens it. A three-row property panel for one
 * {@link KitDefinition} reached from the kit manager (a left click on a kit icon) or the {@code /kit create} flow:
 * an edit-items button into the bespoke item grid, toggle buttons for the permission requirement, one-time claim,
 * first-join grant and auto-equip flags, input-seam buttons for the cooldown, cost, display name, display lore and
 * commands, a hand-copy display-material button, a category button into the engine category selector, a delete
 * button, and a back button to the manager. The cooldown / cost / display-name / display-lore / commands buttons
 * capture a value through the shared input seam; the display-material button copies the item in the operator's main
 * hand; the category button opens the already-engine {@link KitCategorySelectorMenu}; each mutation saves the edited
 * kit and re-opens this panel with the new subject so the operator sees the result.
 *
 * <p>The edited kit is handed in as the menu subject, so the title and every current-value line fill from the
 * {@code kit_set_*} placeholders without the renderer touching a port. The panel holds no new domain logic: it
 * replays the old bespoke window's handlers verbatim through the engine. This is the kit category settings-panel
 * pattern (subject-carried state, the input seam, and re-open after a mutation). Every visible string resolves from
 * the kits catalog.
 */
@NullMarked
public final class KitSettingsView {

    /** The engine spec id this panel registers and opens under. */
    public static final String SPEC_ID = "kit-settings";

    private static final String SPEC_RESOURCE = "modules/kits/gui/kit-settings.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final GuiText guiText;
    private final Messages messages;
    private final TextInput textInput;
    private final KitEditor kitEditor;
    private final DelKit delKit;
    private final KitEditorView editorView;
    private final BiConsumer<Player, PlayerRef> onBack;

    /** The category selector the category button opens; injected after this view to break the settings↔selector cycle. */
    private @Nullable KitCategorySelectorMenu categorySelector;

    public KitSettingsView(
            Menus menus,
            GuiText guiText,
            Messages messages,
            TextInput textInput,
            KitEditor kitEditor,
            DelKit delKit,
            KitEditorView editorView,
            BiConsumer<Player, PlayerRef> onBack) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.kitEditor = Objects.requireNonNull(kitEditor, "kitEditor");
        this.delKit = Objects.requireNonNull(delKit, "delKit");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /**
     * Wire the kit category selector the category button opens. The selector reopens this panel after a pick, so it
     * holds this view and this view holds it; this setter breaks that cycle, mirroring the manager's {@code bind}.
     */
    public void bind(KitCategorySelectorMenu categorySelector) {
        this.categorySelector = Objects.requireNonNull(categorySelector, "categorySelector");
    }

    /** Register the subject placeholders and the action buttons the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        registerPlaceholders(bindings);
        registerActions(bindings);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    private void registerPlaceholders(MenuBindings bindings) {
        bindings.placeholder("kit_set_id", ctx -> subject(ctx).id().value());
        bindings.placeholder("kit_set_perm_material", ctx -> subject(ctx).permission() ? "PAPER" : "BARRIER");
        bindings.placeholder(
                "kit_set_permission", ctx -> required(ctx, subject(ctx).permission()));
        bindings.placeholder("kit_set_onetime", ctx -> yesNo(ctx, subject(ctx).oneTime()));
        bindings.placeholder(
                "kit_set_cooldown", ctx -> Long.toString(subject(ctx).cooldownSeconds()));
        bindings.placeholder("kit_set_cost", this::cost);
        bindings.placeholder("kit_set_display_name", this::displayName);
        bindings.placeholder(
                "kit_set_display_material",
                ctx -> displayMaterial(subject(ctx)).name().toLowerCase(Locale.ROOT));
        bindings.placeholder(
                "kit_set_display_material_icon",
                ctx -> displayMaterial(subject(ctx)).name());
        bindings.placeholder(
                "kit_set_display_lore_count",
                ctx -> Integer.toString(subject(ctx).display().lore().size()));
        bindings.placeholder(
                "kit_set_commands_count",
                ctx -> Integer.toString(subject(ctx).commands().size()));
        bindings.placeholder("kit_set_firstjoin", ctx -> yesNo(ctx, subject(ctx).firstJoin()));
        bindings.placeholder("kit_set_autoequip", ctx -> yesNo(ctx, subject(ctx).autoEquip()));
        bindings.placeholder("kit_set_category", this::category);
    }

    private void registerActions(MenuBindings bindings) {
        bindings.action("kits:settings-edit-items", this::editItems);
        bindings.action(
                "kits:settings-permission",
                ctx -> save(ctx, subject(ctx).withPermission(!subject(ctx).permission())));
        bindings.action(
                "kits:settings-onetime",
                ctx -> save(ctx, subject(ctx).withOneTime(!subject(ctx).oneTime())));
        bindings.action("kits:settings-cooldown", this::promptCooldown);
        bindings.action("kits:settings-cost", this::promptCost);
        bindings.action("kits:settings-display-name", this::promptDisplayName);
        bindings.action("kits:settings-display-material", this::setDisplayMaterial);
        bindings.action("kits:settings-display-lore", this::promptDisplayLore);
        bindings.action("kits:settings-commands", this::promptCommands);
        bindings.action(
                "kits:settings-firstjoin",
                ctx -> save(ctx, subject(ctx).withFirstJoin(!subject(ctx).firstJoin())));
        bindings.action(
                "kits:settings-autoequip",
                ctx -> save(ctx, subject(ctx).withAutoEquip(!subject(ctx).autoEquip())));
        bindings.action("kits:settings-delete", this::delete);
        bindings.action("kits:settings-category", this::openCategory);
        bindings.action("kits:settings-back", ctx -> onBack.accept(ctx.player(), ctx.viewer()));
    }

    /** Open the settings panel for {@code kit}; reads no port, the kit is the subject the panel renders. */
    public void open(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kit, "kit");
        menus.open(viewer, SPEC_ID, kit);
    }

    /** Open the bespoke item-editor grid for this kit's stacks: the old edit-items button's effect. */
    private void editItems(MenuActionContext ctx) {
        Player player = ctx.player();
        player.closeInventory();
        editorView.open(player, ctx.viewer(), subject(ctx));
    }

    /** Capture a cooldown in seconds through the input seam; a non-number or negative value is rejected. */
    private void promptCooldown(MenuActionContext ctx) {
        KitDefinition kit = subject(ctx);
        prompt(
                ctx,
                "kit.cooldown",
                KitsMessageKey.KIT_EDITOR_PROMPT_COOLDOWN,
                kit,
                input -> applyCooldown(ctx.player(), ctx.viewer(), kit, input));
    }

    /** Parse the typed cooldown and, when valid, save it and re-open; otherwise send the matching rejection. */
    void applyCooldown(Player player, PlayerRef viewer, KitDefinition kit, String input) {
        long seconds;
        try {
            seconds = Long.parseLong(input);
        } catch (NumberFormatException notANumber) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
            open(player, viewer, kit);
            return;
        }
        if (seconds < 0) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_NEGATIVE_COOLDOWN));
            open(player, viewer, kit);
            return;
        }
        save(player, viewer, kit.withCooldown(Duration.ofSeconds(seconds)));
    }

    /** Capture a cost (or {@code free}/{@code 0}) through the input seam; a non-number or negative value is rejected. */
    private void promptCost(MenuActionContext ctx) {
        KitDefinition kit = subject(ctx);
        prompt(
                ctx,
                "kit.cost",
                KitsMessageKey.KIT_EDITOR_PROMPT_COST,
                kit,
                input -> applyCost(ctx.player(), ctx.viewer(), kit, input));
    }

    /** Parse the typed cost and, when valid, save it and re-open; {@code free}/{@code 0} clears the cost. */
    void applyCost(Player player, PlayerRef viewer, KitDefinition kit, String input) {
        if (input.equalsIgnoreCase("free") || input.equalsIgnoreCase("0")) {
            save(player, viewer, kit.withCost(KitCost.free()));
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(input);
        } catch (NumberFormatException notANumber) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
            open(player, viewer, kit);
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_NEGATIVE_COST));
            open(player, viewer, kit);
            return;
        }
        save(player, viewer, kit.withCost(KitCost.of(amount)));
    }

    /** Capture a display name through the input seam; {@code none} clears it. */
    private void promptDisplayName(MenuActionContext ctx) {
        KitDefinition kit = subject(ctx);
        prompt(
                ctx,
                "kit.display-name",
                KitsMessageKey.KIT_EDITOR_PROMPT_DISPLAY_NAME,
                kit,
                input -> applyDisplayName(ctx.player(), ctx.viewer(), kit, input));
    }

    /** Save {@code kit} with the new display name and re-open; {@code none} clears it. Package-private for the test. */
    void applyDisplayName(Player player, PlayerRef viewer, KitDefinition kit, String input) {
        Optional<String> name = input.equalsIgnoreCase("none") ? Optional.empty() : Optional.of(input);
        save(player, viewer, kit.withDisplayName(name));
    }

    /** Copy the item in the operator's main hand as the display material, then save and re-open; empty hand rejected. */
    private void setDisplayMaterial(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        KitDefinition kit = subject(ctx);
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir()) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_EMPTY_HAND));
            open(player, viewer, kit);
            return;
        }
        save(player, viewer, kit.withDisplayMaterial(Optional.of(hand.name())));
    }

    /** Capture pipe-separated display lore through the input seam; {@code none} clears it. */
    private void promptDisplayLore(MenuActionContext ctx) {
        KitDefinition kit = subject(ctx);
        prompt(
                ctx,
                "kit.display-lore",
                KitsMessageKey.KIT_EDITOR_PROMPT_DISPLAY_LORE,
                kit,
                input -> applyDisplayLore(ctx.player(), ctx.viewer(), kit, input));
    }

    /** Save {@code kit} with the pipe-split display lore and re-open. Package-private for the golden test. */
    void applyDisplayLore(Player player, PlayerRef viewer, KitDefinition kit, String input) {
        save(player, viewer, kit.withDisplayLore(KitViewText.splitLines(input)));
    }

    /** Capture pipe-separated commands through the input seam; {@code none} clears them. */
    private void promptCommands(MenuActionContext ctx) {
        KitDefinition kit = subject(ctx);
        prompt(
                ctx,
                "kit.commands",
                KitsMessageKey.KIT_EDITOR_PROMPT_COMMANDS,
                kit,
                input -> applyCommands(ctx.player(), ctx.viewer(), kit, input));
    }

    /** Save {@code kit} with the pipe-split commands and re-open. Package-private for the golden test. */
    void applyCommands(Player player, PlayerRef viewer, KitDefinition kit, String input) {
        save(player, viewer, kit.withCommands(KitViewText.splitLines(input)));
    }

    /** Delete the kit through the {@link DelKit} use case, then return to the manager: the old delete button's effect. */
    private void delete(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        player.closeInventory();
        delKit.delete(viewer, subject(ctx).id());
        onBack.accept(player, viewer);
    }

    /** Open the engine kit category selector; choosing a category saves it and re-opens this panel. */
    private void openCategory(MenuActionContext ctx) {
        if (categorySelector != null) {
            categorySelector.open(ctx.player(), ctx.viewer(), subject(ctx));
        }
    }

    /** Drive the shared input seam for {@code key}, applying {@code action} on submit and re-opening on cancel. */
    private void prompt(
            MenuActionContext ctx,
            String inputKey,
            MessageKey promptKey,
            KitDefinition kit,
            java.util.function.Consumer<String> action) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        player.closeInventory();
        textInput.prompt(player, viewer, InputRequest.of(inputKey, promptKey), action, () -> open(player, viewer, kit));
    }

    /** Save the edited kit through the editor, then re-open the panel with the new subject. */
    private void save(MenuActionContext ctx, KitDefinition kit) {
        save(ctx.player(), ctx.viewer(), kit);
    }

    private void save(Player player, PlayerRef viewer, KitDefinition kit) {
        kitEditor.save(viewer, kit);
        open(player, viewer, kit);
    }

    /** The cost amount as plain text, or the catalog "free" string when the kit has no cost. */
    private String cost(MenuContext ctx) {
        KitDefinition kit = subject(ctx);
        return kit.hasCost()
                ? kit.cost().amount().toPlainString()
                : messages.resolve(ctx.viewer(), KitsMessageKey.KIT_EDITOR_VALUE_FREE, Map.of());
    }

    /** The display name, or the catalog "none" string when the kit sets none. */
    private String displayName(MenuContext ctx) {
        return subject(ctx)
                .display()
                .name()
                .orElseGet(() -> messages.resolve(ctx.viewer(), KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of()));
    }

    /** The category id, or the catalog "none" string when the kit is in no category. */
    private String category(MenuContext ctx) {
        return subject(ctx)
                .categoryId()
                .orElseGet(() -> messages.resolve(ctx.viewer(), KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of()));
    }

    /**
     * The display item the kit actually shows in the browse menu. The configured display material if it parses,
     * otherwise the first kit item's type, otherwise {@link Material#CHEST}. The display-material button renders with
     * this so the editor always reflects the live icon rather than a fixed placeholder. Mirrors the old view exactly.
     */
    private Material displayMaterial(KitDefinition kit) {
        if (kit.display().material().isPresent()) {
            Material parsed =
                    Material.matchMaterial(kit.display().material().get().toUpperCase(Locale.ROOT));
            if (parsed != null && !parsed.isAir()) {
                return parsed;
            }
        }
        if (kit.items().isEmpty()) {
            return Material.CHEST;
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? Material.CHEST : type;
    }

    private String required(MenuContext ctx, boolean value) {
        return messages.resolve(
                ctx.viewer(),
                value ? KitsMessageKey.KIT_EDITOR_VALUE_REQUIRED : KitsMessageKey.KIT_EDITOR_VALUE_NONE,
                Map.of());
    }

    private String yesNo(MenuContext ctx, boolean value) {
        return messages.resolve(
                ctx.viewer(),
                value ? KitsMessageKey.KIT_EDITOR_VALUE_YES : KitsMessageKey.KIT_EDITOR_VALUE_NO,
                Map.of());
    }

    private KitDefinition subject(MenuContext ctx) {
        return ctx.subject(KitDefinition.class);
    }

    private KitDefinition subject(MenuActionContext ctx) {
        return ctx.subject(KitDefinition.class);
    }
}
