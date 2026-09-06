package com.uxplima.uxmessentials.kits.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.inbound.command.KitCommands;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitBrowseMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryParentSelectorMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySelectorMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCreatePrompt;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitSettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.KitsJoinListener;
import com.uxplima.uxmessentials.kits.adapter.outbound.BukkitKitActionRunner;
import com.uxplima.uxmessentials.kits.adapter.outbound.BukkitKitGranter;
import com.uxplima.uxmessentials.kits.adapter.outbound.ConfigurateKitCategoryRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.ConfigurateKitRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.FileKitStockStore;
import com.uxplima.uxmessentials.kits.adapter.outbound.PapiRequirementEvaluator;
import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitClaims;
import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitUnlocks;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.kits.application.port.KitActionRunner;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.application.port.KitStockStore;
import com.uxplima.uxmessentials.kits.application.port.KitUnlockStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the kits context's adapters and use cases over the injected kernel ports, the per-kit files
 * under {@code modules/kits/kits/}, and the PDC claim store, and produces the Brigadier command list the
 * plugin registers. This is the one place the kits context is wired: nothing else news up its classes.
 *
 * <p>The repository is the Configurate adapter over the per-kit files (read-on-load, write-through on
 * authoring; a legacy {@code kits.conf} monolith is split into per-kit files on first load). The claim store
 * and the granter are PDC- and inventory-backed Bukkit adapters; the shared
 * {@code Cooldowns} and {@code Permissions} kernel ports cover the cooldown and permission gates. The per-kit
 * cost soft-couples to the economy context: the {@link KitEconomy} seam is injected as an {@link Optional},
 * {@link Optional#empty()} when economy is disabled, so a priced kit's cost is recorded but not charged until
 * that bridge is wired.
 */
@NullMarked
public final class KitsWiring {

    private static final String LEGACY_KITS_FILE = "kits.conf";
    private static final String SHOWKIT_DISPLAY_KEY = "showkit-display";

    private KitsWiring() {}

    /** Build the kits adapters and use cases with no economy bridge (a recorded kit cost is not charged). */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        return wire(plugin, ctx, Optional.empty(), guiLayouts, textInput, menus, menuBindings);
    }

    /**
     * Build the kits context, charging a recorded per-kit cost through {@code economy} when present. The
     * economy context lands before kits in the registry, so its {@link KitEconomy} bridge is captured during
     * economy wiring and handed in here; when it is empty (economy disabled), a priced kit's cost is recorded
     * but not charged: the soft coupling the kits context owns.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Optional<KitEconomy> economy,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        Path dataFolder = plugin.getDataFolder().toPath();
        Path kitsDir = dataFolder.resolve("modules").resolve("kits").resolve("kits");
        Path legacy = dataFolder.resolve(LEGACY_KITS_FILE);
        KitRepository repository = ConfigurateKitRepository.load(kitsDir, legacy, kernel.log());
        Path categoriesDir = dataFolder.resolve("modules").resolve("kits").resolve("categories");
        KitCategoryRepository categoryRepository = ConfigurateKitCategoryRepository.load(categoriesDir, kernel.log());
        KitClaimStore claims = new PdcKitClaims(plugin);
        KitUnlockStore unlocks = new PdcKitUnlocks(plugin);
        KitGranter granter = new BukkitKitGranter(kernel.log());
        KitActionRunner actionRunner = new BukkitKitActionRunner(kernel.scheduler(), kernel.log());
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        GuiLayout menuLayout = guiLayouts.load("kits", "kits-menu", GuiLayout.paginatedDefault(Material.CHEST));
        GuiLayout previewLayout = guiLayouts.load(
                "kits",
                "kits-preview",
                new GuiLayout(6, Material.GRAY_STAINED_GLASS_PANE, Material.ARROW, 0, 1, List.of()));

        // The per-kit settings panel now renders through the menu engine: a declarative spec over the edited kit as
        // its subject. The manager opens it on a kit click, the create flow opens it on a fresh kit, and the
        // kit→category selector reopens it after a pick; each shares this one instance. It closes a small cycle with
        // the category selector (settings opens the selector, the selector reopens settings), broken through the
        // bind(...) setter once both exist. The kit editor and the delete use case it drives are shared with
        // assemble() below, so both are built here once.
        GuiText guiText = new GuiText(kernel.messages());
        KitEditor kitEditor = new KitEditor(repository, notifier);
        DelKit delKit = new DelKit(repository, notifier);
        KitEditorView kitEditorView = new KitEditorView(kernel.messages(), kitEditor, kernel.scheduler());
        KitManagerMenu[] managerHolder = new KitManagerMenu[1];
        KitSettingsView settingsView = new KitSettingsView(
                menus,
                guiText,
                kernel.messages(),
                textInput,
                kitEditor,
                delKit,
                kitEditorView,
                (player, viewer) -> managerHolder[0].open(player, viewer));
        settingsView.register(menuBindings, dataFolder, kernel.log());
        KitCategoryManagerMenu categoryManagerView =
                new KitCategoryManagerMenu(menus, kernel.messages(), kernel.scheduler(), categoryRepository, textInput);
        categoryManagerView.register(menuBindings, dataFolder, kernel.log());
        KitCategorySettingsView categorySettingsView = new KitCategorySettingsView(
                menus,
                guiText,
                kernel.messages(),
                textInput,
                categoryRepository,
                (player, viewer) -> categoryManagerView.open(player, viewer));
        categorySettingsView.register(menuBindings, dataFolder, kernel.log());
        KitCategoryParentSelectorMenu categoryParentSelectorView = new KitCategoryParentSelectorMenu(
                menus, kernel.messages(), kernel.scheduler(), categoryRepository, categorySettingsView);
        categoryParentSelectorView.register(menuBindings, dataFolder, kernel.log());
        categorySettingsView.bind(categoryParentSelectorView);
        categoryManagerView.bind(categorySettingsView, (player, viewer) -> managerHolder[0].open(player, viewer));
        KitCreatePrompt createPrompt = new KitCreatePrompt(
                kernel.messages(), textInput, new CreateKit(repository, notifier), repository, settingsView);
        KitManagerMenu kitManager = new KitManagerMenu(
                menus,
                kernel.scheduler(),
                repository,
                kernel.messages(),
                settingsView,
                categoryManagerView,
                createPrompt.boundTo((player, viewer) -> managerHolder[0].open(player, viewer)));
        managerHolder[0] = kitManager;
        kitManager.register(menuBindings, dataFolder, kernel.log());
        // The kit to category selector renders from its own spec, so it takes the engine façade, the message catalog,
        // and the kit editor its pick saves through, plus the kit settings view it reopens once done.
        KitCategorySelectorMenu categorySelectorView = new KitCategorySelectorMenu(
                menus, kernel.messages(), kernel.scheduler(), categoryRepository, kitEditor, settingsView);
        categorySelectorView.register(menuBindings, dataFolder, kernel.log());
        settingsView.bind(categorySelectorView);

        // The placeholder requirement evaluator soft-couples to PlaceholderAPI exactly like the economy bridge:
        // present only when PlaceholderAPI is installed, otherwise empty, in which case a kit that declares
        // requirements fails closed (KitAccess cannot check its conditions, so it cannot be claimed).
        Optional<com.uxplima.uxmessentials.kits.application.port.RequirementEvaluator> requirements =
                PapiRequirementEvaluator.isPresent() ? Optional.of(new PapiRequirementEvaluator()) : Optional.empty();
        Path stockFile = dataFolder.resolve("modules").resolve("kits").resolve("stock.properties");
        KitStockStore stockStore = new FileKitStockStore(kernel.scheduler(), kernel.log(), stockFile);
        KitAccess access = new KitAccess(
                kernel.permissions(),
                kernel.cooldowns(),
                claims,
                economy,
                requirements,
                Optional.of(stockStore),
                Optional.of(unlocks));
        KitServices services = assemble(
                kernel,
                repository,
                categoryRepository,
                access,
                claims,
                granter,
                actionRunner,
                notifier,
                economy,
                kitEditor,
                delKit,
                kitEditorView,
                menuLayout,
                previewLayout,
                kitManager,
                categoryManagerView,
                categorySettingsView,
                categorySelectorView,
                categoryParentSelectorView,
                menus,
                menuBindings,
                dataFolder);

        List<CommandRegistration> commands = KitCommands.all(
                services,
                kernel.messages(),
                () -> ListDisplayMode.from(ctx.config()),
                () -> ListDisplayMode.from(ctx.config(), SHOWKIT_DISPLAY_KEY),
                kernel.scheduler());

        List<Listener> listeners = List.of(
                new KitPreviewListener(),
                new KitEditorListener(services.kitEditorView()),
                new KitsJoinListener(repository, granter, access));

        // The kit's claim/deny effects (sound, particles, title, firework, commands, the wait-ticks delay) now run
        // through the KitActionRunner inside ClaimKit, ordered around the item grant, so there is no longer a
        // KitClaimed event subscriber to run them reactively after the fact, and none to unsubscribe on stop.

        return new Wired(
                commands,
                listeners,
                services.kitEditorView(),
                repository,
                access,
                services.listKits(),
                services.kitMenu(),
                () -> {},
                granter,
                services.claimKit());
    }

    private static KitServices assemble(
            KernelPorts kernel,
            KitRepository repository,
            KitCategoryRepository categoryRepository,
            KitAccess access,
            KitClaimStore claims,
            KitGranter granter,
            KitActionRunner actionRunner,
            Notifier notifier,
            Optional<KitEconomy> economy,
            KitEditor kitEditor,
            DelKit delKit,
            KitEditorView kitEditorView,
            GuiLayout menuLayout,
            GuiLayout previewLayout,
            KitManagerMenu kitManager,
            KitCategoryManagerMenu categoryManagerView,
            KitCategorySettingsView categorySettingsView,
            KitCategorySelectorMenu categorySelectorView,
            KitCategoryParentSelectorMenu categoryParentSelectorView,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder) {
        // Server-local zone so kit schedules (and the matching browse-menu lock state) read in the time an
        // operator authors a window in; the event timestamp uses clock.instant(), which is zone-independent.
        Clock clock = Clock.system(ZoneId.systemDefault());
        ClaimKit claimKit = new ClaimKit(
                repository,
                access,
                granter,
                notifier,
                kernel.events(),
                clock,
                economy,
                Optional.of(actionRunner),
                kernel.gate());
        KitPreviewView kitPreview = new KitPreviewView(kernel.messages(), kernel.scheduler(), previewLayout);
        // The read-only /kit browse menu now renders through the always-on menu engine. It claims through the same
        // ClaimKit path the command drives and resolves its tiles off the warm kit/category sets, so the engine
        // renders without a port read of its own; a category configured to a content slot pins to it on every page.
        KitBrowseMenu kitMenu = new KitBrowseMenu(
                menus,
                kernel.scheduler(),
                claimKit,
                notifier,
                categoryRepository,
                access,
                kitPreview,
                kernel.messages(),
                menuLayout,
                clock);
        kitMenu.register(menuBindings, dataFolder, kernel.log());
        return new KitServices(
                claimKit,
                new ListKits(repository, kernel.permissions(), claims, notifier),
                new ShowKit(repository, notifier),
                new CreateKit(repository, notifier),
                delKit,
                kitEditor,
                new KitReset(repository, claims, notifier),
                kitMenu,
                kitPreview,
                kitEditorView,
                kitManager,
                kernel.playerLookup(),
                categoryManagerView,
                categorySettingsView,
                categorySelectorView,
                categoryParentSelectorView);
    }

    /**
     * Everything the kits module contributes once wired: the Brigadier commands, the Bukkit listeners (the
     * read-only {@code /kit show} preview guard and the {@code /kit editor} window's save-on-close handler), the
     * {@link KitEditorView} held so {@link #stop()} can flush every still-open editor on disable, plus the
     * {@link KitRepository} the {@code kit_*} placeholders resolve a kit's catalog facts against, plus the
     * {@link KitAccess} gate and the {@link ListKits} filter those placeholders read availability and the
     * usable-kit list through. The kit catalog is config-backed, so the only durable-while-open state is the
     * set of open editor windows.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param kitEditorView the editor window, held so {@code stop()} flushes every still-open edit
     * @param repository the kit catalog the cost/cooldown placeholders read
     * @param access the claim gate the availability/permission/claims-left placeholders read
     * @param listKits the {@code /kit list} filter the usable-kit-list placeholder reads
     * @param kitMenu the browse menu the {@code /kit} command and the management hub both open
     * @param stopAction extra teardown to run on module stop
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            KitEditorView kitEditorView,
            KitRepository repository,
            KitAccess access,
            ListKits listKits,
            KitBrowseMenu kitMenu,
            Runnable stopAction,
            KitGranter granter,
            ClaimKit claimKit) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(kitEditorView, "kitEditorView");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(access, "access");
            Objects.requireNonNull(listKits, "listKits");
            Objects.requireNonNull(kitMenu, "kitMenu");
            Objects.requireNonNull(stopAction, "stopAction");
            Objects.requireNonNull(granter, "granter");
            Objects.requireNonNull(claimKit, "claimKit");
        }

        /**
         * Save every still-open {@code /kit editor} window back to its kit. Called on module stop.
         */
        public void stop() {
            kitEditorView.flushAll();
            stopAction.run();
        }
    }
}
