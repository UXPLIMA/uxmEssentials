package com.uxplima.uxmessentials.shared.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The runtime registry in isolation, with no server: a registered provider's claim is returned, an empty registry
 * resolves to empty, and when two providers both claim a spec the first registered wins, the same first-non-empty
 * rule the built-in {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders} chain
 * follows. {@link ItemStack} stand-ins are mocked so the test stays a plain unit with no MockBukkit server.
 */
class IconProviderRegistryTest {

    private final MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "Viewer"), null, 0);

    @Test
    void aRegisteredProviderResolvesItsClaim() {
        ItemStack claimed = mock(ItemStack.class);
        IconProviderRegistry registry = new IconProviderRegistry();
        registry.register((spec, context) -> spec.equals("test:foo") ? Optional.of(claimed) : Optional.empty());

        assertThat(registry.resolve("test:foo", ctx)).containsSame(claimed);
    }

    @Test
    void anUnclaimedSpecResolvesToEmpty() {
        IconProviderRegistry registry = new IconProviderRegistry();
        registry.register(
                (spec, context) -> spec.equals("test:foo") ? Optional.of(mock(ItemStack.class)) : Optional.empty());

        assertThat(registry.resolve("test:other", ctx)).isEmpty();
    }

    @Test
    void anEmptyRegistryResolvesToEmpty() {
        assertThat(new IconProviderRegistry().resolve("test:foo", ctx)).isEmpty();
    }

    @Test
    void theFirstProviderThatClaimsWins() {
        ItemStack first = mock(ItemStack.class);
        ItemStack second = mock(ItemStack.class);
        IconProviderRegistry registry = new IconProviderRegistry();
        registry.register((spec, context) -> Optional.of(first));
        registry.register((spec, context) -> Optional.of(second));

        assertThat(registry.resolve("test:foo", ctx))
                .as("registration order decides ties, mirroring the built-in chain's first-non-empty rule")
                .containsSame(first);
    }

    @Test
    void registeringNullIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new IconProviderRegistry().register(null));
    }
}
