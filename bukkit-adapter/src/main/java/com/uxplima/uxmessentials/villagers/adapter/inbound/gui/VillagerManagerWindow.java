package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The trade-manager window as the operator sees it: {@code modules/villagers/gui/trade-manager.conf} plus the
 * bindings behind it. The file owns the window's height, its backdrop, the arrows, the per-row remove buttons and
 * the control row; this class owns the wording resolved from the message catalog, which toggle state is drawn, what
 * each button does, and the block of slots the file hands over as a {@code content {}} region for the buy and sell
 * stacks themselves.
 *
 * <p>The region's slots come in threes (buy-A, buy-B, sell) one triple per editable trade, and the geometry is
 * read back out of the parsed spec so nothing here assumes where they sit. A file whose region is not a whole
 * number of triples could not describe a trade set at all, so that is refused at wiring time.
 */
@NullMarked
public final class VillagerManagerWindow {

    static final String SPEC_ID = "villagers-trade-manager";
    static final String SPEC_RESOURCE = "modules/villagers/gui/trade-manager.conf";
    static final String REGION = "villagers:trades";

    /** Each editable trade takes three region slots: the two buy stacks then the sell stack. */
    static final int SLOTS_PER_TRADE = 3;

    /** The height the bundled spec is written for; a file that omits {@code rows} falls back to it. */
    private static final int ROWS = 6;

    private final Messages messages;
    private final Menus menus;
    private final MenuSpec spec;
    private final List<Integer> slots;

    public VillagerManagerWindow(Messages messages, Menus menus, Path dataFolder, Logger log) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.spec = MenuSpecs.loadOrBundled(SPEC_RESOURCE, Objects.requireNonNull(dataFolder, "dataFolder"), ROWS, log);
        this.slots = ContentRegions.slots(spec, REGION, SPEC_RESOURCE);
        if (slots.isEmpty() || slots.size() % SLOTS_PER_TRADE != 0) {
            throw new IllegalStateException(SPEC_RESOURCE + ": the '" + REGION + "' region must declare a whole "
                    + "number of trades at " + SLOTS_PER_TRADE + " slots each (buy, buy, sell), but declares "
                    + slots.size() + " slots");
        }
    }

    /** How many trades this window can edit; a villager with more keeps the rest untouched behind it. */
    int editableTrades() {
        return slots.size() / SLOTS_PER_TRADE;
    }

    /** The window slot holding one part of a trade: {@code part} is 0 for buy-A, 1 for buy-B, 2 for the sell stack. */
    int slotOf(int trade, int part) {
        return slots.get(trade * SLOTS_PER_TRADE + part);
    }

    /** The action id a spec writes to clear trade {@code number}, counting from 1 in region order. */
    static String removeActionId(int number) {
        return "villagers:remove-" + number;
    }

    /** Give the spec its behaviour and register it; called once at wiring time, with the view the buttons drive. */
    public void register(MenuBindings bindings, VillagerManagerView view) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(view, "view");
        bindings.placeholder(
                "villagers_manager_title",
                ctx -> messages.resolve(
                        ctx.viewer(),
                        VillagersMessageKey.VILLAGERS_MANAGER_TITLE,
                        Map.of("name", view.villagerLabel(ctx.subject(VillagerManagerHolder.class)))));
        bindings.condition(
                "villagers:trading-enabled",
                (ctx, args) -> !view.tradingDisabled(ctx.subject(VillagerManagerHolder.class)));
        bindings.condition(
                "villagers:trading-disabled",
                (ctx, args) -> view.tradingDisabled(ctx.subject(VillagerManagerHolder.class)));
        bindings.action(
                "villagers:toggle-trading", action -> view.toggleTrading(action.subject(VillagerManagerHolder.class)));
        // One remove action per editable trade, numbered from 1 in region order. A family of flat ids rather than a
        // single valued one because an action token is resolved by its whole id or by its head, never by a tail.
        for (int trade = 0; trade < editableTrades(); trade++) {
            int index = trade;
            bindings.action(
                    removeActionId(trade + 1),
                    action -> view.removeTrade(action.subject(VillagerManagerHolder.class), index));
        }
        bindings.content(REGION, new VillagerTradeContent(view, editableTrades()));
        menus.registerSpec(SPEC_ID, spec);
    }

    /** Show this window to {@code holder}'s editor, carrying the holder as the menu's subject. */
    void open(VillagerManagerHolder holder) {
        menus.open(holder.editor(), SPEC_ID, holder);
    }

    /** Redraw {@code editor}'s window in place, so a toggled button shows its new state. */
    void redraw(PlayerRef editor) {
        menus.redraw(editor, SPEC_ID);
    }

    /** The live window {@code editor} has open, when it is still this one. Read on the editor's own thread. */
    Optional<Inventory> live(PlayerRef editor) {
        return menus.openWindow(editor, SPEC_ID);
    }

    /** Read the staked buy/sell stacks out of a live window, as a positional array of copies. */
    @Nullable ItemStack[] read(Inventory inv) {
        return ContentRegions.read(inv, slots);
    }

    /** Empty the three slots of one trade row, dropping that trade when the window closes. */
    void clearTrade(Inventory inv, int trade) {
        if (trade < 0 || trade >= editableTrades()) {
            return;
        }
        int first = trade * SLOTS_PER_TRADE;
        ContentRegions.clear(inv, slots.subList(first, first + SLOTS_PER_TRADE));
    }
}
