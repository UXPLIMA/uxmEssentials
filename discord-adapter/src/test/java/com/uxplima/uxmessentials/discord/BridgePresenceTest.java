package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import com.uxplima.uxmessentials.api.link.DiscordBridgePresence;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit coverage of the {@code ServicesManager} presence registrar the bridge advertises so the host knows a
 * link code is redeemable. The whole signal is "a {@link DiscordBridgePresence} is registered", so the test
 * proves publish registers exactly one under that type, withdraw unregisters the same instance, and both stay
 * idempotent: a doubled call neither double-registers nor throws.
 */
class BridgePresenceTest {

    @Test
    void publishRegistersExactlyOnePresenceMarker() {
        ServicesManager services = mock(ServicesManager.class);
        Plugin plugin = mock(Plugin.class);
        BridgePresence presence = new BridgePresence(services, plugin);

        presence.publish();
        presence.publish(); // idempotent. The second call must not register a second marker

        ArgumentCaptor<DiscordBridgePresence> marker = ArgumentCaptor.forClass(DiscordBridgePresence.class);
        verify(services, times(1))
                .register(eq(DiscordBridgePresence.class), marker.capture(), eq(plugin), eq(ServicePriority.Normal));
        assertThat(marker.getValue()).isInstanceOf(DiscordBridgePresence.class);
    }

    @Test
    void withdrawUnregistersTheSameMarkerAndIsIdempotent() {
        ServicesManager services = mock(ServicesManager.class);
        Plugin plugin = mock(Plugin.class);
        BridgePresence presence = new BridgePresence(services, plugin);

        presence.publish();
        ArgumentCaptor<DiscordBridgePresence> marker = ArgumentCaptor.forClass(DiscordBridgePresence.class);
        verify(services)
                .register(eq(DiscordBridgePresence.class), marker.capture(), eq(plugin), eq(ServicePriority.Normal));

        presence.withdraw();
        presence.withdraw(); // idempotent. Nothing left to unregister

        verify(services, times(1)).unregister(marker.getValue());
    }

    @Test
    void withdrawBeforePublishDoesNothing() {
        ServicesManager services = mock(ServicesManager.class);
        Plugin plugin = mock(Plugin.class);

        new BridgePresence(services, plugin).withdraw();

        verify(services, never()).unregister(any());
    }
}
