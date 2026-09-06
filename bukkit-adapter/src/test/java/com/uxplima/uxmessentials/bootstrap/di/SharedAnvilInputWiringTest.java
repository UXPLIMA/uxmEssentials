package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Guards the anvil-input wiring that backs every GUI create/rename prompt (holograms, npc, playerwarps,
 * messaging, communication).
 *
 * <p>{@link AnvilInput} is a Bukkit {@link Listener}: its {@code @EventHandler}s only run once {@link
 * AnvilInput#install()} has registered it. A {@code new AnvilInput(plugin)} handed to a module's wiring
 * without {@code install()} behaves as a vanilla anvil. The rename click is never cancelled and the submit
 * callback never fires, so the entity-create / rename never happens and the prompt item is taken instead.
 * That was the regression: three module wirings (holograms / npc / playerwarps) constructed an anvil inline
 * and never installed it.
 *
 * <p>A full anvil-click simulation is impractical under MockBukkit. {@code Player.openAnvil} throws an
 * {@code UnimplementedOperationException} and there is no {@code AnvilView} mock, so {@code AnvilInput.onClick}
 * can never reach its result-slot branch in a mock world. This guard instead pins the two halves of the fix:
 * the install discriminator that the create/rename flow depends on (a behavioural MockBukkit check), and the
 * wiring contract in {@code PluginModule} that every anvil threaded into a GUI module is the one shared,
 * installed instance (a source guard that fails against the un-installed wiring).
 */
class SharedAnvilInputWiringTest {

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void installedAnvilIsRegisteredAndAnUninstalledOneIsNot() {
        AnvilInput installed = new AnvilInput(plugin);
        AnvilInput neverInstalled = new AnvilInput(plugin);

        installed.install();

        // The installed instance is wired into the plugin's handler list; the never-installed one (the buggy
        // `new AnvilInput(plugin)` that was passed to a module wiring) is absent, so its result-slot handler
        // never runs and the create/rename submit is silently dropped.
        assertThat(registeredAnvils()).containsExactly(installed).doesNotContain(neverInstalled);

        installed.uninstall();
        assertThat(registeredAnvils()).isEmpty();
    }

    @Test
    void oneSharedInstalledAnvilServesEveryGuiModule() {
        // Mirrors the fixed wireModules: a single anvil, installed once, threaded to every GUI-using context.
        // Re-installing the same instance must not multiply its listener registrations, so a single click
        // event is dispatched to it exactly once (no duplicated create-on-submit).
        AnvilInput shared = new AnvilInput(plugin);
        shared.install();
        shared.install();

        assertThat(registeredAnvils()).containsExactly(shared);
    }

    @Test
    void pluginModuleThreadsTheSharedInstalledAnvilIntoEveryGuiModule() {
        String source = pluginModuleSource();

        // Exactly one anvil is constructed in the wiring, and it is installed and torn down on close.
        assertThat(countMatches(source, "new com.uxplima.uxmlib.gui.anvil.AnvilInput("))
                .as("PluginModule must construct exactly one shared AnvilInput, not one per module")
                .isEqualTo(1);
        assertThat(source)
                .as("the shared AnvilInput must be installed and uninstalled on close")
                .contains("anvil.install()")
                .contains("resources.onClose(anvil::uninstall)");

        // The buggy sites passed `new ...AnvilInput(plugin)` straight into a wire call; every anvil reaching a
        // module wiring must be the shared, installed local, never a fresh inline (un-installed) instance.
        List<String> inlineAtCallSites = inlineAnvilArguments(source);
        assertThat(inlineAtCallSites)
                .as(
                        "anvil arguments to module wiring must be the shared installed instance, not a fresh inline one:\n%s",
                        String.join("\n", inlineAtCallSites))
                .isEmpty();
    }

    /**
     * The distinct installed {@link AnvilInput} instances, by identity. Each registers three event handlers, so a
     * single installed anvil contributes three {@link RegisteredListener}s for one logical listener; this collapses
     * those to the one instance so the assertions count anvils, not handler methods.
     */
    private List<AnvilInput> registeredAnvils() {
        List<AnvilInput> anvils = new ArrayList<>();
        for (RegisteredListener registered : HandlerList.getRegisteredListeners(plugin)) {
            if (registered.getListener() instanceof AnvilInput anvil && !containsIdentity(anvils, anvil)) {
                anvils.add(anvil);
            }
        }
        return anvils;
    }

    private static boolean containsIdentity(List<AnvilInput> anvils, AnvilInput candidate) {
        for (AnvilInput anvil : anvils) {
            if (anvil == candidate) {
                return true;
            }
        }
        return false;
    }

    /** Inline {@code new ...AnvilInput(...)} expressions that appear as a wire-call argument (indented past a name). */
    private static List<String> inlineAnvilArguments(String source) {
        Pattern construction = Pattern.compile("new\\s+com\\.uxplima\\.uxmlib\\.gui\\.anvil\\.AnvilInput\\s*\\(");
        List<String> violations = new ArrayList<>();
        for (String raw : source.split("\n", -1)) {
            String line = raw.strip();
            Matcher matcher = construction.matcher(line);
            if (matcher.find() && !line.startsWith("com.uxplima.uxmlib.gui.anvil.AnvilInput anvil =")) {
                violations.add(line);
            }
        }
        return violations;
    }

    private static int countMatches(String source, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = source.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    private static String pluginModuleSource() {
        Path repoRoot = repoRoot();
        Path file = repoRoot.resolve(
                "bukkit-adapter/src/main/java/com/uxplima/uxmessentials/bootstrap/di/PluginModule.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + file, failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
