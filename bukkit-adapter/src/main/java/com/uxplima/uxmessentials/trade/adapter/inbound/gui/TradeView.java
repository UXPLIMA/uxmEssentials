package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeReceipt;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import com.uxplima.uxmessentials.trade.domain.event.TradeCancelled;
import com.uxplima.uxmessentials.trade.domain.event.TradeCompleted;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Drives the two live views of one same-server trade over the shared {@link TradeExchange}. Each participant sees
 * their own {@link TradeWindow}. An engine menu whose chrome comes from {@code modules/trade/gui/trade.conf} and
 * whose two blocks of item slots are content regions this class fills: their editable offer on one side, the
 * counterpart's offer mirrored read-only on the other. Placing or removing an item re-reads that side's offer into
 * the domain {@link TradeSession} (which clears both confirmations. The anti-scam invariant) and redraws both
 * windows, so the other player sees the change and both confirms reset. When both sides confirm with no pending
 * change the swap runs: each player receives the other's stacks, with any overflow dropped at their feet rather than
 * deleted.
 *
 * <p>Item safety is absolute. An offered stack physically sits in the placing player's window, out of their
 * inventory; every terminal path (a commit, a window close, a disconnect, a world change, or a plugin stop) either
 * swaps or returns those stacks exactly once, gated by the exchange's single-winner settle flag. Every live-player
 * touch hops to that player's region thread through the injected {@link Scheduler}, so the flow is Folia-safe.
 */
@NullMarked
public final class TradeView {

    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final TradeWindow window;
    private final TradeSessions sessions;
    private final TradeMoneyPrompt moneyPrompt;
    private final TradeExperiencePrompt experiencePrompt;

    /** The all-or-nothing mover for staked money and experience; always present because experience is always tradeable. */
    private final TradeSettlement settlement;

    /** The experience seam, read for the amount prompt's affordability check; also settled through {@link #settlement}. */
    private final TradeExperience experience;

    /** The completed-trade audit sink; consulted only when {@link #auditEnabled} is set. */
    private final TradeAudit audit;

    /** Where the two facts a trade ends with are published, whatever the audit knob is set to. */
    private final DomainEventPublisher events;

    /** Whether a completed trade emits an audit line: the module's {@code audit} config knob, resolved once. */
    private final boolean auditEnabled;

    /** The materials refused into the window, resolved once from {@code item-blacklist}. */
    private final List<Material> blacklist;

    /**
     * Viewers whose window closed only to show an amount prompt: their next close is a re-open, not a cancel.
     * Concurrent because the two participants' region threads touch it independently on Folia.
     */
    private final Set<UUID> promptingViewers = ConcurrentHashMap.newKeySet();

