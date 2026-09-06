package com.uxplima.uxmessentials.homes.adapter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.bootstrap.di.CloseableResources;
import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeCommands;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionMenu;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeInvitesMenu;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListMenu;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenus;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.listener.HomesJoinListener;
import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.adapter.outbound.TeleportHomeAdapter;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeApiWrites;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeCharge;
import com.uxplima.uxmessentials.homes.application.HomeChargeSettings;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.ListHomeInvites;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.SetHomeVisibility;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.application.VisitHome;
import com.uxplima.uxmessentials.homes.application.WorldBlacklistGuard;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.HomeSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProvidersConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimServiceImpl;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.claim.ClaimPolicySettings;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the homes context's adapters, use cases, and slot-grid views over the injected kernel ports, the
 * persistence DSL, and the teleport context's engine, and produces the Brigadier command list the plugin
 * registers. This is the one place the homes context is wired: nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the delegate,
 * invalidate in the cache) wrapped by {@link HomeSync} so a local write announces itself across servers. The
 * teleporter delegates execution to the teleport context, homes never re-implements movement, which is why the
 * wiring receives the already-constructed {@link TeleportEngine}. Text prompts (home rename, invite add) go through
 * the shared {@link TextInput} seam installed once in bootstrap, so homes installs no input machinery of its own.
 */
@NullMarked
public final class HomesWiring {

    private static final int DEFAULT_HOME_LIMIT = 3;
    private static final int DEFAULT_UNLIMITED_MAX = 1000;
    private static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy HH:mm";
    private static final int DEFAULT_MIDAIR_GROUND_DEPTH = 5;

    private HomesWiring() {}

