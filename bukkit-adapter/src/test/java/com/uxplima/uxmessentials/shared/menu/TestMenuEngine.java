package com.uxplima.uxmessentials.shared.menu;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * A test-only builder for a wired menu engine. Constructing the engine touches the {@code render/} and
 * {@code runtime/} internals an ArchUnit fence keeps private to the engine; this helper lives in the exempt
 * {@code shared.menu} package (the same exemption the engine's own tests rely on) so a feature's test fixtures can
 * stand up a real engine without each having to reach into those internals itself. A feature test builds the
 * engine here, registers its bindings on the returned {@link MenuBindings}, and opens specs through the returned
 * {@link Menus}: the same two public types production wiring uses. The click listener is built lazily only when a
 * test needs to dispatch clicks, so a fixture that merely opens a window need not supply a plugin.
 */
public final class TestMenuEngine {

    private final MenuBindings bindings;
    private final MenuRenderer renderer;
    private final Menus menus;
    private final Scheduler scheduler;

    private TestMenuEngine(MenuBindings bindings, MenuRenderer renderer, Menus menus, Scheduler scheduler) {
        this.bindings = bindings;
        this.renderer = renderer;
        this.menus = menus;
        this.scheduler = scheduler;
    }

    /** Build the engine façade and bindings over {@code messages}/{@code scheduler}; no click listener yet. */
    public static TestMenuEngine create(Messages messages, Scheduler scheduler) {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions(), bindings.contents());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        return new TestMenuEngine(bindings, renderer, menus, scheduler);
    }

    /**
     * A logger for a fixture that loads a shipped spec off the classpath. Those specs parse cleanly, so there is
     * nothing for it to report; a fixture whose spec did fail to load would rather see the assertion than a log line.
     */
    public static final Logger SILENT_LOG = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    /** The façade a feature opens specs through. */
    public Menus menus() {
        return menus;
    }

    /** The registries a feature registers its bindings on. */
    public MenuBindings bindings() {
        return bindings;
    }

    /** Build the click listener over {@code plugin} and register it so a dispatched click is routed. */
    public void installListener(Plugin plugin) {
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                System::currentTimeMillis,
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry(),
                null,
                bindings.contents());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
