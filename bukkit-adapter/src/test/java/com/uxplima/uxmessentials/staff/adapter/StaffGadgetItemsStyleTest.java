package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.staff.adapter.StaffSettings.GadgetSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Proves the staff gadget item names resolve the project palette: a config name written with the
 * {@code <accent>} token renders as a real cyan display name (not a leftover {@code <accent>} literal),
 * upright (ITALIC off, the {@code ItemBuilder} default), because {@link StaffGadgetItems} parses the
 * operator-authored name through the shared {@link StyleTags} resolver. The gadget names are operator config,
 * not catalog keys, so there is no locale-parity guard: this test is the regression guard for the wiring.
 */
class StaffGadgetItemsStyleTest {

    private StaffGadgetItems gadgetItems;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        gadgetItems = new StaffGadgetItems(MockBukkit.createMockPlugin("uxmEssentials"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anAccentGadgetNameRendersCyanAndUprightWithNoLeftoverToken() {
        GadgetSpec spec = new GadgetSpec(StaffGadget.VANISH, 0, Material.SLIME_BALL, "<accent>Vanish</accent>");

        ItemStack item = gadgetItems.build(spec);
        Component name = item.getItemMeta().displayName();

        assertThat(name).isNotNull();
        assertThat(name.color()).isEqualTo(StyleTags.accent());
        assertThat(name.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);

        String plain = PlainTextComponentSerializer.plainText().serialize(name);
        assertThat(plain).isEqualTo("Vanish");
        assertThat(plain).doesNotContain("<accent>").doesNotContain("<aqua>");
    }

    @Test
    void theShippedDefaultGadgetNamesAllResolveTheAccentTokenUpright() {
        StaffSettings settings = StaffAdapterFakes.defaultSettings();

        assertThat(settings.gadgets()).isNotEmpty();
        for (GadgetSpec spec : settings.gadgets()) {
            ItemStack item = gadgetItems.build(spec);
            Component name = item.getItemMeta().displayName();

            assertThat(name).as("display name for %s", spec.gadget()).isNotNull();
            assertThat(name.color()).as("colour for %s", spec.gadget()).isEqualTo(StyleTags.accent());
            assertThat(name.decoration(TextDecoration.ITALIC))
                    .as("italic for %s", spec.gadget())
                    .isEqualTo(TextDecoration.State.FALSE);

            String plain = PlainTextComponentSerializer.plainText().serialize(name);
            assertThat(plain).as("no leftover token for %s", spec.gadget()).doesNotContain("<");
        }
    }
}
