package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LiveDataSources.OnlinePlayerEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LiveDataSources.WorldEntry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Pure unit coverage for the two ready-made live sources' Bukkit-free surface: the environment→icon mapping, the
 * auto-skull convenience, each placeholder reading its record field, and the off-list resilience where a placeholder
 * used without a bound entry (or with the wrong entry type) returns an empty string rather than throwing. No
 * MockBukkit is needed. The sources' snapshot side is exercised in the golden test; here we drive the placeholders
 * directly against a hand-built {@link MenuContext}.
 */
class LiveDataSourcesTest {

    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Viewer");
    private static final UUID ENTRY_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void environmentIconMapsEachKnownEnvironmentAndDefaults() {
        assertThat(LiveDataSources.environmentIcon("NORMAL")).isEqualTo("GRASS_BLOCK");
        assertThat(LiveDataSources.environmentIcon("NETHER")).isEqualTo("NETHERRACK");
        assertThat(LiveDataSources.environmentIcon("THE_END")).isEqualTo("END_STONE");
        assertThat(LiveDataSources.environmentIcon("CUSTOM")).isEqualTo("GRASS_BLOCK");
    }

    @Test
    void onlinePlayerPlaceholdersReadEachRecordField() {
        OnlinePlayerEntry entry = new OnlinePlayerEntry("Alice", ENTRY_UUID, "world_nether", 42, "CREATIVE");
        assertThat(resolve("online_player_name", entry)).isEqualTo("Alice");
        assertThat(resolve("online_player_uuid", entry)).isEqualTo(ENTRY_UUID.toString());
        assertThat(resolve("online_player_world", entry)).isEqualTo("world_nether");
        assertThat(resolve("online_player_ping", entry)).isEqualTo("42");
        assertThat(resolve("online_player_gamemode", entry)).isEqualTo("CREATIVE");
    }

    @Test
    void skullPlaceholderYieldsTheUuidSkinnedHeadSpec() {
        OnlinePlayerEntry entry = new OnlinePlayerEntry("Alice", ENTRY_UUID, "world", 5, "SURVIVAL");
        assertThat(resolve("online_player_skull", entry)).isEqualTo("skull:" + ENTRY_UUID);
    }

    @Test
    void worldPlaceholdersReadEachRecordField() {
        WorldEntry entry = new WorldEntry("world", "NETHER", 7, true);
        assertThat(resolve("worlds_name", entry)).isEqualTo("world");
        assertThat(resolve("worlds_environment", entry)).isEqualTo("NETHER");
        assertThat(resolve("worlds_players", entry)).isEqualTo("7");
        assertThat(resolve("worlds_icon", entry)).isEqualTo("NETHERRACK");
    }

    @Test
    void stackPlaceholdersReadTheBoundItemStack() {
        ServerMock server = MockBukkit.mock();
        try {
            assertThat(server).isNotNull();
            ItemStack stack = new ItemStack(Material.DIAMOND, 5);
            assertThat(resolve("entry_type", stack)).isEqualTo("DIAMOND");
            assertThat(resolve("entry_amount", stack)).isEqualTo("5");
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void placeholderOffAnyListReturnsEmpty() {
        // No entry is bound (the placeholder was written on a static item), so it must render empty, not throw.
        MenuContext ctx = MenuContext.of(VIEWER, null, 0);
        assertThat(handler("online_player_name").apply(ctx)).isEmpty();
        assertThat(handler("worlds_name").apply(ctx)).isEmpty();
        assertThat(handler("entry_type").apply(ctx)).isEmpty();
        assertThat(handler("entry_amount").apply(ctx)).isEmpty();
    }

    @Test
    void placeholderOnAWrongTypedEntryReturnsEmpty() {
        // A world placeholder bound to a player entry (and vice versa) must degrade to empty rather than throw.
        assertThat(resolve("worlds_name", new OnlinePlayerEntry("Alice", ENTRY_UUID, "world", 0, "SURVIVAL")))
                .isEmpty();
        assertThat(resolve("online_player_name", new WorldEntry("world", "NORMAL", 0, true)))
                .isEmpty();
        // An item-stack placeholder bound to a non-stack entry must also degrade to empty rather than throw.
        assertThat(resolve("entry_type", new WorldEntry("world", "NORMAL", 0, true)))
                .isEmpty();
    }

    /** Resolve a registered placeholder against a context bound to {@code entry}. */
    private static String resolve(String id, Object entry) {
        return handler(id).apply(MenuContext.of(VIEWER, null, 0).withEntry(entry));
    }

    /** Pull one registered placeholder handler out of a freshly wired bindings instance. */
    private static Function<MenuContext, String> handler(String id) {
        MenuBindings bindings = new MenuBindings();
        LiveDataSources.register(bindings, new NoopScheduler());
        return bindings.placeholder(id).orElseThrow(() -> new AssertionError("placeholder not registered: " + id));
    }

    /** A scheduler the placeholder tests never call: registration needs one, the placeholders do not touch it. */
    private static final class NoopScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}
