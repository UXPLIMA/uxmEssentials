package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the new-world configuration screen with the menu engine and opens it. A three-row panel carrying one
 * button per field of an in-flight {@link WorldCreateDraft}: the name and seed are captured through the shared
 * {@link TextInput} seam, while the environment, world type and generator are selectors a click cycles in place. The
 * create button builds the draft's {@link WorldName} and {@code WorldSpec} and runs {@link CreateWorld} on the global
 * region thread (the only place a Bukkit world handle may be created), then re-opens the world list so the new world
 * appears. A back button returns to the list.
 *
 * <p>The draft is the menu subject, so every cycle or input submission re-opens this screen with a fresh draft, the
 * screen holds no per-viewer mutable state of its own. The spec mapping mirrors {@code /worlds create} exactly
 * (defaults NORMAL/NORMAL, empty seed and generator), so a GUI-built world is identical to a command-built one. This
 * replays the old bespoke {@code WorldCreateView} verbatim through the engine. Every visible string resolves from the
 * worlds catalog.
 */
@NullMarked
public final class WorldCreateMenu {

    /** The engine spec id this screen registers and opens under. */
    public static final String SPEC_ID = "world-create";

    private static final String SPEC_RESOURCE = "modules/worlds/gui/world-create.conf";
    private static final int ROWS = 3;

    /** The config key the operator flips between anvil and chat for the new-world name prompt. */
    private static final String NAME_INPUT_KEY = "world.create-name";
    /** The config key the operator flips between anvil and chat for the new-world seed prompt. */
    private static final String SEED_INPUT_KEY = "world.create-seed";

    private static final String NAME_UNSET = "(not set)";
    private static final String SEED_RANDOM = "(random)";
    private static final String GENERATOR_VANILLA = "vanilla";

    private final Menus menus;
    private final Scheduler scheduler;
    private final CreateWorld createWorld;
    private final Notifier notifier;
    private final TextInput textInput;
    private final BiConsumer<Player, PlayerRef> reopenList;