    /**
     * Build the homes adapters, use cases, and views from {@code ctx}, the persistence DSL, and the engine,
     * with no economy bridge (a configured home cost is recorded but not charged).
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Bus bus,
            GuiLayouts guiLayouts,
            CloseableResources resources,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            ClaimProvidersConfig claimProviders) {
        return wire(
                plugin,
                ctx,
                persistence,
                teleportEngine,
                Optional.empty(),
                bus,
                guiLayouts,
                resources,
                textInput,
                menus,
                menuBindings,
                claimProviders);
    }

    /**
     * Build the homes context, charging a configured per-action cost through {@code homeEconomy} when
     * present. The economy context lands before homes in the registry, so its {@link HomeEconomy} bridge is
     * captured during economy wiring and handed in here; when it is empty (economy disabled or
     * {@code economy.enabled = false} in homes config), a configured cost is recorded but not charged.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Optional<HomeEconomy> homeEconomy,
            Bus bus,
            GuiLayouts guiLayouts,
            CloseableResources resources,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            ClaimProvidersConfig claimProviders) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(homeEconomy, "homeEconomy");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(claimProviders, "claimProviders");
        KernelPorts kernel = ctx.kernel();
        CachedHomeRepository cached = HomeRepositories.cachedConcrete(persistence);
        bus.registry().register(HomeSync.listener(cached));
        HomeRepository repository = HomeSync.repository(cached, bus.publisher());
        HomeInviteRepository invites = HomeRepositories.homeInviteRepository(persistence);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        HomeQuota quota = new HomeQuota(kernel.permissions(), defaultLimit(ctx), limitMode(ctx));
        HomeTeleporter teleporter = new TeleportHomeAdapter(teleportEngine);
        HomeServices services = assemble(
                plugin,
                ctx,
                repository,
                invites,
                notifier,
                quota,
                teleporter,
                homeEconomy,
                guiLayouts,
                textInput,
                menus,
                menuBindings,
                claimProviders);
        HomesJoinListener joinWarmer = new HomesJoinListener(repository, kernel.scheduler());
        return new Wired(
                HomeCommands.all(services, kernel.messages(), kernel.scheduler()),
                List.of(joinWarmer),
                repository,
                quota,
                services.homeList(),
                services.apiWrites());
    }

    private static HomeServices assemble(
            Plugin plugin,
            ModuleContext ctx,
            HomeRepository repository,
            HomeInviteRepository invites,
            Notifier notifier,
            HomeQuota quota,
            HomeTeleporter teleporter,
            Optional<HomeEconomy> homeEconomy,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            ClaimProvidersConfig claimProviders) {
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        int unlimitedMax = unlimitedMax(ctx);
        DateTimeFormatter dateFormat = dateFormat(ctx);

        SafeLocationGuard safeGuard = buildSafeGuard(plugin, ctx);
        ClaimService claimService = buildClaimService(plugin, ctx, kernel, claimProviders);
        List<SethomeGuard> guards = buildGuards(ctx);
        HomeCharge charge = buildCharge(ctx, kernel, homeEconomy);
        CreateHomeAtSlot createHome = new CreateHomeAtSlot(
                repository,
                invites,
                quota,
                guards,
                notifier,
                kernel.events(),
                kernel.gate(),
                charge,
                unlimitedMax,
                clock);
        RelocateHome relocateHome =
                new RelocateHome(repository, guards, notifier, kernel.events(), kernel.gate(), charge, clock);
        RenameHome renameHome = new RenameHome(repository, notifier, kernel.events(), clock);
        SetHomeIcon setHomeIcon = new SetHomeIcon(repository, notifier, kernel.events(), clock);
        SetHomeVisibility setHomeVisibility = new SetHomeVisibility(repository, notifier, kernel.events(), clock);
        DeleteHome deleteHome = new DeleteHome(repository, invites, notifier, kernel.events(), kernel.gate());
        // The published API runs the same use cases with one collaborator swapped: a charge gate with no economy
        // behind it, so a plugin setting a home for a player never takes that player's money.
        HomeCharge freeCharge = new HomeCharge(kernel.permissions(), Optional.empty(), HomeChargeSettings.allFree());
        HomeApiWrites apiWrites = new HomeApiWrites(
                new CreateHomeAtSlot(
                        repository,
                        invites,
                        quota,
                        guards,
                        notifier,
                        kernel.events(),
                        kernel.gate(),
                        freeCharge,
                        unlimitedMax,
                        clock),
                new RelocateHome(repository, guards, notifier, kernel.events(), kernel.gate(), freeCharge, clock),
                renameHome,
                deleteHome);
        TeleportHome teleportHome = new TeleportHome(repository, teleporter, notifier, charge);
        ListHomes listHomes = new ListHomes(repository);
        ListHomeInvites listHomeInvites = new ListHomeInvites(invites);
        InviteToHome inviteToHome = new InviteToHome(repository, invites, notifier);
        UninviteFromHome uninviteFromHome = new UninviteFromHome(invites, notifier);
        VisitHome visitHome = new VisitHome(repository, invites, teleporter, notifier);

        boolean confirmDelete = ctx.config().getBoolean("confirm-delete", true);
        boolean confirmRelocate = ctx.config().getBoolean("confirm-relocate", false);
        boolean confirmUnsafeTeleport = ctx.config().getBoolean("confirm-unsafe-teleport", true);

        // The grid opens the action menu; the action menu's change-icon and invites buttons open the icon picker and
        // invited-players list, which in turn re-open the action menu; the action menu's back / post-delete flow
        // re-opens the grid. These three references form a cycle, so the three children are bound after construction
        // through holders.
        HomeListMenu[] listHolder = new HomeListMenu[1];
        HomeMenus[] iconHolder = new HomeMenus[1];
        HomeInvitesMenu[] invitesHolder = new HomeInvitesMenu[1];
        HomeActionMenu actionMenu = buildActionMenu(
                menus,
                kernel,
                notifier,
                teleportHome,
                deleteHome,
                relocateHome,
                renameHome,
                setHomeVisibility,
                repository,
                textInput,
                dateFormat,
                confirmDelete,
                confirmRelocate,
                confirmUnsafeTeleport,
                safeGuard,
                claimService,
                (viewer, home) -> iconHolder[0].openIcons(viewer, home),
                (viewer, home) -> invitesHolder[0].open(viewer, home),
                listHolder);
        HomeMenus.ActionMenuOpener reopenAction = (player, viewer, home) -> actionMenu.open(viewer, home);
        HomeMenus homeMenus =
                new HomeMenus(menus, kernel.scheduler(), setHomeIcon, iconLayout(guiLayouts), reopenAction);
        homeMenus.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        iconHolder[0] = homeMenus;
        HomeInvitesMenu invitesMenu = new HomeInvitesMenu(
                menus,
                kernel.scheduler(),
                kernel.messages(),
                listHomeInvites,
                inviteToHome,
                uninviteFromHome,
                kernel.playerLookup(),
                notifier,
                textInput,
                reopenAction);
        invitesMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        invitesHolder[0] = invitesMenu;
        actionMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        HomeListMenu listView = new HomeListMenu(
                menus,
                kernel.messages(),
                notifier,
                kernel.permissions(),
                kernel.scheduler(),
                listHomes,
                quota,
                createHome,
                safeGuard,
                claimService,
                listLayout(guiLayouts),
                unlimitedMax,
                dateFormat,
                actionMenu::open);
        listView.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        listHolder[0] = listView;
        HomeAdmin homeAdmin = new HomeAdmin(repository, invites, teleporter, notifier, kernel.events(), clock);
        return new HomeServices(
                listView,
                homeAdmin,
                visitHome,
                inviteToHome,
                uninviteFromHome,
                kernel.playerLookup(),
                repository,
                apiWrites);
    }

    /**
     * Assemble the engine action menu's collaborators. The icon-picker and invites openers are bound after this
     * returns (they reopen the action menu), so they are passed as the menu's own {@code open} re-entered through the
     * shared reopener; the change-icon / invites click bindings are registered on this menu and call the icon picker
     * and invited-players list directly through the seams the caller wires up.
     */
    private static HomeActionMenu buildActionMenu(
            Menus menus,
            KernelPorts kernel,
            Notifier notifier,
            TeleportHome teleportHome,
            DeleteHome deleteHome,
            RelocateHome relocateHome,
            RenameHome renameHome,
            SetHomeVisibility setHomeVisibility,
            HomeRepository repository,
            TextInput textInput,
            DateTimeFormatter dateFormat,
            boolean confirmDelete,
            boolean confirmRelocate,
            boolean confirmUnsafeTeleport,
            SafeLocationGuard safeGuard,
            ClaimService claimService,
            java.util.function.BiConsumer<
                            com.uxplima.uxmessentials.shared.domain.PlayerRef,
                            com.uxplima.uxmessentials.homes.domain.Home>
                    openIconPicker,
            java.util.function.BiConsumer<
                            com.uxplima.uxmessentials.shared.domain.PlayerRef,
                            com.uxplima.uxmessentials.homes.domain.Home>
                    openInvites,
            HomeListMenu[] listHolder) {
        HomeActionMenu.Collaborators collaborators = new HomeActionMenu.Collaborators(
                menus,
                kernel.messages(),
                notifier,
                kernel.permissions(),
                kernel.scheduler(),
                teleportHome,
                deleteHome,
                relocateHome,
                renameHome,
                setHomeVisibility,
                repository,
                textInput,
                dateFormat,
                confirmDelete,
                confirmRelocate,
                confirmUnsafeTeleport,
                safeGuard.blockUnsafe(),
                (Position pos) -> safeGuard.isUnsafe(pos),
                claimService,
                openIconPicker,
                openInvites,
                player -> listHolder[0].open(BukkitRefs.toRef(player)));
        return new HomeActionMenu(collaborators);
    }

