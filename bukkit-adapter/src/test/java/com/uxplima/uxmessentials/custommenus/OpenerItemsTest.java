package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerItems;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link OpenerItems} PDC helper: the item it builds carries the target menu id in a way
 * {@link OpenerItems#menuOf} reads straight back, a plain item carries no tag, and the per-opener first-join flag it
 * stamps on a player round-trips too.
 */
class OpenerItemsTest {

    private ServerMock server;
    private OpenerItems openerItems;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        openerItems = new OpenerItems(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildTagsTheItemWithItsMenuIdAndMenuOfReadsItBack() {
        ItemStack item = openerItems.build(opener("hub"));

        assertThat(item.getType()).isEqualTo(Material.COMPASS);
        assertThat(openerItems.menuOf(item)).contains("hub");
    }

    @Test
    void menuOfIsEmptyForAPlainUntaggedItem() {
        assertThat(openerItems.menuOf(new ItemStack(Material.COMPASS))).isEmpty();
    }

    @Test
    void theFirstJoinGivenFlagRoundTrips() {
        PlayerMock player = server.addPlayer("Steve");

        assertThat(openerItems.hasGiven(player, "hub")).isFalse();
        openerItems.markGiven(player, "hub");
        assertThat(openerItems.hasGiven(player, "hub")).isTrue();
        // Different opener, its own flag: marking one does not mark another.
        assertThat(openerItems.hasGiven(player, "shop")).isFalse();
    }

    private static OpenerSpec opener(String menu) {
        return new OpenerSpec(
                menu,
                new OpenerSpec.Item(Material.COMPASS, "<gold>Server Menu", List.of("<gray>Right-click to open")),
                4,
                GiveOnJoin.NEVER);
    }
}
