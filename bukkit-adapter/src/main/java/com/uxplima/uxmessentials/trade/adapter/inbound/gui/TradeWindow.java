package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The same-server trade window as the operator sees it: the spec in {@code modules/trade/gui/trade.conf} plus the
 * bindings that give it behaviour. The file owns the window's height, its backdrop and where the confirm, money and
 * experience buttons sit; this class owns everything the file cannot. The wording (resolved from the message
 * catalog through placeholders), which button is shown in which state, what a click does, and the two blocks of item
 * slots the file hands over as {@code content {}} regions.
 *
 * <p>The geometry is read back out of the parsed spec, so the code never assumes where the slots are: the offer
 * region's declared slots are this side's, the mirror region's are the other side's, and slot {@code k} of one lines
 * up with slot {@code k} of the other. A file whose two regions differ in size would let a player stake an item the
 * counterpart could never see, so that is refused at wiring time rather than discovered mid-trade.
 */
@NullMarked
public final class TradeWindow {

    static final String SPEC_ID = "trade";
    static final String SPEC_RESOURCE = "modules/trade/gui/trade.conf";
    static final String OFFER_REGION = "trade:offer";
    static final String MIRROR_REGION = "trade:mirror";

    /** The height the bundled spec is written for; a file that omits {@code rows} falls back to it. */
    private static final int ROWS = 6;

    private final Messages messages;
    private final Menus menus;
    private final MenuSpec spec;
    private final List<Integer> offerSlots;
    private final List<Integer> mirrorSlots;

    /** The allowed currency ids the single money button cycles through; empty when no economy is wired. */
    private final List<String> currencies;

