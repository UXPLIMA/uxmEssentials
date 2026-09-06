package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The raw-entry icon provider in isolation. It must claim the {@code entry} marker (case-insensitively) and return a
 * defensive clone of the bound {@link ItemStack}, and it must decline every other case, a non-{@code entry} spec, an
 * unbound entry, or an entry that is not an {@link ItemStack}, so a real material name or a placeholder used off such
 * a list falls through to the material fallback rather than throwing. MockBukkit supplies the item factory the stacks
 * need; no menu is opened.
 */
class EntryStackIconProviderTest {

    private ServerMock server;
    private MenuContext ctx;
    private final EntryStackIconProvider provider = new EntryStackIconProvider();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        ctx = MenuContext.of(new PlayerRef(player.getUniqueId(), player.getName()), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void claimsTheEntryMarkerAndReturnsACloneOfTheBoundStack() {
        ItemStack backing = new ItemStack(Material.DIAMOND, 3);
        Optional<ItemStack> icon = provider.icon("entry", ctx.withEntry(backing));

        assertThat(icon).isPresent();
        assertThat(icon.get()).isEqualTo(backing);
        // A clone, never the backing item, so name/lore layered onto the tile can never reach the source stack.
        assertThat(icon.get()).isNotSameAs(backing);
    }

    @Test
    void markerIsCaseInsensitiveAndTrimmed() {
        ItemStack backing = new ItemStack(Material.BREAD, 1);
        assertThat(provider.icon("ENTRY", ctx.withEntry(backing))).isPresent();
        assertThat(provider.icon("  Entry  ", ctx.withEntry(backing))).isPresent();
    }

    @Test
    void declinesAnySpecThatIsNotTheEntryMarker() {
        // A real material name (or another provider's prefix) must fall through untouched even with a stack bound.
        ItemStack backing = new ItemStack(Material.DIAMOND, 1);
        assertThat(provider.icon("DIAMOND", ctx.withEntry(backing))).isEmpty();
        assertThat(provider.icon("entry_stack", ctx.withEntry(backing))).isEmpty();
        assertThat(provider.icon("skull:Notch", ctx.withEntry(backing))).isEmpty();
    }

    @Test
    void emptyWhenNoEntryIsBound() {
        assertThat(provider.icon("entry", ctx)).isEmpty();
    }

    @Test
    void emptyWhenTheBoundEntryIsNotAnItemStack() {
        assertThat(provider.icon("entry", ctx.withEntry("not-a-stack"))).isEmpty();
    }
}
