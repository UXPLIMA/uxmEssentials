package com.uxplima.uxmessentials.regions.adapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.regions.adapter.inbound.command.RegionsCommand;
import com.uxplima.uxmessentials.regions.adapter.inbound.command.WorldEditRegionSelection;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionFlagEditorView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionListView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionRosterView;
import com.uxplima.uxmessentials.regions.adapter.outbound.NoWorldGuardRegionService;
import com.uxplima.uxmessentials.regions.adapter.outbound.WorldGuardRegionService;
import com.uxplima.uxmessentials.regions.application.RegionsConfig;
import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the regions context's adapters over the injected kernel ports and the shared menu engine, and produces
 * the {@code /regions} command. WorldGuard is a soft dependency, so the {@link RegionService} is chosen here by
 * {@link #regionService a plugin-present probe}: the reflective {@link WorldGuardRegionService} when WorldGuard is
 * installed, otherwise the {@link NoWorldGuardRegionService} no-op that reports "not available" and degrades the
 * command to a "WorldGuard not installed" reply. Either way the module registers exactly one permission-gated
 * command and no listener, and holds no runtime state, so there is nothing to tear down on stop.
 *
 * <p>The region-list panel is opened through the menu engine's paginated list, so the context creates no raw
 * inventory. Its three panels (the browser, the flag editor and the roster) read their geometry from
 * {@code modules/regions/gui/*.conf}, falling back to the code defaults below when a file is absent; the list page
 * size from the module config is what the shipped browser conf's content-slot count starts from.
 */
@NullMarked
public final class RegionsWiring {

    /** The folder the three panel layouts are read from: {@code modules/regions/gui/<name>.conf}. */
    private static final String MODULE = "regions";

    /** The full five content rows of a six-row chest; the nav row (slots 45..53) carries the prev/next buttons. */
    private static final int LIST_ROWS = 6;

    /** The bottom-row slot the flag editor's "members and owners" button sits in, clear of the nav arrows. */
    private static final int MEMBERS_BUTTON_SLOT = 53;

    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;

    /** Each region is drawn as, and the paginated-list fallback icon is, a sheet of paper. */
    private static final Material REGION_ICON = Material.PAPER;

    /** The browse gate {@code /regions} itself requires, reused for the hub entry that opens the same list. */
    private static final String LIST_PERMISSION = "uxmessentials.regions.list";

    /** The clicked-a-region-without-the-flags-permission refusal is gated on this node. */
    private static final String FLAGS_PERMISSION = "uxmessentials.regions.flags";

    /** Opening the roster editor from the detail panel's members button is gated on this node. */
    private static final String MEMBERS_PERMISSION = "uxmessentials.regions.members";

    private RegionsWiring() {}

    /** Build the regions adapters and the {@code /regions} command from the injected ports, menu engine and input seam. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            TextInput textInput,
            GuiLayouts guiLayouts) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        KernelPorts kernel = ctx.kernel();
        RegionsConfig config = RegionsConfig.from(ctx.config());
        GuiText guiText = new GuiText(kernel.messages());
        RegionService service = regionService(plugin.getServer(), kernel.log());
        RegionRosterView rosterView = new RegionRosterView(
                menus,
                guiText,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                service,
                kernel.playerLookup(),
                guiLayouts.loadEntityList(
                        MODULE, "region-roster", EntityListLayout.paginatedDefault(Material.PLAYER_HEAD)));
        RegionFlagEditorView flagEditor = new RegionFlagEditorView(
                menus,
                guiText,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                service,
                textInput::prompt,
                config.editableFlags(),
                guiLayouts.loadEntityList(
                        MODULE,
                        "region-flags",
                        EntityListLayout.paginatedDefault(REGION_ICON)
                                .withAction(MEMBERS_BUTTON_SLOT, Material.PLAYER_HEAD)),
                openRosterOnClick(kernel, rosterView));
        RegionListView listView = new RegionListView(
                menus,
                guiText,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                service,
                guiLayouts.loadEntityList(MODULE, "region-list", listLayout(config.listPageSize())),
                openEditorOnClick(kernel, flagEditor));
        RegionsCommand command = new RegionsCommand(
                service,
                listView,
                flagEditor,
                rosterView,
                new WorldEditRegionSelection(plugin.getServer()),
                kernel.playerLookup(),
                kernel.scheduler(),
                plugin.getServer(),
                kernel.messages());
        // The hub entry opens the browser for the world the viewer is standing in, the same screen a bare
        // /regions opens, and shares its WorldGuard-present gate.
        guiRegistry.register(new ManagementGuiEntry(
                "regions",
                RegionsMessageKey.REGIONS_GUI_TITLE,
                Material.BRICKS,
                LIST_PERMISSION,
                (player, viewer) -> command.openBrowser(player)));
        return new Wired(List.of(command), service);
    }

    /**
     * The list-click handler: a click on a region opens its flag editor, but only for a viewer holding the flags
     * permission, otherwise the same "no permission" line the {@code /regions flags} command would answer is sent, so
     * the editor's mutation surface is gated identically from the list and from the command.
     */
    private static BiConsumer<Player, RegionRef> openEditorOnClick(
            KernelPorts kernel, RegionFlagEditorView flagEditor) {
        return (player, region) -> {
            PlayerRef ref = BukkitRefs.toRef(player);
            if (!player.hasPermission(FLAGS_PERMISSION)) {
                kernel.messageSink()
                        .deliver(ref, kernel.messages().resolve(ref, SharedMessageKey.COMMAND_NO_PERMISSION, Map.of()));
                return;
            }
            flagEditor.open(ref, region);
        };
    }

    /**
     * The detail-panel members-button handler: it opens the roster editor for a region, but only for a viewer holding
     * the members permission, otherwise the same "no permission" line the {@code /regions members} command would
     * answer is sent, so the roster's mutation surface is gated identically from the panel and from the command.
     */
    private static BiConsumer<Player, RegionRef> openRosterOnClick(KernelPorts kernel, RegionRosterView rosterView) {
        return (player, region) -> {
            PlayerRef ref = BukkitRefs.toRef(player);
            if (!player.hasPermission(MEMBERS_PERMISSION)) {
                kernel.messageSink()
                        .deliver(ref, kernel.messages().resolve(ref, SharedMessageKey.COMMAND_NO_PERMISSION, Map.of()));
                return;
            }
            rosterView.open(ref, region);
        };
    }

    /**
     * The region-service seam bound for this run: the reflective WorldGuard implementation when the WorldGuard
     * plugin is installed, else the no-op fallback. The probe is the shared plugin-installed check, the same soft-dep
     * pattern the economy Vault/Treasury bridge uses, so no {@code com.sk89q} class loads on a server without
     * WorldGuard. Package-visible for the wiring probe test.
     */
    public static RegionService regionService(Server server, Logger log) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        if (WorldGuardReflection.isInstalled(server)) {
            return new WorldGuardRegionService(server, log);
        }
        return new NoWorldGuardRegionService();
    }

    /** A six-row paginated layout whose content region is the first {@code pageSize} slots (nav in the bottom row). */
    private static EntityListLayout listLayout(int pageSize) {
        List<Integer> content = IntStream.range(0, pageSize).boxed().toList();
        GuiLayout base = new GuiLayout(LIST_ROWS, REGION_ICON, Material.ARROW, PREV_SLOT, NEXT_SLOT, content);
        return new EntityListLayout(base, Material.BLACK_STAINED_GLASS_PANE, OptionalInt.empty(), REGION_ICON);
    }

    /**
     * Everything the regions module contributes once wired: the {@code /regions} command. The context registers no
     * listener and holds no runtime state (WorldGuard owns the region store), so there is no stop hook.
     *
     * @param commands the Brigadier command registrations to publish
     * @param service the region seam bound for this run, which the published region query reads through
     */
    public record Wired(List<CommandRegistration> commands, RegionService service) {
        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(service, "service");
        }
    }
}
