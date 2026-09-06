package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.adapter.outbound.TradeItemBytes;
import com.uxplima.uxmessentials.trade.application.CrossServerTrade;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeSignal;
import com.uxplima.uxmessentials.trade.application.TradeSignalType;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Drives the player-facing half of a cross-server trade: the rendezvous over the bus and each side's own solo offer
 * window. A {@code /trade} to a player on another backend sends an {@code INVITE}; the target's backend opens a window
 * and answers {@code ACCEPT}; the sender's backend then opens its own. Each participant stakes items in their own
 * {@link CrossTradeWindow} (items-only in v1) and confirms; confirming reads the live window and escrows the side
 * through the {@link CrossServerTrade} coordinator, which runs the two-phase commit and delivers the counterpart's
 * goods. A close before confirming returns the items and aborts both sides; every player/inventory touch hops to the
 * player's region through the injected {@link Scheduler}, so the flow is Folia-safe.
 */
@NullMarked
public final class CrossServerTradeView {

    /** The placeholder id an {@code INVITE} carries for the not-yet-resolved target, matched by name, not uuid. */
    private static final UUID UNRESOLVED = new UUID(0L, 0L);

    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final CrossServerTrade coordinator;
    private final TradeBus bus;
    private final CrossTradeWindow window;
    private final ConcurrentHashMap<UUID, CrossTradeHolder> sessions = new ConcurrentHashMap<>();

