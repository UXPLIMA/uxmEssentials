package com.uxplima.uxmessentials.itemworld.adapter;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGroupACommands;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGroupBCommands;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGuiCommand;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.EntityCountMenu;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ItemEditView;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ItemworldHubMenu;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.RecipeGridMenu;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ShulkerBoxListener;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ShulkerBoxView;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.listener.PowertoolInteractListener;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.listener.UnlimitedPlacementListener;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.LoggingItemworldAudit;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PowertoolToggleStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.StoreItemworldPlaceholders;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.UnlimitedPlacementStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.PowertoolPolicy;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ItemworldPlaceholders;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/**
 * Constructs the itemworld context's adapters over the injected kernel ports and produces everything the plugin
 * must register: the full Brigadier command list (groups A and B, the ~40-verb surface) and the powertool +
 * unlimited-placement listeners. This is the one place the itemworld context is wired. Nothing else news up
 * its classes (the wiring invariant, docs/10-feature-modules.md §5).
 *
 * <p>itemworld is stateless and ACL-thin: it needs no database, only the {@link KernelPorts} the commands
 * schedule and render through, the {@link ItemworldConfig} view of {@code itemworld.conf} (the sub-feature-group
 * + per-command disable gate, the {@code /give}//{@code /more} caps, the enchant-level clamp, the audit
 * toggles), and the {@link ItemworldAudit} on the dedicated {@code com.uxplima.uxmessentials.audit} channel
 * (mirroring {@code LoggingModerationAudit}). The two stateful corners. Powertool bindings (item PDC) and the
 * powertool / unlimited per-player toggles. Are transient runtime state dropped with the wiring on module stop;
 * the {@code Plugin} handle is used only to mint the powertool PDC {@code NamespacedKey} once.
 */
@NullMarked
public final class ItemworldWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";
    private static final int DISPOSAL_ROWS = 6;

    private ItemworldWiring() {}

    /** Build the itemworld adapters from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        ItemworldConfig config = ItemworldConfig.from(ctx.config());
        ItemworldAudit audit = new LoggingItemworldAudit(auditLogger(), config);
        GuiLayout disposalLayout = guiLayouts.load("itemworld", "disposal", GuiLayout.storageDefault(DISPOSAL_ROWS));
        ItemworldServices services = new ItemworldServices(kernel, audit, config, disposalLayout);

        PowertoolPolicy powertoolPolicy = new PowertoolPolicy();
        PurgePolicy purgePolicy = new PurgePolicy(config);
        PdcPowertoolStore powertoolStore = new PdcPowertoolStore(plugin);
        PowertoolToggleStore powertoolToggles = new PowertoolToggleStore();
        UnlimitedPlacementStore unlimited = new UnlimitedPlacementStore();

        // The utilities hub, the /entitycount grid, and the /recipe grid all render through the shared menu engine;
        // each registers its bindings and specs once here. A hub button runs the same surface a command does (open a
        // workstation, set time/weather, sweep drops/mobs) and is drawn only when the viewer holds that command's
        // permission (the engine's perm view-condition); /itemworld gui and the /uxmess gui hub both open it. No new
        // domain logic: only the surfaces the commands expose.
        java.nio.file.Path dataFolder = plugin.getDataFolder().toPath();
        ItemworldHubMenu hubView =
                new ItemworldHubMenu(menus, kernel.messages(), kernel.scheduler(), services, purgePolicy);
        hubView.register(menuBindings, dataFolder, kernel.log());
        EntityCountMenu entityCountView = new EntityCountMenu(menus);
        entityCountView.register(menuBindings, dataFolder, kernel.log());
        RecipeGridMenu recipeView = new RecipeGridMenu(menus, kernel.messages(), Material.BLACK_STAINED_GLASS_PANE);
        recipeView.register(menuBindings, dataFolder, kernel.log());
        // The click-driven /itemedit editor: a bare /itemedit opens it on the held item, editing through the same
        // shared item-edit path the /itemedit subcommands use. Registers its preview placeholders and per-button
        // actions here, then the ItemEditCommand hands it the opener.
        ItemEditView itemEditView = new ItemEditView(menus, services, textInput);
        itemEditView.register(menuBindings, dataFolder, kernel.log());

        List<CommandRegistration> commands =
                new java.util.ArrayList<>(ItemworldGroupACommands.all(services, recipeView, itemEditView));
        commands.addAll(ItemworldGroupBCommands.all(
                services, powertoolPolicy, purgePolicy, powertoolStore, powertoolToggles, unlimited, entityCountView));
        commands.add(new ItemworldGuiCommand(hubView, kernel.messages(), kernel.messageSink()));

        // The in-inventory shulker opener: a sanctioned raw-inventory leaf (view + holder + listener) that opens a
        // shulker box the player right-clicks in hand and writes the edits back into the box item on close.
        ShulkerBoxView shulkerView = new ShulkerBoxView(kernel.messages(), kernel.scheduler());
        List<Listener> listeners = List.of(
                new PowertoolInteractListener(powertoolStore, powertoolToggles, config),
                new UnlimitedPlacementListener(unlimited, config),
                new ShulkerBoxListener(shulkerView, config));
        return new Wired(
                List.copyOf(commands),
                listeners,
                hubView,
                shulkerView,
                powertoolStore,
                new StoreItemworldPlaceholders(plugin.getServer(), powertoolStore, powertoolToggles, unlimited));
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * Everything the itemworld module contributes once wired: the full Brigadier command list and the powertool
     * + unlimited-placement listeners. The context holds no repeating scheduled work and no persistence; its
     * only runtime state is the powertool/unlimited toggle maps and the item-PDC bindings, which are
     * garbage-collected with the wiring on module stop: there is nothing to drain or flush.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the powertool interact, unlimited-placement, and shulker-open listeners to register
     * @param hubView the utilities hub the {@code /uxmess gui} hub entry opens
     * @param shulkerView the in-inventory shulker view, exposed so its still-open windows are flushed on module stop
     * @param powertools the item-PDC powertool store, exposed so the published query reads the same bindings
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            ItemworldHubMenu hubView,
            ShulkerBoxView shulkerView,
            PdcPowertoolStore powertools,
            ItemworldPlaceholders placeholders) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(hubView, "hubView");
            Objects.requireNonNull(shulkerView, "shulkerView");
            Objects.requireNonNull(powertools, "powertools");
            Objects.requireNonNull(placeholders, "placeholders");
        }
    }
}
