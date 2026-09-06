package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenusWiring;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Wiring-level smoke for the custommenus context: building the wiring over the real generic menu vocabulary loads the
 * operator's {@code menus/*.conf}, registers the {@code /menu} command, and surfaces the loaded names, and the
 * shipped {@code example.conf} is itself a valid spec against that vocabulary, so a fresh install lands a working
 * sample. The bindings here register exactly the generic vocabulary {@code PluginModule} installs at startup.
 */
class CustomMenusWiringTest {

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        com.uxplima.uxmlib.gui.Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        com.uxplima.uxmlib.gui.Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void wiringLoadsOperatorMenusAndRegistersTheCommand(@TempDir Path dataFolder) throws Exception {
        Path menus = Files.createDirectories(dataFolder.resolve("menus"));
        Files.copy(shippedExample(), menus.resolve("example.conf"));
        Files.writeString(menus.resolve("hub.conf"), "rows = 1\nitems { x { slot = 0, material = STONE } }\n");

        CustomMenusWiring.Wired wired = CustomMenusWiring.wire(
                plugin,
                menus(),
                bindings(),
                dataFolder,
                log(),
                scheduler(),
                messages(),
                guiText(),
                new GuiLayouts(dataFolder, log()),
                textInput(),
                new ManagementGuiRegistry());

        assertThat(wired.commands()).hasSize(1);
        assertThat(wired.commands().getFirst().build().getLiteral()).isEqualTo("menu");
        assertThat(wired.menuNames().get()).contains("example", "hub");
        assertThat(wired.listeners()).hasSize(4); // opener interact + join + swap + edit-lock
    }

    @Test
    void aMenuDeclaringACommandBlockAlsoRegistersItsOpenCommand(@TempDir Path dataFolder) throws Exception {
        Path menus = Files.createDirectories(dataFolder.resolve("menus"));
        Files.writeString(menus.resolve("shop.conf"), """
                rows = 1
                items { x { slot = 0, material = STONE, click { left = ["close"] } } }
                command { name = "shop", aliases = ["store"] }
                """);

        CustomMenusWiring.Wired wired = CustomMenusWiring.wire(
                plugin,
                menus(),
                bindings(),
                dataFolder,
                log(),
                scheduler(),
                messages(),
                guiText(),
                new GuiLayouts(dataFolder, log()),
                textInput(),
                new ManagementGuiRegistry());

        assertThat(wired.commands().stream().map(c -> c.build().getLiteral())).contains("menu", "shop");
        assertThat(wired.commands().stream()
                        .filter(c -> c.build().getLiteral().equals("shop"))
                        .findFirst()
                        .orElseThrow()
                        .aliases())
                .containsExactly("store");
    }

    @Test
    void anEmptyMenusFolderLoadsNothingButStillRegistersTheCommandAndListeners(@TempDir Path dataFolder) {
        CustomMenusWiring.Wired wired = CustomMenusWiring.wire(
                plugin,
                menus(),
                bindings(),
                dataFolder,
                log(),
                scheduler(),
                messages(),
                guiText(),
                new GuiLayouts(dataFolder, log()),
                textInput(),
                new ManagementGuiRegistry());

        assertThat(wired.commands()).hasSize(1);
        assertThat(wired.menuNames().get()).isEmpty();
        assertThat(wired.listeners()).hasSize(4); // opener interact + join + swap + edit-lock
    }

    @Test
    void anOpenersConfFileLivingBesideTheMenusIsNotLoadedAsAMenu(@TempDir Path dataFolder) throws Exception {
        Path menus = Files.createDirectories(dataFolder.resolve("menus"));
        Files.writeString(menus.resolve("hub.conf"), "rows = 1\nitems { x { slot = 0, material = STONE } }\n");
        Files.writeString(menus.resolve("openers.conf"), """
                openers = [
                  { menu = "hub", slot = 4, give-on-join = "first", item { material = "COMPASS", name = "<gold>Menu" } }
                ]
                """);

        CustomMenusWiring.Wired wired = CustomMenusWiring.wire(
                plugin,
                menus(),
                bindings(),
                dataFolder,
                log(),
                scheduler(),
                messages(),
                guiText(),
                new GuiLayouts(dataFolder, log()),
                textInput(),
                new ManagementGuiRegistry());

        // openers.conf is reserved for opener config: it must not appear as a loaded menu name, and it arms the
        // two opener listeners rather than being skipped as a malformed menu spec.
        assertThat(wired.menuNames().get()).containsExactly("hub");
        assertThat(wired.listeners()).hasSize(4); // opener interact + join + swap + edit-lock
    }

    /** The generic vocabulary registry exactly as PluginModule installs it at startup (console dispatch off). */
    private static MenuBindings bindings() {
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerActions(bindings, menus(), false, log());
        MenuVocabulary.registerConditions(bindings, allowAll(), log());
        MenuVocabulary.registerPlaceholders(bindings);
        return bindings;
    }

    private static Menus menus() {
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        return new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
    }

    private static GuiText guiText() {
        return new GuiText(new KeyMessages());
    }

    /** A wired text-input seam (anvil default; no live prompt is fired in these wiring smokes). */
    private TextInput textInput() {
        return TextInputTestKit.create(plugin, guiText(), scheduler(), Path.of("nonexistent"), log());
    }

    private static Path shippedExample() {
        return ConfigLayoutResources.resources().resolve("menus/example.conf");
    }

    private static Messages messages() {
        return new KeyMessages();
    }

    private static Scheduler scheduler() {
        return new SyncScheduler();
    }

    private static Logger log() {
        return new NoopLogger();
    }

    private static Permissions allowAll() {
        return new AllowAllPermissions();
    }

    /** Locates the bundled resource root so the test can read the shipped example.conf. */
    private static final class ConfigLayoutResources {
        static Path resources() {
            Path dir = Path.of("").toAbsolutePath();
            while (dir != null) {
                if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                    return dir.resolve("bukkit-adapter/src/main/resources");
                }
                dir = dir.getParent();
            }
            throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** A permissions double that grants everything: the wiring smoke never gates on a real node. */
    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
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
