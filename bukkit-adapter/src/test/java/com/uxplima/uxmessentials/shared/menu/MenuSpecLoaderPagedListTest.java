package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

/**
 * The {@code list { }} block gained two optional keys, {@code page-size} and {@code sorts}, that a paged source
 * reads to page and order its corpus at the store. This pins that the loader reads both when present, defaults them
 * to the historic "derive size from slots, no explicit sort" when absent, and rejects a negative page size loudly,
 * naming the offending item so an operator can find it.
 */
class MenuSpecLoaderPagedListTest {

    @Test
    void readsPageSizeAndSortsWhenDeclared() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                rows = 6
                items {
                  warps {
                    slots = ["0-44"]
                    list {
                      source    = "playerwarps:browse"
                      page-size = 45
                      sorts     = ["RATING", "VISITS", "NEWEST", "ALPHABETICAL"]
                      template { material = "%pwarp_icon%", name = "@pwarp.browse.entry" }
                    }
                  }
                }
                """);

        ListSpec list = spec.items().get("warps").list().orElseThrow();
        assertThat(list.source().id()).isEqualTo("playerwarps:browse");
        assertThat(list.pageSize()).isEqualTo(45);
        assertThat(list.sorts()).containsExactly("RATING", "VISITS", "NEWEST", "ALPHABETICAL");
    }

    @Test
    void defaultsToDerivedSizeAndNoSortsWhenKeysAbsent() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                rows = 6
                items {
                  warps {
                    slots = ["0-44"]
                    list {
                      source   = "playerwarps:browse"
                      template { material = "%pwarp_icon%", name = "@pwarp.browse.entry" }
                    }
                  }
                }
                """);

        ListSpec list = spec.items().get("warps").list().orElseThrow();
        assertThat(list.pageSize())
                .as("absent page-size derives the page size from the item's slot count")
                .isZero();
        assertThat(list.sorts())
                .as("absent sorts leaves the source on its default order")
                .isEmpty();
    }

    @Test
    void rejectsANegativePageSizeNamingTheItem() {
        MenuSpecLoader loader = new MenuSpecLoader();
        String hocon = """
                rows = 6
                items {
                  warps {
                    slots = ["0-44"]
                    list {
                      source    = "playerwarps:browse"
                      page-size = -1
                      template { material = "%pwarp_icon%", name = "@pwarp.browse.entry" }
                    }
                  }
                }
                """;

        assertThatThrownBy(() -> loader.parse(hocon))
                .isInstanceOf(MenuSpecException.class)
                .hasMessageContaining("warps");
    }

    @Test
    void aShippedListSpecStillLoadsWithTheHistoricDefaults() {
        MenuSpec spec = new MenuSpecLoader().parse(shippedSpec("/modules/playerwarps/gui/playerwarp-list.conf"));

        ListSpec list = spec.items().get("warps").list().orElseThrow();
        assertThat(list.source().id()).isEqualTo("playerwarps:list");
        assertThat(list.pageSize())
                .as("a shipped spec that names no page-size must still derive its size from slots")
                .isZero();
        assertThat(list.sorts())
                .as("a shipped spec that names no sorts must still carry an empty sort list")
                .isEmpty();
    }

    private static String shippedSpec(String resource) {
        try (InputStream in = MenuSpecLoaderPagedListTest.class.getResourceAsStream(resource)) {
            return new String(Objects.requireNonNull(in, resource).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