    private static HomeListLayout listLayout(GuiLayouts guiLayouts) {
        return guiLayouts.loadHomeList("homes", "home-list", HomeListLayout.codeDefault());
    }

    private static IconSelectorLayout iconLayout(GuiLayouts guiLayouts) {
        return guiLayouts.loadIconSelector("homes", "icon-selector", IconSelectorLayout.codeDefault());
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_HOME_LIMIT));
    }

    private static QuotaReduction limitMode(ModuleContext ctx) {
        String raw = ctx.config().getString("limit-mode", "highest");
        return "stack".equalsIgnoreCase(raw) ? QuotaReduction.STACK : QuotaReduction.MAX;
    }

    private static int unlimitedMax(ModuleContext ctx) {
        return Math.max(1, ctx.config().getInt("unlimited-max", DEFAULT_UNLIMITED_MAX));
    }

    private static DateTimeFormatter dateFormat(ModuleContext ctx) {
        String pattern = ctx.config().getString("date-format", DEFAULT_DATE_FORMAT);
        return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
    }

    private static ClaimService buildClaimService(
            Plugin plugin, ModuleContext ctx, KernelPorts kernel, ClaimProvidersConfig claimProviders) {
        boolean enabled = ctx.config().getBoolean("claims.enabled", true);
        if (!enabled) {
            return new AlwaysAllowClaimService();
        }
        boolean requireClaim = ctx.config().getBoolean("claims.require-claim", false);
        boolean blockForeignClaims = ctx.config().getBoolean("claims.block-foreign-claims", true);
        int foreignChunkDistance = Math.max(0, ctx.config().getInt("claims.foreign-claim-chunk-distance", 0));
        boolean checkTeleportAccess = ctx.config().getBoolean("claims.check-teleport-access", true);
        ClaimPolicySettings settings =
                new ClaimPolicySettings(requireClaim, blockForeignClaims, foreignChunkDistance, checkTeleportAccess);
        return new ClaimServiceImpl(
                ClaimProviders.detectAll(claimProviders, plugin, plugin.getServer(), kernel.log()), settings);
    }

    private static SafeLocationGuard buildSafeGuard(Plugin plugin, ModuleContext ctx) {
        boolean blockUnsafe = ctx.config().getBoolean("block-unsafe-sethome", true);
        boolean considerMidair = ctx.config().getBoolean("consider-midair-unsafe", true);
        int midairDepth = Math.max(1, ctx.config().getInt("midair-ground-depth", DEFAULT_MIDAIR_GROUND_DEPTH));
        return new SafeLocationGuard(plugin.getServer(), blockUnsafe, considerMidair, midairDepth);
    }

    private static List<SethomeGuard> buildGuards(ModuleContext ctx) {
        // Only the pure, Bukkit-free guard runs inside the use cases. They execute async, where a block
        // read is illegal. The block-reading SafeLocationGuard is invoked by the views on the region thread.
        Set<String> disabledWorlds = new HashSet<>(ctx.config().getStringList("disabled-worlds", List.of()));
        return List.of(new WorldBlacklistGuard(disabledWorlds));
    }

    private static HomeCharge buildCharge(ModuleContext ctx, KernelPorts kernel, Optional<HomeEconomy> homeEconomy) {
        boolean economyEnabled = ctx.config().getBoolean("economy.enabled", false);
        if (!economyEnabled || homeEconomy.isEmpty()) {
            // Economy disabled in config or no provider wired: all actions are free.
            return new HomeCharge(kernel.permissions(), Optional.empty(), HomeChargeSettings.allFree());
        }
        String currency = ctx.config().getString("economy.currency", "default");
        HomeCost createCost = toCost(ctx.config().getDouble("economy.create-cost", 0), currency);
        HomeCost relocateCost = toCost(ctx.config().getDouble("economy.relocate-cost", 0), currency);
        HomeCost teleportCost = toCost(ctx.config().getDouble("economy.teleport-cost", 0), currency);
        HomeChargeSettings settings = new HomeChargeSettings(createCost, relocateCost, teleportCost);
        return new HomeCharge(kernel.permissions(), homeEconomy, settings);
    }

    private static HomeCost toCost(double raw, String currency) {
        BigDecimal amount = BigDecimal.valueOf(raw);
        return amount.signum() > 0 ? HomeCost.of(amount, currency) : HomeCost.free();
    }

    /**
     * Everything the homes module contributes once wired: the Brigadier commands plus the read seams the
     * PlaceholderAPI expansion queries. The homes context holds no repeating scheduled work and installs no
     * input machinery of its own (text prompts go through the shared seam), so there is nothing to drain on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join cache-warmer the plugin registers
     * @param repository the home store the {@code homes_count} placeholder reads
     * @param quota the home-limit reducer the {@code homes_limit}/{@code homes_left} placeholders read
     * @param listView the slot-grid home list the {@code /home} command and the management hub both open
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            HomeRepository repository,
            HomeQuota quota,
            HomeListMenu listView,
            HomeApiWrites apiWrites) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(quota, "quota");
            Objects.requireNonNull(listView, "listView");
            Objects.requireNonNull(apiWrites, "apiWrites");
        }
    }
}
