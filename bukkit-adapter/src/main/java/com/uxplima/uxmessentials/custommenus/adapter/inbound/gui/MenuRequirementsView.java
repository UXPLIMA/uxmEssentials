package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuSchema;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The view-requirement builder opened from the item editor's "View requirements" row. It is an engine
 * {@link EntityEditorView} of three rows, the condition list (a {@link MenuRefListEditor.RefList} over the schema's
 * condition ids), the {@code minimum} combinator (a {@link NumberProperty}: 0 is all/AND, 1 is any/OR, N is N-of-M),
 * and the deny-action list (a ref-list over the action ids), so an item can gate visibility on a minimum or a set of
 * conditions and react when the gate fails. Every row reads the item's {@code view} block fresh and writes back through
 * the session's view setters; back returns to the item editor, which the row's caller supplies as {@code onBack}.
 */
@NullMarked
public final class MenuRequirementsView {

    private static final String MODULE = "custommenus";
    private static final String LAYOUT = "menu-requirements-editor";
    private static final List<Integer> PROPERTY_SLOTS = List.of(11, 13, 15);
    private static final int BACK_SLOT = 22;
    private static final long MAX_MINIMUM = 64L;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final MenuRefListEditor refList;
    private final Supplier<MenuSchema> schema;
    private final EntityEditorView<ReqTarget> view;

    public MenuRequirementsView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            MenuRefListEditor refList,
            Supplier<MenuSchema> schema,
            BiConsumer<Player, PlayerRef> onBack) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.refList = Objects.requireNonNull(refList, "refList");
        this.schema = Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(onBack, "onBack");
        EntityEditorLayout layout = guiLayouts.loadEntityEditor(MODULE, LAYOUT, codeDefault());
        this.view = EntityEditorView.<ReqTarget>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(this::title)
                .valueLore(CustomMenusMessageKey.MENU_ITEM_EDITOR_VALUE_LORE)
                .backName(CustomMenusMessageKey.MENU_ACTION_EDITOR_REQ_BACK)
                .properties(this::properties)
                .onBack(onBack)
                .build();
    }

    /** Open the requirement editor for the item under {@code itemId} in {@code session}. */
    void open(Player player, PlayerRef viewer, MenuEditSession session, String itemId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(itemId, "itemId");
        view.open(player, viewer, new ReqTarget(session, itemId));
    }

    /** The item editor's "View requirements" row: value lore is the condition count, a click opens this editor. */
    EditableProperty row(MenuEditSession session, String itemId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(itemId, "itemId");
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_REQUIREMENTS,
                Material.COMPARATOR,
                viewer -> Integer.toString(session.viewConditions(itemId).size()),
                context -> open(context.player(), context.viewer(), session, itemId));
    }

    /** The property drawn at {@code slot}, exposed so a test can resolve it without firing a click. */
    Optional<EditableProperty> propertyAt(int slot, MenuEditSession session, String itemId) {
        return view.propertyAt(slot, new ReqTarget(session, itemId));
    }

    private Component title(PlayerRef viewer, ReqTarget target) {
        return guiText.text(viewer, CustomMenusMessageKey.MENU_ACTION_EDITOR_REQ_TITLE, Map.of("id", target.itemId()));
    }

    private List<EditableProperty> properties(ReqTarget target) {
        return List.of(conditionsRow(target), minimumRow(target), denyRow(target));
    }

    private EditableProperty conditionsRow(ReqTarget target) {
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_CONDITIONS,
                Material.COMPASS,
                viewer -> Integer.toString(
                        target.session().viewConditions(target.itemId()).size()),
                context -> openConditions(context, target));
    }

    private void openConditions(ClickContext context, ReqTarget target) {
        MenuRefListEditor.RefList list = new MenuRefListEditor.RefList(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_CONDITIONS_TITLE,
                Map.of(),
                schema.get().conditionIds(),
                () -> target.session().viewConditions(target.itemId()),
                refs -> target.session().setViewConditions(target.itemId(), refs),
                context.reopen());
        refList.open(context, list);
    }

    private EditableProperty minimumRow(ReqTarget target) {
        return new NumberProperty(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_MINIMUM,
                Material.REPEATER,
                () -> viewMinimum(target),
                1,
                5,
                0,
                MAX_MINIMUM,
                value -> target.session().setViewMinimum(target.itemId(), (int) (long) value),
                scheduler);
    }

    private EditableProperty denyRow(ReqTarget target) {
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_DENY,
                Material.BARRIER,
                viewer -> Integer.toString(deny(target).size()),
                context -> openDeny(context, target));
    }

    private void openDeny(ClickContext context, ReqTarget target) {
        MenuRefListEditor.RefList list = new MenuRefListEditor.RefList(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_DENY_TITLE,
                Map.of(),
                schema.get().actionIds(),
                () -> deny(target),
                refs -> target.session().setViewDeny(target.itemId(), refs),
                context.reopen());
        refList.open(context, list);
    }

    private static long viewMinimum(ReqTarget target) {
        return target.session()
                .item(target.itemId())
                .map(item -> (long) item.view().minimum())
                .orElse(0L);
    }

    private static List<Ref> deny(ReqTarget target) {
        return target.session()
                .item(target.itemId())
                .map(item -> item.view().deny())
                .orElseGet(List::of);
    }

    private static EntityEditorLayout codeDefault() {
        return new EntityEditorLayout(
                3,
                PROPERTY_SLOTS,
                BACK_SLOT,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /** The subject a requirement-editor open carries: the working copy and the id of the item being gated. */
    private record ReqTarget(MenuEditSession session, String itemId) {
        private ReqTarget {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(itemId, "itemId");
        }
    }
}