    public WorldCreateMenu(
            Menus menus,
            Scheduler scheduler,
            CreateWorld createWorld,
            Notifier notifier,
            TextInput textInput,
            BiConsumer<Player, PlayerRef> reopenList) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.createWorld = Objects.requireNonNull(createWorld, "createWorld");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.reopenList = Objects.requireNonNull(reopenList, "reopenList");
    }

    /** Register the draft placeholders, the cycle/input/create/back actions, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder(
                "world_create_name", ctx -> draft(ctx).hasName() ? draft(ctx).name() : NAME_UNSET);
        bindings.placeholder(
                "world_create_environment", ctx -> draft(ctx).environment().name());
        bindings.placeholder("world_create_type", ctx -> draft(ctx).worldType().name());
        bindings.placeholder(
                "world_create_generator", ctx -> draft(ctx).generatorId().orElse(GENERATOR_VANILLA));
        bindings.placeholder(
                "world_create_seed",
                ctx -> draft(ctx).seed().map(String::valueOf).orElse(SEED_RANDOM));
        bindings.action("worlds:create-name", this::promptName);
        bindings.action("worlds:create-seed", this::promptSeed);
        bindings.action("worlds:create-environment-next", ctx -> cycleEnvironment(ctx, false));
        bindings.action("worlds:create-environment-prev", ctx -> cycleEnvironment(ctx, true));
        bindings.action("worlds:create-type-next", ctx -> cycleType(ctx, false));
        bindings.action("worlds:create-type-prev", ctx -> cycleType(ctx, true));
        bindings.action("worlds:create-generator-next", ctx -> cycleGenerator(ctx, false));
        bindings.action("worlds:create-generator-prev", ctx -> cycleGenerator(ctx, true));
        bindings.action("worlds:create-confirm", this::create);
        bindings.action("worlds:create-back", ctx -> reopenList.accept(ctx.player(), ctx.viewer()));
        menus.registerSpec(SPEC_ID, WorldEditorSpecs.load(SPEC_RESOURCE, ROWS, dataFolder, log));
    }

    /** Open a fresh create screen for {@code viewer}. */
    public void open(Player player, PlayerRef viewer) {
        open(player, viewer, WorldCreateDraft.empty());
    }

    /** Open the create screen showing {@code draft}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, WorldCreateDraft draft) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(draft, "draft");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, draft));
    }

    private void cycleEnvironment(MenuActionContext ctx, boolean backward) {
        WorldCreateDraft draft = draft(ctx);
        open(
                ctx.player(),
                ctx.viewer(),
                draft.withEnvironment(cycle(WorldEnvironment.values(), draft.environment(), backward)));
    }

    private void cycleType(MenuActionContext ctx, boolean backward) {
        WorldCreateDraft draft = draft(ctx);
        open(
                ctx.player(),
                ctx.viewer(),
                draft.withWorldType(cycle(WorldGenType.values(), draft.worldType(), backward)));
    }

    private void cycleGenerator(MenuActionContext ctx, boolean backward) {
        WorldCreateDraft draft = draft(ctx);
        open(ctx.player(), ctx.viewer(), draft.withGenerator(nextGenerator(draft.generator(), backward)));
    }

    private void promptName(MenuActionContext ctx) {
        WorldCreateDraft draft = draft(ctx);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        InputRequest request = InputRequest.of(NAME_INPUT_KEY, WorldEditorMessageKey.CREATE_NAME_PROMPT);
        textInput.prompt(
                player,
                viewer,
                request,
                line -> applyName(player, viewer, draft, line),
                () -> open(player, viewer, draft));
    }

    /** Validate the typed name with the same shape {@link WorldName} enforces, then re-open with it; reject otherwise. */
    void applyName(Player player, PlayerRef viewer, WorldCreateDraft draft, String line) {
        String trimmed = line.trim();
        try {
            WorldName.of(trimmed);
            open(player, viewer, draft.withName(trimmed));
        } catch (IllegalArgumentException invalid) {
            notifier.send(viewer, WorldsMessageKey.WORLD_NAME_INVALID, Map.of("world", trimmed));
            open(player, viewer, draft);
        }
    }

    private void promptSeed(MenuActionContext ctx) {
        WorldCreateDraft draft = draft(ctx);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        InputRequest request = InputRequest.of(SEED_INPUT_KEY, WorldEditorMessageKey.CREATE_SEED_PROMPT);
        textInput.prompt(
                player,
                viewer,
                request,
                line -> applySeed(player, viewer, draft, line),
                () -> open(player, viewer, draft));
    }

    /** Parse the typed seed (empty clears it) and re-open with it; a non-number sends the existing rejection. */
    void applySeed(Player player, PlayerRef viewer, WorldCreateDraft draft, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            open(player, viewer, draft.withSeed(Optional.empty()));
            return;
        }
        try {
            open(player, viewer, draft.withSeed(Optional.of(Long.parseLong(trimmed))));
        } catch (NumberFormatException invalid) {
            notifier.send(viewer, WorldEditorMessageKey.CREATE_SEED_INVALID, Map.of("value", trimmed));
            open(player, viewer, draft);
        }
    }

    /**
     * Build the world from the draft on the global region thread (the only place a world handle is legal under Folia),
     * then re-open the list so the new world shows. A blank or malformed name rejects and re-opens unchanged.
     */
    private void create(MenuActionContext ctx) {
        WorldCreateDraft draft = draft(ctx);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        if (!draft.hasName()) {
            notifier.send(viewer, WorldEditorMessageKey.CREATE_NAME_REQUIRED, Map.of());
            open(player, viewer, draft);
            return;
        }
        WorldName name;
        try {
            name = WorldName.of(draft.name());
        } catch (IllegalArgumentException invalid) {
            notifier.send(viewer, WorldsMessageKey.WORLD_NAME_INVALID, Map.of("world", draft.name()));
            open(player, viewer, draft);
            return;
        }
        notifier.send(viewer, WorldsMessageKey.WORLD_CREATING, Map.of("world", name.value()));
        scheduler.onGlobal(() -> {
            createWorld.create(viewer, name, draft.toSpec(), true);
            reopenList.accept(player, viewer);
        });
    }

    /** The generator cycle: vanilla (null) -> void -> flat -> vanilla, matching the two built-ins the GUI offers. */
    private static @Nullable String nextGenerator(@Nullable String current, boolean backward) {
        List<String> order = Arrays.asList(null, BuiltInGenerators.VOID, BuiltInGenerators.FLAT);
        int step = backward ? -1 : 1;
        int next = Math.floorMod(order.indexOf(current) + step, order.size());
        return order.get(next);
    }

    private static <E extends Enum<E>> E cycle(E[] values, E current, boolean backward) {
        List<E> order = Arrays.asList(values);
        int step = backward ? -1 : 1;
        int next = Math.floorMod(order.indexOf(current) + step, order.size());
        return order.get(next);
    }

    private WorldCreateDraft draft(MenuContext ctx) {
        return ctx.subject(WorldCreateDraft.class);
    }

    private WorldCreateDraft draft(MenuActionContext ctx) {
        return ctx.subject(WorldCreateDraft.class);
    }
}
