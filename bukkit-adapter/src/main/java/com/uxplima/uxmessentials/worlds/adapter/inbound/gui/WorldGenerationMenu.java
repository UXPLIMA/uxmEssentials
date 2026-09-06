package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the read-only generation summary for one world with the menu engine and opens it. A three-row panel
 * reporting the world's immutable spec, environment, generation type, seed, and external generator (or
 * {@code vanilla} when none), over a single back button to the per-world hub. None of the four info slots mutate
 * anything; the spec is fixed at creation, so this screen only shows it.
 *
 * <p>The world's spec is read on the viewer's entity thread at open and handed in as the {@link Subject}, so each info
 * line fills from the {@code world_gen_*} placeholders without the renderer touching a port. This replays the old
 * bespoke {@code WorldGenerationView} verbatim through the engine; the per-world hub it returns to is injected through
 * {@link #bind} after this menu so their re-open cycle is broken. Every visible string resolves from the worlds
 * catalog.
 */
@NullMarked
public final class WorldGenerationMenu {

    /** The engine spec id this summary registers and opens under. */
    public static final String SPEC_ID = "world-generation";

    private static final String SPEC_RESOURCE = "modules/worlds/gui/world-generation.conf";
    private static final int ROWS = 3;
    private static final String NONE_SEED = "(random)";
    private static final String VANILLA = "vanilla";

    private final Menus menus;
    private final Scheduler scheduler;
    private final WorldRepository repository;

    /** The per-world hub the back button returns to; injected after this menu via {@link #bind} to break the cycle. */
    private @Nullable WorldMainMenu mainMenu;

    public WorldGenerationMenu(Menus menus, Scheduler scheduler, WorldRepository repository) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** Wire the per-world hub the back button returns to; breaks the hub-drills-here, here-returns-to-hub cycle. */
    public void bind(WorldMainMenu mainMenu) {
        this.mainMenu = Objects.requireNonNull(mainMenu, "mainMenu");
    }

    /** Register the subject placeholders, the back action, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("world_gen_world", ctx -> subject(ctx).world().value());
        bindings.placeholder("world_gen_environment", ctx -> subject(ctx).environment());
        bindings.placeholder("world_gen_type", ctx -> subject(ctx).type());
        bindings.placeholder("world_gen_seed", ctx -> subject(ctx).seed());
        bindings.placeholder("world_gen_generator", ctx -> subject(ctx).generator());
        bindings.action("worlds:editor-generation-back", this::back);
        menus.registerSpec(SPEC_ID, WorldEditorSpecs.load(SPEC_RESOURCE, ROWS, dataFolder, log));
    }

    /**
     * Open the read-only generation summary for {@code world}. The spec is read on the viewer's entity thread and
     * handed to the engine as the subject; a world missing from the repository falls back to a normal spec so the
     * screen still renders sensibly.
     */
    public void open(Player player, PlayerRef viewer, WorldName world) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(world, "world");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, snapshot(world)));
    }

    private Subject snapshot(WorldName world) {
        WorldSpec spec = repository.find(world).map(ManagedWorld::spec).orElseGet(WorldSpec::normal);
        String seed = spec.seed().map(String::valueOf).orElse(NONE_SEED);
        String generator = spec.generator().map(GeneratorRef::value).orElse(VANILLA);
        return new Subject(world, spec.environment().name(), spec.worldType().name(), seed, generator);
    }

    private void back(MenuActionContext ctx) {
        if (mainMenu != null) {
            mainMenu.open(ctx.player(), ctx.viewer(), subject(ctx).world());
        }
    }

    private Subject subject(MenuContext ctx) {
        return ctx.subject(Subject.class);
    }

    private Subject subject(MenuActionContext ctx) {
        return ctx.subject(Subject.class);
    }

    /**
     * The subject of an open generation summary: the world and the spec values it shows, environment, type, seed
     * (or {@code (random)}) and generator (or {@code vanilla}). Read on the viewer's entity thread before the open
     * so the engine renders without a port read. The placeholders read this directly.
     *
     * @param world the world this summary describes
     * @param environment the world's environment name
     * @param type the world's generation type name
     * @param seed the world's seed, or {@code (random)} when none was set
     * @param generator the world's external generator, or {@code vanilla} when none
     */
    public record Subject(WorldName world, String environment, String type, String seed, String generator) {

        public Subject {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(seed, "seed");
            Objects.requireNonNull(generator, "generator");
        }
    }
}
