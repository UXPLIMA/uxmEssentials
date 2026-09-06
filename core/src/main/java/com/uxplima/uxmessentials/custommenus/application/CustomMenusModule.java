package com.uxplima.uxmessentials.custommenus.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The custommenus bounded context as a first-class {@link FeatureModule}: the operator surface over the Phase-1 menu
 * engine. When enabled, the adapter loads every {@code menus/*.conf} the operator authored and registers the
 * {@code /menu} command (open / list / reload); when disabled, no menus load, the command is not registered, and the
 * engine sees no operator specs at all.
 *
 * <p><b>Ships enabled by default but inert.</b> Like holograms and npc this is a steady-state context that changes
 * nothing out of the box. A fresh install ships one sample {@code menus/example.conf}, but no menu opens until a
 * player runs {@code /menu open <name>}. The {@link #enabled(ConfigStore)} gate therefore defaults to {@code true};
 * an operator flips {@code modules.custommenus.enabled = false} to turn the whole surface off.
 *
 * <p>The {@code /menu} command depends on the shared menu engine ({@code Menus} / the {@code CustomMenuLoader}),
 * which live in the bukkit-adapter rather than the kernel ports, so, as with worlds and warps, the real Brigadier
 * node is constructed in the adapter wiring. The {@link CommandSpec} published here is the catalog/drift descriptor
 * that names the literal and its base node; the lifecycle bookkeeping keeps {@code stop()} honest.
 */
@NullMarked
public final class CustomMenusModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("custommenus");
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running;

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        // The real /menu node is built in the adapter wiring over the menu engine; this descriptor names the literal
        // and its base node so the command catalog and the feature-module drift guard see the context's surface.
        return List.of(spec("menu", "uxmessentials.menu.use", descriptor("menu", "Open operator custom menus")));
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The single menu click listener is installed once in bootstrap for every menu-using context (our own feature
        // menus and operator menus alike), so custommenus registers no listener of its own.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // custommenus persists nothing: the menus are operator-authored .conf files on disk, so the module owns no
        // Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // A steady-state context that ships enabled but inert (no menu opens until a player runs /menu open). The
        // default here is true; an operator opts out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The CustomMenuLoader run and the /menu command are constructed in the adapter wiring over the menu engine;
        // the lifecycle bookkeeping (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started. */
    public boolean isRunning() {
        return running;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand descriptor(String literal, String description) {
        return new MenuCommandDescriptor(literal, description);
    }

    /** Platform-neutral handle naming the {@code /menu} literal for the catalog; the adapter builds the real node. */
    private record MenuCommandDescriptor(String literal, String description) implements BrigadierCommand {}
}
