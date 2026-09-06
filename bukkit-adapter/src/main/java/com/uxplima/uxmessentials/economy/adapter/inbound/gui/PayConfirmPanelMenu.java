package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /pay} confirmation strip with the menu engine and opens it. A one-row window shown when a
 * {@code /pay} exceeds the currency's confirmation threshold: a confirm block, the recipient head, the value, and a
 * cancel block. The pending payment (the recipient and the amount) is handed in as the {@link PendingPayment} the
 * confirm/recipient/value items render from. A left click on the confirm block runs the payer's staged transfer
 * through the same {@link Pay} use case {@code /payconfirm} uses; a left click on the cancel block aborts and closes.
 *
 * <p>The menu holds no new domain logic. The confirm action runs the provider call off the tick thread (the old
 * view's threading), then closes the window on the viewer's entity thread; the cancel action simply closes. Every
 * visible string resolves from the economy catalog. The window carries extra context items beyond a bare yes/no, the
 * recipient head and the value, so it is its own spec rather than a generic {@link Menus#confirm} dialog.
 */
@NullMarked
public final class PayConfirmPanelMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-pay-confirm";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-pay-confirm.conf";

    private final Menus menus;
    private final Pay pay;
    private final Scheduler scheduler;

    public PayConfirmPanelMenu(Menus menus, Pay pay, Scheduler scheduler) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.pay = Objects.requireNonNull(pay, "pay");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Register the per-item placeholders and the confirm/cancel actions the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("payconfirm_target", ctx -> subject(ctx).target().name());
        bindings.placeholder("payconfirm_amount", ctx -> amountText(subject(ctx).amount()));
        bindings.action("economy:pay-confirm", this::confirm);
        bindings.action("economy:pay-cancel", ctx -> ctx.player().closeInventory());
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 1, log));
    }

    /** Open the confirmation strip for {@code viewer}, staging {@code amount} to {@code target}. */
    public void open(Player viewer, PlayerRef target, Money amount) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        menus.open(viewerRef, SPEC_ID, new PendingPayment(target, amount));
    }

    /** Run the payer's staged transfer off the tick thread, then close the window, exactly as the old confirm did. */
    private void confirm(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        Player player = ctx.player();
        scheduler.async(() -> {
            pay.confirm(viewer);
            scheduler.onEntity(viewer, player::closeInventory);
        });
    }

    private PendingPayment subject(MenuContext ctx) {
        return ctx.subject(PendingPayment.class);
    }

    /** The amount rendered as the original strip rendered it: the plain amount and the currency id. */
    private static String amountText(Money amount) {
        return amount.amount().toPlainString() + " " + amount.currency().id().value();
    }

    /**
     * The subject of an open confirmation strip: the recipient and the amount the payer staged. The recipient and
     * value placeholders read {@link #target()} and {@link #amount()} directly, so the render touches no port.
     *
     * @param target the recipient of the staged payment
     * @param amount the amount staged for transfer
     */
    public record PendingPayment(PlayerRef target, Money amount) {

        public PendingPayment {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
