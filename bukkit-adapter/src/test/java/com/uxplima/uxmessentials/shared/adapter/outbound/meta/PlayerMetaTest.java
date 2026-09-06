package com.uxplima.uxmessentials.shared.adapter.outbound.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link PlayerMeta} over a live player's {@link org.bukkit.persistence.PersistentDataContainer}.
 * It proves the set/get round-trip stores a {@link PersistentDataType#STRING}, that {@link PlayerMeta#add}
 * accumulates a numeric value, that {@link PlayerMeta#remove} and {@link PlayerMeta#has} behave, and that the
 * operator-arbitrary {@link NamespacedKey} is cached. The same instance is returned for repeated calls and the
 * value lands under the one {@code uxmessentials} namespace, so a hot click path never builds a key per call.
 */
class PlayerMetaTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerMeta meta;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        meta = new PlayerMeta(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setThenGetRoundTripsAStringValue() {
        meta.set(player, "rank", "gold");

        assertThat(meta.get(player, "rank")).contains("gold");
        // The raw container holds it as a STRING under the plugin's namespace.
        NamespacedKey key = meta.keyFor("rank");
        assertThat(player.getPersistentDataContainer().get(key, PersistentDataType.STRING))
                .isEqualTo("gold");
        assertThat(key.getNamespace()).isEqualTo(plugin.getName().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void getOfAnUnsetKeyIsEmptyAndHasIsFalse() {
        assertThat(meta.get(player, "missing")).isEmpty();
        assertThat(meta.has(player, "missing")).isFalse();
    }

    @Test
    void addAccumulatesTheNumericValueAndStoresItAsAString() {
        assertThat(meta.add(player, "score", 10.0)).isEqualTo(10.0); // missing reads as zero
        assertThat(meta.add(player, "score", 5.0)).isEqualTo(15.0);

        assertThat(meta.get(player, "score")).contains("15"); // whole number, no trailing .0
    }

    @Test
    void hasReflectsSetAndRemove() {
        meta.set(player, "flag", "1");
        assertThat(meta.has(player, "flag")).isTrue();

        meta.remove(player, "flag");
        assertThat(meta.has(player, "flag")).isFalse();
        assertThat(meta.get(player, "flag")).isEmpty();
    }

    @Test
    void namespacedKeyIsCachedAcrossCalls() {
        NamespacedKey first = meta.keyFor("anti-dupe.token");
        NamespacedKey second = meta.keyFor("anti-dupe.token");

        assertThat(second).isSameAs(first);
    }

    @Test
    void arbitraryKeyNamesAreSanitisedToALegalKey() {
        // Spaces and uppercase are illegal in a NamespacedKey value segment; the accessor folds them.
        meta.set(player, "My Key!", "v");

        assertThat(meta.get(player, "My Key!")).contains("v");
        assertThat(meta.keyFor("My Key!").getKey()).isEqualTo("my_key_");
    }
}
