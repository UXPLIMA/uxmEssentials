package com.uxplima.uxmessentials.staff.adapter;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.staff.adapter.StaffWiring.StaffSeams;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffAlerts;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Wires the staff-roster enter/exit alerts onto the in-process event bus. The alerts ride the messaging
 * staff-chat audience, so the subscription exists only when the messaging seam is bound; with messaging off the
 * subscriber is never registered and a toggle simply produces no roster alert.
 *
 * <p>{@code EnterStaffMode}/{@code ExitStaffMode} publish the toggle events; {@code RecoverStaffLoadout}
 * publishes neither, so a crash recovery on join never broadcasts an alert. The returned subscriber is the handle
 * the wiring drops on stop so the subscription does not outlive the module.
 */
@NullMarked
final class StaffAlertWiring {

    private StaffAlertWiring() {}

    /**
     * Subscribe the roster-alert consumer to {@code events}, returning it so {@code Wired.stop} can unsubscribe.
     * Returns {@code null} when the messaging audience seam is absent: nothing is registered in that case.
     */
    static @Nullable Consumer<DomainEvent> subscribe(
            StaffSeams seams, StaffSettings settings, KernelPorts kernel, InProcessDomainEventPublisher events) {
        if (seams.staffAudience().isEmpty()) {
            return null;
        }
        MessagingStaffAlerts alerts = new MessagingStaffAlerts(
                seams.staffAudience().get(),
                kernel.messageSink(),
                kernel.messages(),
                kernel.scheduler(),
                settings.staffChatNode());
        Consumer<DomainEvent> subscriber = event -> dispatch(alerts, event);
        events.subscribe(subscriber);
        return subscriber;
    }

    private static void dispatch(MessagingStaffAlerts alerts, DomainEvent event) {
        if (event instanceof StaffModeEntered entered) {
            alerts.announceEnter(entered.staff());
        } else if (event instanceof StaffModeExited exited) {
            alerts.announceExit(exited.staff());
        }
    }
}