    public CrossServerTradeView(
            Messages messages,
            MessageSink messageSink,
            Scheduler scheduler,
            CrossServerTrade coordinator,
            TradeBus bus,
            CrossTradeWindow window) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.window = Objects.requireNonNull(window, "window");
    }

    /** Register the window's spec and its bindings; the wiring calls this once, before the first invite. */
    public void register(MenuBindings bindings) {
        window.register(bindings, this);
    }

    /** The lifecycle listener bound to this view: the wiring registers it. */
    public CrossServerTradeListener newListener() {
        return new CrossServerTradeListener(this);
    }

    /** Whether {@code player} is already in a cross-server trade: the {@code /trade} busy check reads it. */
    public boolean isTrading(UUID player) {
        return sessions.containsKey(player);
    }

    /** The open window {@code player} is staking into, or {@code null} when they are not in a cross-server trade. */
    @Nullable CrossTradeHolder session(UUID player) {
        return sessions.get(player);
    }

    /** Send a cross-server trade request to a player named {@code targetName} on another backend. */
    public void invite(Player sender, String targetName) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(targetName, "targetName");
        PlayerRef from = ref(sender);
        bus.send(new TradeSignal(
                TradeId.newId(),
                TradeSignalType.INVITE,
                from,
                new PlayerRef(UNRESOLVED, targetName),
                bus.localServer()));
        notify(from, TradeMessageKey.TRADE_CROSS_SERVER_REQUEST_SENT, Map.of("player", targetName));
    }

    /** Route an inbound rendezvous signal; escrow signals are handled by the coordinator, not here. */
    public void onSignal(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        switch (signal.type()) {
            case INVITE -> scheduler.onGlobal(() -> onInvite(signal));
            case ACCEPT -> scheduler.onGlobal(() -> onAccept(signal));
            case ABORT -> onAbort(signal);
            case COMMIT -> notify(signal.to(), TradeMessageKey.TRADE_CROSS_SERVER_COMPLETED, Map.of());
            case READY, DECLINE -> {
                // READY is the coordinator's; DECLINE is folded into ABORT.
            }
        }
    }

    private void onInvite(TradeSignal signal) {
        Player target = Bukkit.getPlayerExact(signal.to().name());
        if (target == null || isTrading(target.getUniqueId())) {
            return;
        }
        PlayerRef local = ref(target);
        open(signal.tradeId(), local, signal.from(), signal.originServer());
        notify(
                local,
                TradeMessageKey.TRADE_CROSS_SERVER_INCOMING,
                Map.of("player", signal.from().name()));
        bus.send(new TradeSignal(signal.tradeId(), TradeSignalType.ACCEPT, local, signal.from(), bus.localServer()));
    }

    private void onAccept(TradeSignal signal) {
        Player sender = Bukkit.getPlayer(signal.to().uuid());
        if (sender == null || isTrading(sender.getUniqueId())) {
            return;
        }
        open(signal.tradeId(), ref(sender), signal.from(), signal.originServer());
    }

    private void onAbort(TradeSignal signal) {
        CrossTradeHolder holder = sessions.get(signal.to().uuid());
        if (holder != null) {
            // The peer already gave up, so this side is only returned and closed; telling them again would be noise.
            abort(holder, false);
        }
    }

    private void open(TradeId tradeId, PlayerRef local, PlayerRef remote, String remoteServer) {
        CrossTradeHolder holder = new CrossTradeHolder(tradeId, local, remote, remoteServer);
        sessions.put(local.uuid(), holder);
        window.open(holder);
    }

    /** Whether the local player may still move items in their window: only until the side is escrowed or returned. */
    boolean acceptsItems(CrossTradeHolder holder) {
        return !holder.escrowed();
    }

    /** A confirm click: read the live window, escrow the items, and close; the coordinator settles from here. */
    void confirm(CrossTradeHolder holder) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid());
        List<ItemStack> staked = window.live(holder.local())
                .map(inv -> {
                    List<ItemStack> read = TradeItemCodec.stacks(window.readOffer(inv));
                    window.clearOffer(inv);
                    return read;
                })
                .orElse(List.of());
        closeWindow(holder);
        notify(
                holder.local(),
                TradeMessageKey.TRADE_CROSS_SERVER_ESCROWED,
                Map.of("player", holder.remote().name()));
        String itemData = TradeItemBytes.encode(staked);
        int count = TradeItemBytes.totalCount(staked);
        scheduler.async(() -> coordinator.escrow(
                holder.tradeId(), holder.local(), holder.remoteServer(), holder.remote(), itemData, count, Map.of()));
    }

    /**
     * A window closed before confirming: return what was staked in it and abort the other side. The escrow gate wins
     * here only when no confirm ran first: a plain close is the real abort and takes this path exactly once, while
     * the close a confirm causes loses the gate (the confirm already claimed it) and does nothing, so it never fires
     * a spurious {@code ABORT} that would tear the counterpart down. The items come from the window itself rather
     * than from any snapshot, so a stack placed in the closing tick is still returned.
     */
    void onWindowClosed(CrossTradeHolder holder, List<@Nullable ItemStack> contents) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid());
        deliver(holder.local(), TradeItemCodec.stacks(contents.toArray(new ItemStack[0])));
        notify(holder.local(), TradeMessageKey.TRADE_CANCELLED, Map.of());
        bus.send(new TradeSignal(
                holder.tradeId(), TradeSignalType.ABORT, holder.local(), holder.remote(), bus.localServer()));
    }

    /**
     * End this side from outside its window (the peer aborted, the player quit, or the module is stopping) by
     * reading whatever is still staked back out of the live window and returning it. {@code tellPeer} is false only
     * when the peer is the one who aborted, since they need no telling.
     */
    private void abort(CrossTradeHolder holder, boolean tellPeer) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid());
        returnAndClose(holder);
        notify(holder.local(), TradeMessageKey.TRADE_CANCELLED, Map.of());
        if (tellPeer) {
            bus.send(new TradeSignal(
                    holder.tradeId(), TradeSignalType.ABORT, holder.local(), holder.remote(), bus.localServer()));
        }
    }

    /**
     * Drain every open cross-server trade window on module stop or reload: return each side's still-staked items to the
     * player and signal the peer to abort, exactly as a plain close does. Mirrors the same-server {@code TradeView.closeAll}
     * so a disable or {@code /uxmess reload trade} never drops the transient window's items. Snapshots the open holders
     * first because each abort removes from {@link #sessions} as it drains.
     */
    public void flushAll() {
        for (CrossTradeHolder holder : List.copyOf(sessions.values())) {
            abort(holder, true);
        }
    }

    /** The window a player quit belonged to a cross-server trade, treat the disconnect as a close. */
    public void onQuit(Player quitter) {
        CrossTradeHolder holder = sessions.get(quitter.getUniqueId());
        if (holder != null) {
            abort(holder, true);
        }
    }

    /** Empty the live window into the player's hands and shut it, on their own region thread. */
    private void returnAndClose(CrossTradeHolder holder) {
        PlayerRef viewer = holder.local();
        scheduler.onEntity(viewer, () -> {
            List<ItemStack> staked = window.live(viewer)
                    .map(inv -> {
                        List<ItemStack> read = TradeItemCodec.stacks(window.readOffer(inv));
                        window.clearOffer(inv);
                        return read;
                    })
                    .orElse(List.of());
            deliverNow(viewer, staked);
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    /** Hand {@code stacks} to {@code viewer} on their own region thread, dropping any overflow at their feet. */
    private void deliver(PlayerRef viewer, List<ItemStack> stacks) {
        scheduler.onEntity(viewer, () -> deliverNow(viewer, stacks));
    }

    private void deliverNow(PlayerRef viewer, List<ItemStack> stacks) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline() || stacks.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            live.getInventory()
                    .addItem(stack)
                    .values()
                    .forEach(extra -> live.getWorld().dropItemNaturally(live.getLocation(), extra));
        }
    }

    private void closeWindow(CrossTradeHolder holder) {
        PlayerRef viewer = holder.local();
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    private void notify(PlayerRef who, TradeMessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(who, messages.resolve(who, key, placeholders));
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