    public TradeView(
            Messages messages,
            MessageSink messageSink,
            Scheduler scheduler,
            TradeConfig config,
            TradeSessions sessions,
            TradeWindow window,
            TradeMoneyPrompt moneyPrompt,
            TradeExperiencePrompt experiencePrompt,
            TradeSettlement settlement,
            TradeExperience experience,
            TradeAudit audit,
            DomainEventPublisher events) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(config, "config");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.window = Objects.requireNonNull(window, "window");
        this.moneyPrompt = Objects.requireNonNull(moneyPrompt, "moneyPrompt");
        this.experiencePrompt = Objects.requireNonNull(experiencePrompt, "experiencePrompt");
        this.settlement = Objects.requireNonNull(settlement, "settlement");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.auditEnabled = config.audit();
        this.blacklist = parseBlacklist(config.itemBlacklist());
    }

    /** Register the window's spec and every binding it names; the wiring calls this once, before the first open. */
    public void register(MenuBindings bindings) {
        window.register(bindings, this, blacklist);
    }

    /** The lifecycle listener bound to this view: the wiring registers it. */
    public TradeListener newListener() {
        return new TradeListener(this);
    }

    /** Open a fresh trade window between two distinct, online players, unless either is already trading. */
    public void open(Player a, Player b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.getUniqueId().equals(b.getUniqueId())
                || sessions.isTrading(a.getUniqueId())
                || sessions.isTrading(b.getUniqueId())) {
            return;
        }
        PlayerRef aRef = ref(a);
        PlayerRef bRef = ref(b);
        TradeId id = TradeId.newId();
        TradeExchange exchange = new TradeExchange(
                TradeSession.open(id, aRef, bRef),
                new TradeHolder(id, TradeSide.INITIATOR, aRef, bRef),
                new TradeHolder(id, TradeSide.PARTNER, bRef, aRef));
        sessions.register(exchange);
        for (TradeSide side : TradeSide.values()) {
            window.open(exchange.holder(side));
        }
    }

    // --- what the window's bindings read ---

    /** Whether the viewer may still move items in their offer region: only while the trade is live. */
    boolean acceptsItems(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        return exchange != null && !exchange.session().state().isTerminal();
    }

    /** The stacks this side has staked, in region order: what the offer region is filled with when it opens. */
    List<@Nullable ItemStack> ownOffer(TradeHolder holder) {
        return offerOf(holder, true);
    }

    /** The other side's stakes, in region order: the read-only mirror, repainted on every redraw. */
    List<@Nullable ItemStack> mirroredOffer(TradeHolder holder) {
        return offerOf(holder, false);
    }

    /** Whether {@code holder}'s own side (or its counterpart, when {@code own} is false) has confirmed. */
    boolean hasConfirmed(TradeHolder holder, boolean own) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        return exchange != null && exchange.confirmed(sideOf(holder, own));
    }

    /** The money staked by {@code holder}'s own side (or its counterpart), per currency id. */
    Map<String, BigDecimal> moneyStaked(TradeHolder holder, boolean own) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        return exchange == null ? Map.of() : exchange.money(sideOf(holder, own));
    }

    /** The experience points staked by {@code holder}'s own side (or its counterpart). */
    long experienceStaked(TradeHolder holder, boolean own) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        return exchange == null ? 0L : exchange.experience(sideOf(holder, own));
    }

    private List<@Nullable ItemStack> offerOf(TradeHolder holder, boolean own) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        return window.painted(exchange == null ? null : exchange.offer(sideOf(holder, own)));
    }

    private static TradeSide sideOf(TradeHolder holder, boolean own) {
        return own ? holder.side() : holder.side().other();
    }

    // --- what the window's clicks do ---

    /**
     * Schedule an offer re-read on {@code holder}'s viewer's region thread. The offer region's provider calls this
     * for every movement it allows; the hop lands the read on the next tick, once Bukkit has applied the interaction
     * to the window.
     */
    void scheduleSync(TradeHolder holder) {
        Objects.requireNonNull(holder, "holder");
        scheduler.onEntity(holder.viewer(), () -> syncOffer(holder));
    }

    /** Re-read {@code holder}'s side after a placement or removal and redraw both windows. */
    void syncOffer(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null) {
            return;
        }
        window.live(holder.viewer()).ifPresent(inv -> {
            exchange.applyOffer(holder.side(), window.readOffer(inv));
            rerender(exchange);
        });
    }

    /** Handle a confirm click: run the swap once both sides agree, otherwise redraw the reset controls. */
    void confirm(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null) {
            return;
        }
        switch (exchange.confirm(holder.side())) {
            case COMMITTED -> commit(exchange);
            case CHANGED -> rerender(exchange);
            case IGNORED -> {}
        }
    }

    /** Refuse a blacklisted item back into the window: tell the viewer their placement was rejected. */
    void refuseBlacklisted(TradeHolder holder) {
        PlayerRef viewer = holder.viewer();
        messageSink.deliver(viewer, messages.resolve(viewer, TradeMessageKey.TRADE_ITEM_BLACKLISTED, Map.of()));
    }

    /**
     * A window closed, with {@code contents} the stacks that were still in its offer region. Two closes reach here.
     * One is the window stepping aside for an amount prompt, which reopens it: the contents are held on the exchange
     * so the reopened window paints them back, and the trade carries on. The other is the real thing, the player
     * closed the window, which cancels the trade and returns both sides' items, from what was physically in the
     * window rather than from the last recorded offer, so a stack placed in the closing tick is still returned.
     */
    void onWindowClosed(TradeHolder holder, List<@Nullable ItemStack> contents) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (promptingViewers.remove(holder.viewer().uuid())) {
            if (exchange != null) {
                exchange.captureItems(holder.side(), toArray(contents));
            }
            return;
        }
        if (exchange == null || !exchange.beginCancel()) {
            // Another path (a commit, or the counterpart's own close) already owns the settlement, and it emptied
            // this region before the window closed, so there is nothing here to return.
            return;
        }
        exchange.captureItems(holder.side(), toArray(contents));
        finishCancel(exchange);
    }

    /**
     * A viewer left-clicked their "add money" button, prompt them for an amount of the currently-selected currency.
     * The window closes to show the prompt (suppressed from the cancel path), and the prompt callback re-stakes the
     * money and reopens. Because the money slot is single but the economy is multi-currency, the currency is the one
     * the viewer has cycled to (see {@link #cycleCurrency}); its staked amount is preserved for the currencies not
     * selected.
     */
    void promptMoney(Player player, TradeHolder holder) {
        Objects.requireNonNull(player, "player");
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (!window.moneyEnabled()
                || exchange == null
                || exchange.session().state().isTerminal()) {
            return;
        }
        String currencyId = window.currencyAt(holder.selectedCurrency());
        PlayerRef viewer = holder.viewer();
        promptingViewers.add(viewer.uuid());
        moneyPrompt.prompt(
                player,
                viewer,
                currencyId,
                text -> onMoneySubmitted(holder, currencyId, text),
                () -> reopenAfterPrompt(holder));
    }

    /** A viewer right-clicked their money button, advance the selected currency and redraw both sides. */
    void cycleCurrency(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (!window.moneyEnabled()
                || exchange == null
                || exchange.session().state().isTerminal()) {
            return;
        }
        holder.cycleCurrency(window.currencyCount());
        rerender(exchange);
    }

    private void onMoneySubmitted(TradeHolder holder, String currencyId, String text) {
        PlayerRef viewer = holder.viewer();
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange != null && !exchange.session().state().isTerminal()) {
            Optional<BigDecimal> amount = parseAmount(text);
            if (amount.isEmpty()) {
                messageSink.deliver(viewer, messages.resolve(viewer, TradeMessageKey.TRADE_MONEY_INVALID, Map.of()));
            } else {
                exchange.setMoney(holder.side(), currencyId, amount.get());
            }
        }
        reopenAfterPrompt(holder);
    }

    /**
     * A viewer clicked their "add experience" button, prompt them for an amount of experience points. The window
     * closes to show the prompt (suppressed from the cancel path), and the prompt callback validates the amount
     * against the experience they actually hold, re-stakes it, and reopens, exactly as the money button does.
     */
    void promptExperience(Player player, TradeHolder holder) {
        Objects.requireNonNull(player, "player");
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null || exchange.session().state().isTerminal()) {
            return;
        }
        PlayerRef viewer = holder.viewer();
        promptingViewers.add(viewer.uuid());
        experiencePrompt.prompt(
                player, viewer, text -> onExperienceSubmitted(holder, text), () -> reopenAfterPrompt(holder));
    }

    private void onExperienceSubmitted(TradeHolder holder, String text) {
        PlayerRef viewer = holder.viewer();
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange != null && !exchange.session().state().isTerminal()) {
            Optional<Long> amount = parseExperience(text);
            if (amount.isEmpty() || amount.get() > experience.available(viewer)) {
                messageSink.deliver(
                        viewer, messages.resolve(viewer, TradeMessageKey.TRADE_EXPERIENCE_INVALID, Map.of()));
            } else {
                exchange.setExperience(holder.side(), amount.get());
            }
        }
        reopenAfterPrompt(holder);
    }

    /**
     * Show the viewer's window again once the prompt resolves. The window is rebuilt rather than re-shown: what the
     * viewer had staked was captured onto the exchange as the prompt took the screen, so the reopened offer region
     * paints it straight back. Both sides are redrawn after, because a money or experience change resets confirms.
     */
    private void reopenAfterPrompt(TradeHolder holder) {
        promptingViewers.remove(holder.viewer().uuid());
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null || exchange.session().state().isTerminal()) {
            return;
        }
        window.open(holder);
        rerender(exchange);
    }

    /** Parse a typed money amount, a strictly-positive number, else empty (an invalid entry is rejected). */
    private static Optional<BigDecimal> parseAmount(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.signum() > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Parse a typed experience amount, a strictly-positive whole number, else empty (an invalid entry is rejected). */
    private static Optional<Long> parseExperience(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static List<Material> parseBlacklist(List<String> ids) {
        List<Material> materials = new java.util.ArrayList<>();
        for (String id : ids) {
            Material material = Material.matchMaterial(id);
            if (material != null) {
                materials.add(material);
            }
        }
        return List.copyOf(materials);
    }

    /** A participant changed world: treat it like a close, returning both sides' items. */
    void onLeave(UUID player) {
        TradeExchange exchange = sessions.find(player);
        if (exchange != null) {
            cancel(exchange);
        }
    }

    /** A participant disconnected: return the quitter's items inline (their thread is theirs now) and the other's async. */
    void onQuit(Player quitter) {
        TradeExchange exchange = sessions.find(quitter.getUniqueId());
        if (exchange == null || !exchange.beginCancel()) {
            return;
        }
        exchange.markCancelled();
        sessions.remove(exchange);
        TradeSide side = exchange.participant(TradeSide.INITIATOR).uuid().equals(quitter.getUniqueId())
                ? TradeSide.INITIATOR
                : TradeSide.PARTNER;
        deliver(quitter, TradeItemCodec.stacks(exchange.offer(side)));
        deliverAndClose(exchange, side.other(), exchange.offer(side.other()));
        publishCancelled(exchange);
        notifyBoth(exchange, TradeMessageKey.TRADE_CANCELLED);
    }

    /** Drain every live trade on module stop or reload, returning all offered items. */
    public void closeAll() {
        for (TradeExchange exchange : sessions.all()) {
            cancel(exchange);
        }
    }

    /** Redraw both participants' windows in place, so a change on one side shows on the other. */
    private void rerender(TradeExchange exchange) {
        for (TradeSide side : TradeSide.values()) {
            window.redraw(exchange.participant(side));
        }
    }

    /**
     * Both sides confirmed: settle the trade all-or-nothing. First hop to each side's region to read its LIVE window
     * and freeze it, then off the tick thread move the staked money (guarded, double-spend-safe) and only on success
     * swap the item clones: a money failure leaves every stack with its owner, so nothing moves partway. Reading the
     * live window here (rather than the last snapshot) is the Folia sub-tick fix: a stack a player places in the same
     * tick as the counterpart's confirm, before its deferred re-read runs, is delivered instead of discarded. The
     * settle runs only once both region reads complete, so it never races an un-read window.
     */
    private void commit(TradeExchange exchange) {
        AtomicInteger remaining = new AtomicInteger(TradeSide.values().length);
        for (TradeSide side : TradeSide.values()) {
            freezeAndCapture(exchange, side, remaining);
        }
    }

    /** Read {@code side}'s live window into the item snapshot, clear and close it, and settle once both sides are read. */
    private void freezeAndCapture(TradeExchange exchange, TradeSide side, AtomicInteger remaining) {
        TradeHolder holder = exchange.holder(side);
        PlayerRef viewer = holder.viewer();
        scheduler.onEntity(viewer, () -> {
            window.live(viewer).ifPresent(inv -> {
                exchange.captureItems(side, window.readOffer(inv));
                window.clearOffer(inv);
            });
            closeWindow(viewer);
            if (remaining.decrementAndGet() == 0) {
                scheduler.async(() -> settleAndDeliver(exchange));
            }
        });
    }

    private void settleAndDeliver(TradeExchange exchange) {
        sessions.remove(exchange);
        // Settle the staked money and experience all-or-nothing; the items swap only when both moved (or nothing was
        // staked), so items, money, and experience move together or not at all.
        if (settlement.settle(exchange.session())) {
            giveBack(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.PARTNER));
            giveBack(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.INITIATOR));
            TradeReceipt receipt = TradeReceipt.of(exchange.session());
            if (auditEnabled) {
                audit.completed(receipt);
            }
            // The fact is published whether or not the operator wants an audit line: one is a log, the other is
            // something another plugin acts on, and tying them together would make the API a logging setting.
            events.publish(new TradeCompleted(
                    exchange.id(),
                    receipt.initiator(),
                    receipt.partner(),
                    receipt.initiatorItems(),
                    receipt.partnerItems(),
                    receipt.initiatorMoney(),
                    receipt.partnerMoney(),
                    receipt.initiatorExperience(),
                    receipt.partnerExperience()));
            notifyBoth(exchange, TradeMessageKey.TRADE_COMPLETED);
        } else {
            giveBack(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.INITIATOR));
            giveBack(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.PARTNER));
            notifyBoth(exchange, TradeMessageKey.TRADE_INSUFFICIENT_FUNDS);
        }
    }

    /** Deliver the (cloned) stacks {@code incoming} to {@code side}'s player, dropping any overflow at their feet. */
    private void giveBack(TradeExchange exchange, TradeSide side, @Nullable ItemStack @Nullable [] incoming) {
        PlayerRef viewer = exchange.holder(side).viewer();
        List<ItemStack> gifts = TradeItemCodec.stacks(incoming);
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                deliver(live, gifts);
            }
        });
    }

    /** Claim the settlement for a cancel and, having won it, return both sides' items and close both windows. */
    private void cancel(TradeExchange exchange) {
        if (exchange.beginCancel()) {
            finishCancel(exchange);
        }
    }

    /** The one place a cancelled trade is announced, so every path that ends one without a swap says so. */
    private void publishCancelled(TradeExchange exchange) {
        events.publish(new TradeCancelled(
                exchange.id(), exchange.participant(TradeSide.INITIATOR), exchange.participant(TradeSide.PARTNER)));
    }

    /** The body of a cancel, run by whichever path won the settle flag. */
    private void finishCancel(TradeExchange exchange) {
        exchange.markCancelled();
        sessions.remove(exchange);
        deliverAndClose(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.INITIATOR));
        deliverAndClose(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.PARTNER));
        publishCancelled(exchange);
        notifyBoth(exchange, TradeMessageKey.TRADE_CANCELLED);
    }

    /**
     * Return {@code side}'s stakes and shut their window. The region is emptied before the window closes, so the
     * close that follows reads an empty region and cannot return the same stacks a second time.
     */
    private void deliverAndClose(TradeExchange exchange, TradeSide side, @Nullable ItemStack @Nullable [] incoming) {
        PlayerRef viewer = exchange.holder(side).viewer();
        List<ItemStack> gifts = TradeItemCodec.stacks(incoming);
        scheduler.onEntity(viewer, () -> {
            window.live(viewer).ifPresent(window::clearOffer);
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                deliver(live, gifts);
                live.closeInventory();
            }
        });
    }

    /** Close {@code viewer}'s window, if they are still online to have one. Runs on their own region thread. */
    private void closeWindow(PlayerRef viewer) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live != null && live.isOnline()) {
            live.closeInventory();
        }
    }

    private void deliver(Player recipient, List<ItemStack> gifts) {
        if (gifts.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow = recipient.getInventory().addItem(gifts.toArray(ItemStack[]::new));
        for (ItemStack extra : overflow.values()) {
            recipient.getWorld().dropItemNaturally(recipient.getLocation(), extra);
        }
    }

    private void notifyBoth(TradeExchange exchange, TradeMessageKey key) {
        for (TradeSide side : TradeSide.values()) {
            PlayerRef viewer = exchange.participant(side);
            messageSink.deliver(viewer, messages.resolve(viewer, key, Map.of()));
        }
    }

    private static @Nullable ItemStack[] toArray(List<@Nullable ItemStack> contents) {
        return contents.toArray(new ItemStack[0]);
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
