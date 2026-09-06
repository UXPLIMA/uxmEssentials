package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the per-category settings panel with the menu engine and opens it. A three-row property panel for one
 * {@link KitCategory}: labeled buttons for its display name, material, lore, sorting slot, and parent category, a
 * delete button, and a back button to the category manager. The display-name / lore / slot buttons capture a value
 * through the shared input seam, the material button copies the item in the operator's main hand, and the parent
 * button opens the already-engine {@link KitCategoryParentSelectorMenu}; each mutation saves the edited category and
 * re-opens this panel with the new subject so the operator sees the result.
 *
 * <p>The edited category is handed in as the menu subject, so the title and every current-value line fill from the
 * {@code cat_set_*} placeholders without the renderer touching a port. The panel holds no new domain logic: it
 * replays the old bespoke window's handlers verbatim through the engine. This is the economy target-panel pattern
 * (subject-carried state, the input seam, and re-open after a mutation). Every visible string resolves from the kits
 * catalog.
 */
@NullMarked
public final class KitCategorySettingsView {

    /** The engine spec id this panel registers and opens under. */
    public static final String SPEC_ID = "kit-category-settings";

    private static final String SPEC_RESOURCE = "modules/kits/gui/kit-category-settings.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final GuiText guiText;
    private final Messages messages;
    private final TextInput textInput;
    private final KitCategoryRepository categoryRepository;
    private final BiConsumer<Player, PlayerRef> onBack;

    /** The parent selector the parent button opens; injected after this view to break the settings↔selector cycle. */
    private @Nullable KitCategoryParentSelectorMenu parentSelector;

    public KitCategorySettingsView(
            Menus menus,
            GuiText guiText,
            Messages messages,
            TextInput textInput,
            KitCategoryRepository categoryRepository,
            BiConsumer<Player, PlayerRef> onBack) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /**
     * Wire the parent-category selector the parent button opens. The selector reopens this panel after a pick, so it
     * holds this view and this view holds it; this setter breaks that cycle, mirroring the manager's {@code bind}.
     */
    public void bind(KitCategoryParentSelectorMenu parentSelector) {
        this.parentSelector = Objects.requireNonNull(parentSelector, "parentSelector");
    }

    /** Register the subject placeholders and the action buttons the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("cat_set_id", ctx -> subject(ctx).id());
        bindings.placeholder("cat_set_name", ctx -> subject(ctx).displayName());
        bindings.placeholder(
                "cat_set_material", ctx -> subject(ctx).displayMaterial().orElse("BOOK"));
        bindings.placeholder(
                "cat_set_lore_count",
                ctx -> Integer.toString(subject(ctx).displayLore().size()));
        bindings.placeholder(
                "cat_set_slot", ctx -> Integer.toString(subject(ctx).slot()));
        bindings.placeholder("cat_set_parent", this::parentName);
        bindings.action("kits:cat-set-name", this::promptName);
        bindings.action("kits:cat-set-material", this::setMaterial);
        bindings.action("kits:cat-set-lore", this::promptLore);
        bindings.action("kits:cat-set-slot", this::promptSlot);
        bindings.action("kits:cat-set-parent", this::openParent);
        bindings.action("kits:cat-set-delete", this::delete);
        bindings.action("kits:cat-set-back", ctx -> onBack.accept(ctx.player(), ctx.viewer()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the settings panel for {@code category}; reads no port, the category is the subject the panel renders. */
    public void open(Player player, PlayerRef viewer, KitCategory category) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(category, "category");
        menus.open(viewer, SPEC_ID, category);
    }

    /** Capture a display name through the input seam, then save it and re-open; cancel re-opens unchanged. */
    private void promptName(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        KitCategory category = subject(ctx);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(
                        "kit.category.display-name", KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT),
                name -> applyName(player, viewer, category, name),
                () -> open(player, viewer, category));
    }

    /** Save {@code category} with the new display name and re-open the panel. Package-private for the golden test. */
    void applyName(Player player, PlayerRef viewer, KitCategory category, String name) {
        save(player, viewer, category.withDisplayName(name));
    }

    /** Copy the item in the operator's main hand as the icon material, then save and re-open; empty hand is rejected. */
    private void setMaterial(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        KitCategory category = subject(ctx);
        org.bukkit.Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir()) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_EMPTY_HAND));
            open(player, viewer, category);
            return;
        }
        save(player, viewer, category.withDisplayMaterial(Optional.of(hand.name())));
    }

    /** Capture pipe-separated lore through the input seam, then save it and re-open; cancel re-opens unchanged. */
    private void promptLore(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        KitCategory category = subject(ctx);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(
                        "kit.category.display-lore", KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT),
                input -> applyLore(player, viewer, category, input),
                () -> open(player, viewer, category));
    }

    /** Save {@code category} with the pipe-split lore and re-open the panel. Package-private for the golden test. */
    void applyLore(Player player, PlayerRef viewer, KitCategory category, String input) {
        save(player, viewer, category.withDisplayLore(KitViewText.splitLines(input)));
    }

    /** Capture a sorting-slot index through the input seam, then save it and re-open; a non-number is rejected. */
    private void promptSlot(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        KitCategory category = subject(ctx);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("kit.category.slot", KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT),
                input -> applySlot(player, viewer, category, input),
                () -> open(player, viewer, category));
    }

    /**
     * Parse the typed slot index and, when valid, save it and re-open; a non-number sends the existing rejection and
     * re-opens unchanged. Package-private so the slot branch is unit-tested without driving a live anvil.
     */
    void applySlot(Player player, PlayerRef viewer, KitCategory category, String input) {
        int target;
        try {
            target = Integer.parseInt(input);
        } catch (NumberFormatException notANumber) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
            open(player, viewer, category);
            return;
        }
        save(player, viewer, category.withSlot(target));
    }

    /** Open the engine parent-category selector; choosing a parent saves it and re-opens this panel. */
    private void openParent(MenuActionContext ctx) {
        if (parentSelector != null) {
            parentSelector.open(ctx.player(), ctx.viewer(), subject(ctx));
        }
    }

    /** Delete the category through the repository, then return to the manager: the old delete button's effect. */
    private void delete(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        categoryRepository.delete(subject(ctx).id());
        onBack.accept(player, viewer);
    }

    /** Save the edited category through the repository, then re-open the panel with the new subject. */
    private void save(Player player, PlayerRef viewer, KitCategory category) {
        categoryRepository.save(category);
        open(player, viewer, category);
    }

    /** The current parent id, or the catalog "none" string when the category has no parent. */
    private String parentName(MenuContext ctx) {
        return subject(ctx)
                .parentCategoryId()
                .orElseGet(() -> messages.resolve(ctx.viewer(), KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of()));
    }

    private KitCategory subject(MenuContext ctx) {
        return ctx.subject(KitCategory.class);
    }

    private KitCategory subject(MenuActionContext ctx) {
        return ctx.subject(KitCategory.class);
    }
}
