package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.EchoMessages;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.NoopLogger;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.StaffKeySink;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffAlerts;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import org.junit.jupiter.api.Test;

/**
 * The roster enter/exit alerts: {@link MessagingStaffAlerts} resolves the staff audience through the
 * {@link StaffAudience} node and fans the alert out to every holder EXCEPT the toggling player, who already
 * gets their own toggle feedback. The second half drives the same subscriber shape the wiring registers on the
 * in-process bus, proving an enter/exit event broadcasts while a {@code StaffChatSent} (and, by the same token,
 * the recovery path that publishes neither toggle event) stays silent.
 *
 * <p>The alert is driven on the toggling player's entity region thread, not the global thread, so the audience
 * enumeration and the per-recipient delivery run inside a {@code scheduler.onGlobal} hop. The recording scheduler
 * runs that hop inline and counts it, so a test can assert the enumeration was marshalled onto the global thread.
 */
class MessagingStaffAlertsTest {

    private static final String NODE = "uxmessentials.staff.chat";

    @Test
    void announceEnterFansToEveryStaffMemberExceptTheActor() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        PlayerRef carol = new PlayerRef(UUID.randomUUID(), "Carol");
        StaffKeySink sink = new StaffKeySink();
        MessagingStaffAlerts alerts = new MessagingStaffAlerts(
                fixedAudience(List.of(alice, bob, carol)), sink, new EchoMessages(), new RecordingScheduler(), NODE);

        alerts.announceEnter(alice);

        // Bob and Carol hear it; Alice (the actor) does not: the echo folds the actor name into the enter key.
        assertThat(sink.delivered).hasSize(2);
        assertThat(sink.delivered).allMatch(line -> line.startsWith("staff.alert.enter") && line.contains("Alice"));
    }

    @Test
    void announceExitFansToEveryStaffMemberExceptTheActor() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StaffKeySink sink = new StaffKeySink();
        MessagingStaffAlerts alerts = new MessagingStaffAlerts(
                fixedAudience(List.of(alice, bob)), sink, new EchoMessages(), new RecordingScheduler(), NODE);

        alerts.announceExit(bob);

        assertThat(sink.delivered).hasSize(1);
        assertThat(sink.delivered.get(0)).startsWith("staff.alert.exit").contains("Bob");
    }

    @Test
    void theAudienceEnumerationRunsOnTheGlobalRegionThread() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StaffKeySink sink = new StaffKeySink();
        // The audience records the thread-ownership answer it saw when it was enumerated; the toggle drives the
        // alert off the global thread, so it must observe "on global": proving the read was marshalled, not inline.
        RecordingScheduler scheduler = new RecordingScheduler();
        boolean[] enumeratedOnGlobal = {false};
        StaffAudience audience = node -> {
            enumeratedOnGlobal[0] = scheduler.onGlobalThread();
            return NODE.equals(node) ? List.of(alice, bob) : List.of();
        };
        MessagingStaffAlerts alerts = new MessagingStaffAlerts(audience, sink, new EchoMessages(), scheduler, NODE);

        alerts.announceEnter(alice);

        assertThat(scheduler.globalHops).isOne(); // the enumeration + delivery were marshalled onto the global thread
        assertThat(enumeratedOnGlobal[0]).isTrue();
        assertThat(sink.delivered).hasSize(1); // Bob only, never the actor Alice
    }

    @Test
    void theSubscriberBroadcastsOnEnterAndExitButNotOnOtherStaffEvents() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StaffKeySink sink = new StaffKeySink();
        MessagingStaffAlerts alerts = new MessagingStaffAlerts(
                fixedAudience(List.of(alice, bob)), sink, new EchoMessages(), new RecordingScheduler(), NODE);
        InProcessDomainEventPublisher events = new InProcessDomainEventPublisher(new NoopLogger());
        events.subscribe(alertSubscriber(alerts));

        events.publish(new StaffModeEntered(alice));
        events.publish(new StaffModeExited(alice));
        // A staff-chat event (and, like recovery, anything that is not an enter/exit toggle) must not alert.
        events.publish(new StaffChatSent(alice, "patrolling"));

        // Two broadcasts (enter + exit), each delivered to Bob only, never to the actor Alice.
        assertThat(sink.delivered).hasSize(2);
        assertThat(sink.delivered.get(0)).startsWith("staff.alert.enter");
        assertThat(sink.delivered.get(1)).startsWith("staff.alert.exit");
    }

    /** The exact subscriber shape StaffWiring registers on the bus, rebuilt here so the dispatch is testable. */
    private static Consumer<DomainEvent> alertSubscriber(MessagingStaffAlerts alerts) {
        return event -> {
            if (event instanceof StaffModeEntered entered) {
                alerts.announceEnter(entered.staff());
            } else if (event instanceof StaffModeExited exited) {
                alerts.announceExit(exited.staff());
            }
        };
    }

    private static StaffAudience fixedAudience(List<PlayerRef> audience) {
        return node -> NODE.equals(node) ? audience : List.of();
    }

    /**
     * Runs {@code onGlobal} inline (collapsing the tick boundary) while counting the hops, and reports that the
     * task body is on the global thread so the audience can verify it was marshalled there. The alert path is the
     * only one tested here, so the other hops simply run inline.
     */
    private static final class RecordingScheduler implements Scheduler {
        int globalHops;
        private boolean onGlobal;

        @Override
        public boolean onGlobalThread() {
            return onGlobal;
        }

        @Override
        public void onGlobal(Runnable task) {
            globalHops++;
            boolean previous = onGlobal;
            onGlobal = true;
            try {
                task.run();
            } finally {
                onGlobal = previous;
            }
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