    public TradeWindow(Messages messages, Menus menus, List<String> currencies, Path dataFolder, Logger log) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.currencies = List.copyOf(Objects.requireNonNull(currencies, "currencies"));
        this.spec = MenuSpecs.loadOrBundled(SPEC_RESOURCE, Objects.requireNonNull(dataFolder, "dataFolder"), ROWS, log);
        this.offerSlots = ContentRegions.slots(spec, OFFER_REGION, SPEC_RESOURCE);
        this.mirrorSlots = ContentRegions.slots(spec, MIRROR_REGION, SPEC_RESOURCE);
        if (offerSlots.size() != mirrorSlots.size()) {
            throw new IllegalStateException(SPEC_RESOURCE + ": the '" + OFFER_REGION + "' and '" + MIRROR_REGION
                    + "' regions must declare the same number of slots, so that every staked item has a slot the "
                    + "other player can see it in (found " + offerSlots.size() + " and " + mirrorSlots.size() + ")");
        }
    }

    /** How many item slots each side of this window holds, the size of either region. */
    int perSide() {
        return offerSlots.size();
    }

    /** The window slot the viewer's {@code k}-th offered stack sits in. */
    int offerSlot(int k) {
        return offerSlots.get(k);
    }

    /** The window slot the other side's {@code k}-th offered stack is mirrored into. */
    int mirrorSlot(int k) {
        return mirrorSlots.get(k);
    }

    /** Whether the money button is wired at all, i.e. whether any currency may be staked. */
    boolean moneyEnabled() {
        return !currencies.isEmpty();
    }

    /** How many currencies the money button cycles through; {@code 0} when money is off. */
    int currencyCount() {
        return currencies.size();
    }

    /** The currency id the money button shows at {@code index}, wrapped into range. */
    String currencyAt(int index) {
        return currencies.get(Math.floorMod(index, currencies.size()));
    }

    /**
     * Give the spec its behaviour and register it: the text placeholders, the state conditions that decide which of
     * the paired buttons is drawn, the click actions, and the two content providers that fill and police the item
     * slots. Called once at wiring time, with the view the actions drive.
     */
    void register(MenuBindings bindings, TradeView view, List<Material> blacklist) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(view, "view");
        registerText(bindings, view);
        registerState(bindings, view);
        registerActions(bindings, view);
        bindings.content(OFFER_REGION, new TradeOfferContent(view, blacklist));
        bindings.content(MIRROR_REGION, new TradeMirrorContent(view));
        menus.registerSpec(SPEC_ID, spec);
    }

    private void registerText(MenuBindings bindings, TradeView view) {
        bindings.placeholder(
                "trade_title",
                ctx -> text(
                        ctx,
                        TradeMessageKey.TRADE_WINDOW_TITLE,
                        Map.of("player", holder(ctx).counterpart().name())));
        bindings.placeholder(
                "trade_money_name",
                ctx -> text(
                        ctx, TradeMessageKey.TRADE_WINDOW_MONEY, Map.of("currency", selectedCurrency(holder(ctx)))));
        bindings.placeholder("trade_money_lore", ctx -> ownMoneyLore(ctx, view));
        bindings.placeholder(
                "trade_experience_lore", ctx -> experienceLine(ctx, view.experienceStaked(holder(ctx), true)));
        bindings.placeholder("trade_partner_money_lore", ctx -> partnerMoneyLore(ctx, view));
        bindings.placeholder(
                "trade_partner_experience_lore", ctx -> experienceLine(ctx, view.experienceStaked(holder(ctx), false)));
    }

    private void registerState(MenuBindings bindings, TradeView view) {
        bindings.condition("trade:money-enabled", (ctx, args) -> moneyEnabled());
        bindings.condition("trade:confirmed", (ctx, args) -> view.hasConfirmed(holder(ctx), true));
        bindings.condition("trade:awaiting", (ctx, args) -> !view.hasConfirmed(holder(ctx), true));
        bindings.condition("trade:partner-confirmed", (ctx, args) -> view.hasConfirmed(holder(ctx), false));
        bindings.condition("trade:partner-awaiting", (ctx, args) -> !view.hasConfirmed(holder(ctx), false));
    }

    private void registerActions(MenuBindings bindings, TradeView view) {
        bindings.action("trade:confirm", action -> view.confirm(action.subject(TradeHolder.class)));
        bindings.action("trade:money", action -> view.promptMoney(action.player(), action.subject(TradeHolder.class)));
        bindings.action("trade:money-cycle", action -> view.cycleCurrency(action.subject(TradeHolder.class)));
        bindings.action(
                "trade:experience",
                action -> view.promptExperience(action.player(), action.subject(TradeHolder.class)));
    }

    /** Show this window to {@code holder}'s viewer, carrying the holder as the menu's subject. */
    void open(TradeHolder holder) {
        menus.open(holder.viewer(), SPEC_ID, holder);
    }

    /** Redraw {@code viewer}'s window in place, if they still have it open: how a change on one side reaches both. */
    void redraw(PlayerRef viewer) {
        menus.redraw(viewer, SPEC_ID);
    }

    /** The live window {@code viewer} has open, when it is still this one. Read on the viewer's own thread. */
    Optional<Inventory> live(PlayerRef viewer) {
        return menus.openWindow(viewer, SPEC_ID);
    }

    /** Read the viewer's own offer out of their live window, as a positional array of copies. */
    @Nullable ItemStack[] readOffer(Inventory inv) {
        return ContentRegions.read(inv, offerSlots);
    }

    /** Empty the viewer's offer region: the offered originals leave the window, so nothing is returned twice. */
    void clearOffer(Inventory inv) {
        ContentRegions.clear(inv, offerSlots);
    }

    /** {@code offer} as the region-ordered list the content providers paint from. */
    List<@Nullable ItemStack> painted(@Nullable ItemStack @Nullable [] offer) {
        return ContentRegions.copies(offer, perSide());
    }

    private String ownMoneyLore(MenuContext ctx, TradeView view) {
        TradeHolder holder = holder(ctx);
        String currency = selectedCurrency(holder);
        List<String> lines = new ArrayList<>();
        lines.add(moneyLine(ctx, currency, view.moneyStaked(holder, true).getOrDefault(currency, BigDecimal.ZERO)));
        if (currencies.size() > 1) {
            lines.add(text(ctx, TradeMessageKey.TRADE_WINDOW_MONEY_CYCLE, Map.of()));
        }
        return String.join("\n", lines);
    }

    private String partnerMoneyLore(MenuContext ctx, TradeView view) {
        Map<String, BigDecimal> staked = view.moneyStaked(holder(ctx), false);
        List<String> lines = new ArrayList<>();
        for (String currency : currencies) {
            lines.add(moneyLine(ctx, currency, staked.getOrDefault(currency, BigDecimal.ZERO)));
        }
        return String.join("\n", lines);
    }

    private String moneyLine(MenuContext ctx, String currency, BigDecimal amount) {
        return text(
                ctx,
                TradeMessageKey.TRADE_WINDOW_MONEY_AMOUNT,
                Map.of("currency", currency, "amount", amount.toPlainString()));
    }

    private String experienceLine(MenuContext ctx, long amount) {
        return text(ctx, TradeMessageKey.TRADE_WINDOW_EXPERIENCE_AMOUNT, Map.of("amount", Long.toString(amount)));
    }

    private String selectedCurrency(TradeHolder holder) {
        return moneyEnabled() ? currencyAt(holder.selectedCurrency()) : "";
    }

    private String text(MenuContext ctx, TradeMessageKey key, Map<String, String> placeholders) {
        return messages.resolve(ctx.viewer(), key, placeholders);
    }

    /** The side of the trade the context's window renders; every binding here is about one holder. */
    static TradeHolder holder(MenuContext ctx) {
        return ctx.subject(TradeHolder.class);
    }
}
