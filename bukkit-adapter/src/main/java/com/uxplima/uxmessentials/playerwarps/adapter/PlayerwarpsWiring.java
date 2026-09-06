package com.uxplima.uxmessentials.playerwarps.adapter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.persistence.messaging.MessagingStores;
import com.uxplima.uxmessentials.persistence.playerwarps.JooqPlayerWarpEconomy;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommands;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpBrowseMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpCategoriesMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorSubLayouts;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpIconMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpManageMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpPeopleMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpViewMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.listener.PlayerwarpsJoinListener;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.BukkitCrossServerTeleport;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.BukkitRatingRewardGranter;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.MailRentMailer;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.PlayerWarpLegacyMigration;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.ReceiptedPlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.RentSweep;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.SponsorExpirySweep;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.TeleportPlayerWarpAdapter;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.BuySponsorship;
import com.uxplima.uxmessentials.playerwarps.application.CrossServerArrival;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.FavouritePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.ManageBans;
import com.uxplima.uxmessentials.playerwarps.application.ManageMembers;
import com.uxplima.uxmessentials.playerwarps.application.ManageWhitelist;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.RatePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.RatingRewardConfig;
import com.uxplima.uxmessentials.playerwarps.application.RatingRewards;
import com.uxplima.uxmessentials.playerwarps.application.RentConfig;
import com.uxplima.uxmessentials.playerwarps.application.RentPolicy;
import com.uxplima.uxmessentials.playerwarps.application.RentReminders;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.SettleRent;
import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.TransferPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.application.WithdrawEarnings;
import com.uxplima.uxmessentials.playerwarps.application.port.CrossServerTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingRewardStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BayesianRating;
import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the player-warps context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the teleport context's engine, and produces the Brigadier command list the plugin registers. This
 * is the one place the player-warps context is wired: nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator keyed by owner (write-through
 * at the delegate, invalidate in the cache). The teleporter delegates execution to the teleport context
 * player-warps never re-implements movement, which is why the wiring receives the already-constructed
 * {@link TeleportEngine}. The per-owner count limit resolves through {@link PlayerWarpQuota} over the shared
 * {@code Permissions} reducer with the module's {@code default-limit} config value as the fallback.
 */
@NullMarked
public final class PlayerwarpsWiring {

    private static final int DEFAULT_LIMIT = 3;
    private static final int DEFAULT_CONFIDENCE = 10;

    private PlayerwarpsWiring() {}

    /**
     * Build the player-warps adapters and use cases over the kernel ports and the teleport engine. The warp
     * arrival-notification registry is shared from the warps module so a player-warp hop fires the same welcome
     * effects; when warps is disabled it is {@code null} and player-warps falls back to a private throwaway
     * registry (no listener consumes it, exactly as before this was injected).
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus bus,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpRepositoryHandle
                    playerWarpHandle,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpGoToHandle
                    playerWarpGoTo,
            com.uxplima.uxmessentials.warps.adapter.@org.jspecify.annotations.Nullable WarpTeleportRegistry
                    teleportRegistry,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings,
            @org.jspecify.annotations.Nullable EconomyProvider economyProvider,
            @org.jspecify.annotations.Nullable CurrencyRegistry economyCurrencies,
            @org.jspecify.annotations.Nullable CurrencyBackendRegistry economyBackends) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        // Handed back on Wired rather than run here: it needs the world list, and at load: STARTUP the wiring
        // thread has none. Bootstrap releases it through WorldPhase once the worlds are up.
        Runnable legacyMigration = new PlayerWarpLegacyMigration(
                plugin, persistence, kernel, bus.publisher().serverId())::run;
        // The concrete cache is what the cross-server listener invalidates per owner; the broadcasting decorator
        // wraps that same cache so a local write announces it to peers (the homes seam, copied for player-warps).
        com.uxplima.uxmessentials.persistence.playerwarps.CachedPlayerWarpRepository cached =
                PlayerWarpRepositories.cachedConcrete(
                        persistence,
                        com.uxplima.uxmessentials.shared.adapter.outbound.lookup.PlayerNames.resolver(
                                kernel.playerLookup()));
        bus.registry().register(com.uxplima.uxmessentials.shared.adapter.outbound.bus.PlayerWarpSync.listener(cached));
        PlayerWarpRepository repository =
                com.uxplima.uxmessentials.shared.adapter.outbound.bus.PlayerWarpSync.repository(
                        cached, bus.publisher());
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry registry = teleportRegistry != null
                ? teleportRegistry
                : new com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry();
        PlayerWarpTeleporter teleporter = new TeleportPlayerWarpAdapter(teleportEngine, registry);
        PlayerWarpQuota quota = new PlayerWarpQuota(kernel.permissions(), defaultLimit(ctx));
        // UsePlayerWarp is built once so the /pwarp command, the browse menu, and the shared warp editor's "go to"
        // button all teleport through the same path; the go-to handle the warps editor reads is bound to it here.
        // The ordered access gate reads the ban/member/whitelist/password stores and rate-limits password attempts
        // through the shared Cooldowns port. The economy seam is the jOOQ bridge over the resolved provider, a
        // priced warp charges the visitor and banks the owner's cut through it; when economy is disabled it stays
        // empty and a priced warp teleports for free (the WarpEconomy soft-coupling precedent).
        Optional<PlayerWarpEconomy> economy =
                playerWarpEconomy(ctx, persistence, kernel, economyProvider, economyCurrencies, economyBackends);
        // The member store backs both the ordered access gate (a member reaches a private warp) and the management
        // authorization (a member's role decides which edit verbs they may run), so it is built once and shared.
        WarpMemberStore memberStore = PlayerWarpRepositories.memberStore(persistence, kernel.log());
        WarpAuthorization warpAuthorization = new WarpAuthorization(memberStore);
        // The ban and whitelist stores back both the ordered access gate (T3) and the management verbs (T6b-2),
        // so they are built once here and shared with the ManageBans / ManageWhitelist use cases below.
        WarpBanStore banStore = PlayerWarpRepositories.banStore(persistence);
        WarpWhitelistStore whitelistStore = PlayerWarpRepositories.whitelistStore(persistence, Clock.systemUTC());
        // The password store backs both the ordered access gate (a PASSWORD warp verifies the entered plaintext)
        // and the edit verbs' set/clear-password writes, so it is built once here and shared with both.
        PlayerWarpPasswordStore passwordStore = PlayerWarpRepositories.passwordStore(persistence);
        // The cross-server sub-group: when cross-server.enabled is on, a warp tagged for another backend is routed
        // across the proxy instead of hopped locally. The gate charges here, records the pending row, connects the
        // player, and the target backend completes the hop on join (or refunds). Off, both seams are empty and the
        // gate fails a remote warp closed (unavailable + refund) rather than a broken local hop. localServerId is
        // this backend's network.server-id, the same source the P3 server-id claim used.
        String localServerId = bus.publisher().serverId();
        CrossServerParts crossServer = buildCrossServer(
                ctx, plugin, kernel, persistence, repository, teleporter, notifier, economy, localServerId);
        UsePlayerWarp usePlayerWarp = new UsePlayerWarp(
                repository,
                teleporter,
                notifier,
                new com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker(),
                kernel.permissions(),
                banStore,
                memberStore,
                whitelistStore,
                passwordStore,
                kernel.cooldowns(),
                economy,
                localServerId,
                crossServer.seam(),
                Clock.systemUTC());
        if (playerWarpHandle != null) {
            playerWarpHandle.bind(repository);
        }
        if (playerWarpGoTo != null) {
            playerWarpGoTo.bind((viewer, owner, name) -> usePlayerWarp.useFor(
                    viewer, owner, com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName.of(name)));
        }
        // Build the use cases, then the management GUI over them, then the services holder carrying both editors.
        SetPlayerWarp setPlayerWarp = new SetPlayerWarp(
                repository,
                quota,
                notifier,
                kernel.events(),
                kernel.gate(),
                Clock.systemUTC(),
                ctx.config().getStringList("world-blacklist", List.of()));
        ArchivePlayerWarp archivePlayerWarp = new ArchivePlayerWarp(
                repository, warpAuthorization, notifier, kernel.events(), kernel.gate(), Clock.systemUTC());
        SetPlayerWarpVisibility visibility = new SetPlayerWarpVisibility(repository, notifier, Clock.systemUTC());
        // The rate verb tallies the per-vote star rows and writes the Bayesian rollup back through the repository;
        // the favourite verb toggles the star store and recomputes favourite_count. Both are any-viewer writes, so
        // they go through the same broadcasting repository as every other write. The confidence C is this module's
        // own tunable: higher pulls a warp's score harder toward the global mean, so few votes cannot top the sort.
        WarpRatingStore ratingStore = PlayerWarpRepositories.ratingStore(persistence);
        WarpFavouriteStore favouriteStore = PlayerWarpRepositories.favouriteStore(persistence, Clock.systemUTC());
        // The rating-reward sub-group: when ratings.rewards is on, a rating grants a deduped reward to the rater
        // (once per warp) and the owner (once per unique rater). Off, the bundle is empty and nothing is granted or
        // recorded: a disabled sub-group instantiates no granter and writes no reward row.
        Optional<RatingRewards> ratingRewards = ratingRewards(ctx, kernel, persistence, economy);
        RatePlayerWarp ratePlayerWarp = new RatePlayerWarp(
                repository,
                ratingStore,
                notifier,
                new BayesianRating(ratingConfidence(ctx)),
                Clock.systemUTC(),
                ratingRewards);
        FavouritePlayerWarp favouritePlayerWarp = new FavouritePlayerWarp(repository, favouriteStore, notifier);
        // The role-gated people/money verbs (T6b-2) all gate through the shared WarpAuthorization and reuse the
        // member/whitelist/ban stores and the economy seam already built above: no new persistence, just use cases.
        ManageMembers manageMembers =
                new ManageMembers(repository, warpAuthorization, memberStore, notifier, Clock.systemUTC());
        ManageWhitelist manageWhitelist = new ManageWhitelist(repository, warpAuthorization, whitelistStore, notifier);
        ManageBans manageBans = new ManageBans(repository, warpAuthorization, banStore, notifier, Clock.systemUTC());
        WithdrawEarnings withdrawEarnings = new WithdrawEarnings(repository, warpAuthorization, notifier, economy);
        // The single-warp edit verbs and the ownership transfer gate through the same shared WarpAuthorization and
        // reuse the repository, notifier, and password store built above: no new persistence, just use cases.
        EditPlayerWarp editPlayerWarp =
                new EditPlayerWarp(repository, warpAuthorization, notifier, passwordStore, Clock.systemUTC());
        TransferPlayerWarp transferPlayerWarp =
                new TransferPlayerWarp(repository, warpAuthorization, notifier, Clock.systemUTC());
        // The sponsorship sub-group: an owner buys a paid, time-limited pinned browse slot through the same
        // WarpAuthorization (owner-only) and the economy seam already built. When economy is disabled the sub-group is
        // forced off (a paid feature cannot run for free), so its command branch, its manage button, its pinned slots,
        // and its expiry sweep all stay dark. The read model the browse and the landing share is built once here.
        SponsorConfig sponsorConfig = sponsorConfig(ctx, kernel, economy);
        PlayerWarpBrowse browse = PlayerWarpRepositories.browse(persistence, Clock.systemUTC());
        BuySponsorship buySponsorship =
                new BuySponsorship(repository, warpAuthorization, economy, notifier, sponsorConfig, Clock.systemUTC());
        PlayerWarpListMenu listMenu = buildGui(
                plugin,
                kernel,
                repository,
                setPlayerWarp,
                visibility,
                archivePlayerWarp,
                guiText,
                guiLayouts,
                textInput,
                menus);
        listMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        // The paged public browse bare /pwarp opens: one card per public warp read a page at a time through the
        // browse read model. The read model is the jOOQ LIMIT/OFFSET+COUNT projection, never a full-table scan, so
        // opening it is one bounded page query. A tile's left click opens the per-warp detail panel; a right click is
        // the quick-teleport shortcut through the same UsePlayerWarp gate the command drives. The browse and the view
        // open each other (a tile opens the view, the view's back button reopens the browse), so a one-slot holder
        // breaks the construction cycle: the browse is built first with a deferred view opener, the view second.
        PlayerWarpViewMenu[] viewHolder = new PlayerWarpViewMenu[1];
        PlayerWarpBrowseMenu browseMenu = new PlayerWarpBrowseMenu(
                menus,
                kernel.scheduler(),
                browse,
                usePlayerWarp,
                kernel.messages(),
                (viewer, name) -> viewHolder[0].open(viewer, name),
                sponsorConfig);
        // The browse tile opens the view, the view opens the manage panel, and the manage panel's back button reopens
        // the view, a three-way construction cycle, so a one-slot holder breaks it: the manage menu is built after the
        // view, and the view's manage-open runs through this holder filled a step later.
        PlayerWarpManageMenu[] manageHolder = new PlayerWarpManageMenu[1];
        // The per-warp detail panel (pwarp-view) and the five-star rating menu (pwarp-rate) behind it. It resolves the
        // clicked warp off the tick thread (a bounded findByName plus the viewer's favourite and membership flags)
        // and
        // routes its buttons through the same UsePlayerWarp / FavouritePlayerWarp / RatePlayerWarp use cases.
        PlayerWarpViewMenu viewMenu = new PlayerWarpViewMenu(
                menus,
                kernel.scheduler(),
                repository,
                favouriteStore,
                memberStore,
                usePlayerWarp,
                favouritePlayerWarp,
                ratePlayerWarp,
                kernel.messages(),
                notifier,
                browseMenu::open,
                (viewer, name) -> manageHolder[0].open(viewer, name));
        viewHolder[0] = viewMenu;
        // The three people sub-menus (pwarp-members / pwarp-whitelist / pwarp-bans) the manage panel's people buttons
        // open. The manage panel opens them and their back button reopens it, so a one-slot holder breaks that cycle:
        // the people menu is built after the manage panel and reads it back through this holder.
        PlayerWarpPeopleMenu[] peopleHolder = new PlayerWarpPeopleMenu[1];
        // The manage panel's icon button opens the pwarp-icon selector, whose pick reopens the manage panel, another
        // one-slot holder breaks that cycle: the icon menu is built after the manage panel and reads it back here.
        PlayerWarpIconMenu[] iconHolder = new PlayerWarpIconMenu[1];
        // The capability-gated management panel (pwarp-manage) the view's manage button opens: it resolves the warp and
        // the viewer's WarpRole off the tick thread and routes each button through the same single-warp use cases the
        // /pwarp verbs drive, gated by the viewer's role. Its back button reopens the view; its people buttons open the
        // members / whitelist / bans sub-menus; its icon button opens the pwarp-icon selector.
        PlayerWarpManageMenu manageMenu = new PlayerWarpManageMenu(
                menus,
                kernel.scheduler(),
                repository,
                warpAuthorization,
                editPlayerWarp,
                withdrawEarnings,
                transferPlayerWarp,
                archivePlayerWarp,
                buySponsorship,
                kernel.playerLookup(),
                kernel.messages(),
                notifier,
                sponsorConfig,
                viewMenu::open,
                (viewer, name) -> peopleHolder[0].openMembers(viewer, name),
                (viewer, name) -> peopleHolder[0].openWhitelist(viewer, name),
                (viewer, name) -> peopleHolder[0].openBans(viewer, name),
                (viewer, name) -> iconHolder[0].open(viewer, name));
        manageHolder[0] = manageMenu;
        // The pwarp-icon palette selector the manage panel's icon button opens: a picked material becomes the warp's
        // browse icon through the same EditPlayerWarp use case a typed edit drove, then the manage panel re-opens.
        PlayerWarpIconMenu iconMenu =
                new PlayerWarpIconMenu(menus, kernel.scheduler(), editPlayerWarp, manageMenu::open);
        iconHolder[0] = iconMenu;
        // The people sub-menus snapshot the warp's bounded members / whitelist / bans list off the tick thread and
        // route
        // each row/add click through the same ManageMembers / ManageWhitelist / ManageBans use cases the /pwarp verbs
        // drive; each back button reopens the manage panel.
        PlayerWarpPeopleMenu peopleMenu = new PlayerWarpPeopleMenu(
                menus,
                kernel.scheduler(),
                repository,
                kernel.playerLookup(),
                memberStore,
                whitelistStore,
                banStore,
                manageMembers,
                manageWhitelist,
                manageBans,
                kernel.messages(),
                notifier,
                manageMenu::open);
        peopleHolder[0] = peopleMenu;
        // The pwarp-categories landing bare /pwarp opens: a hub of quick browse entries (all / mine / favourites / top
        // rated) plus one button per defined category. It reuses the shared warp-categories set (player-warps and warps
        // share it) through a fresh cached jOOQ repository over the same table, snapshotting the bounded set off the
        // tick thread; every entry hands off to the browse with a preset filter, so the landing never reads the warp
        // table. Built after the browse it drives.
        PlayerWarpCategoriesMenu categoriesMenu = new PlayerWarpCategoriesMenu(
                menus,
                kernel.scheduler(),
                com.uxplima.uxmessentials.persistence.warps.WarpRepositories.categories(persistence),
                kernel.messages(),
                browse,
                browseMenu::open,
                (viewer, name) -> viewHolder[0].open(viewer, name),
                sponsorConfig);
        browseMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        viewMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        manageMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        peopleMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        categoriesMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        iconMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        guiRegistry.register(new ManagementGuiEntry(
                "playerwarps",
                PlayerwarpsMessageKey.PWARP_GUI_LIST_TITLE,
                org.bukkit.Material.ENDER_PEARL,
                PlayerWarpListMenu.MANAGE_PERMISSION,
                listMenu::open));
        PlayerWarpServices services = assemble(
                kernel,
                repository,
                usePlayerWarp,
                setPlayerWarp,
                archivePlayerWarp,
                visibility,
                ratePlayerWarp,
                favouritePlayerWarp,
                manageMembers,
                manageWhitelist,
                manageBans,
                withdrawEarnings,
                editPlayerWarp,
                transferPlayerWarp,
                buySponsorship,
                sponsorConfig,
                notifier,
                editorView,
                listMenu,
                browseMenu,
                categoriesMenu);
        // The rent sub-group: a config-gated, off-tick, batched sweep that charges due warps (bank-first, then the
        // owner's wallet), suspends then archives the unpaid, and mails owners a heads-up before their term lapses.
        // Disabled, or with no economy to charge against, it is null and nothing is scheduled: a disabled sub-group
        // schedules no task and charges nothing.
        RentSweep rentSweep = buildRentSweep(ctx, persistence, kernel, repository, economy);
        // The sponsor expiry sweep: a config-gated, off-tick, batched sweep that frees the slot of every lapsed
        // sponsorship and stamps its post-expiry cooldown. Disabled (the sub-group off, or economy absent so it was
        // forced off) it is null and nothing is scheduled.
        SponsorExpirySweep sponsorSweep = buildSponsorSweep(ctx, kernel, repository, sponsorConfig);
        PlayerwarpsJoinListener joinWarmer =
                new PlayerwarpsJoinListener(repository, kernel.scheduler(), crossServer.arrival());
        return new Wired(
                PlayerWarpCommands.all(services, kernel.messages()),
                List.of(joinWarmer),
                repository,
                browse,
                quota,
                setPlayerWarp,
                editPlayerWarp,
                archivePlayerWarp,
                rentSweep,
                sponsorSweep,
                legacyMigration);
    }

    /**
     * Build the rent sweep when the {@code rent} sub-group is on and there is an economy to charge against, else
     * {@code null}. Rent with no economy provider could only ever suspend every warp, so it is left off with an
     * operator warning rather than silently starving the network. The mail seam is a fresh {@link JooqMailRepository}
     * over the always-present messaging {@code mail} table, so an offline owner still gets their reminder.
     */
    private static @org.jspecify.annotations.Nullable RentSweep buildRentSweep(
            ModuleContext ctx,
            Persistence persistence,
            KernelPorts kernel,
            PlayerWarpRepository repository,
            Optional<PlayerWarpEconomy> economy) {
        RentConfig config = rentConfig(ctx);
        if (!config.enabled()) {
            return null;
        }
        if (economy.isEmpty()) {
            kernel.log().warn("event=playerwarp_rent_no_economy detail=rent_enabled_but_economy_absent_sweep_off");
            return null;
        }
        Clock clock = Clock.systemUTC();
        SettleRent settle = new SettleRent(repository, economy.orElseThrow(), new RentPolicy(), config, clock);
        MailRentMailer mailer =
                new MailRentMailer(MessagingStores.mail(persistence), kernel.messages(), clock, kernel.log());
        RentReminders reminders = new RentReminders(repository, mailer, config, clock);
        Duration interval = Duration.ofMinutes(Math.max(1L, ctx.config().getLong("rent.check-interval-minutes", 60L)));
        return new RentSweep(repository, settle, reminders, config, kernel.scheduler(), interval, clock, kernel.log());
    }

    /**
     * Build the sponsor expiry sweep when the {@code sponsor} sub-group is on, else {@code null}. The config is already
     * forced off when economy is absent (see {@link #sponsorConfig}), so an enabled config here always has an economy
     * to have charged against; the sweep itself only frees slots and stamps cooldowns, no money moves in it.
     */
    private static @org.jspecify.annotations.Nullable SponsorExpirySweep buildSponsorSweep(
            ModuleContext ctx, KernelPorts kernel, PlayerWarpRepository repository, SponsorConfig config) {
        if (!config.enabled()) {
            return null;
        }
        Duration interval =
                Duration.ofMinutes(Math.max(1L, ctx.config().getLong("sponsor.check-interval-minutes", 60L)));
        return new SponsorExpirySweep(
                repository, config, kernel.scheduler(), interval, Clock.systemUTC(), kernel.log());
    }

    /**
     * Build the cross-server sub-group's send seam and arrival handler when {@code cross-server.enabled} is on,
     * else two empty {@link Optional}s. The two share one {@link PendingTeleportStore} over the network-shared
     * {@code player_warp_pending_teleports} table. The send half writes the row and connects the player, the
     * arrival half reads it on join and completes the local hop (or refunds). The {@link ServerConnector} is built
     * fresh over the standard proxy channel, exactly as the {@code npc}/{@code holograms} connectors are; when no
     * proxy is in front its {@code isAvailable()} degrades a send to an unavailable-notice-plus-refund. Disabled,
     * nothing is instantiated and the gate fails a remote warp closed.
     */
    private static CrossServerParts buildCrossServer(
            ModuleContext ctx,
            Plugin plugin,
            KernelPorts kernel,
            Persistence persistence,
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            Notifier notifier,
            Optional<PlayerWarpEconomy> economy,
            String localServerId) {
        if (!ctx.config().getBoolean("cross-server.enabled", false)) {
            return new CrossServerParts(Optional.empty(), Optional.empty());
        }
        Clock clock = Clock.systemUTC();
        PendingTeleportStore store = PlayerWarpRepositories.pendingTeleportStore(persistence);
        ServerConnector connector = new BukkitServerConnector(plugin, kernel.log());
        CrossServerTeleport seam = new BukkitCrossServerTeleport(
                store, connector, kernel.scheduler(), economy, notifier, localServerId, clock);
        Duration arrivalDelay =
                Duration.ofMillis(Math.max(0, ctx.config().getInt("cross-server.arrival-delay-ticks", 20)) * 50L);
        Duration requestTtl =
                Duration.ofSeconds(Math.max(1L, ctx.config().getLong("cross-server.request-ttl-seconds", 30L)));
        CrossServerArrival arrival = new CrossServerArrival(
                store,
                repository,
                teleporter,
                economy,
                notifier,
                kernel.scheduler(),
                localServerId,
                arrivalDelay,
                requestTtl,
                clock);
        return new CrossServerParts(Optional.of(seam), Optional.of(arrival));
    }

    /** The cross-server sub-group's two wired seams: both empty when the sub-group is off. */
    private record CrossServerParts(Optional<CrossServerTeleport> seam, Optional<CrossServerArrival> arrival) {}

    /**
     * Read the {@code sponsor} config block into the immutable {@link SponsorConfig}. When the sub-group is enabled but
     * no economy provider is present it is forced off with an operator warning. A paid pinned slot cannot be sold for
     * free, so running the feature with nothing to charge against would only ever hand out placement gratis.
     */
    private static SponsorConfig sponsorConfig(
            ModuleContext ctx, KernelPorts kernel, Optional<PlayerWarpEconomy> economy) {
        var cfg = ctx.config();
        boolean enabled = cfg.getBoolean("sponsor.enabled", false);
        if (enabled && economy.isEmpty()) {
            kernel.log().warn("event=playerwarp_sponsor_no_economy detail=sponsor_enabled_but_economy_absent_off");
            enabled = false;
        }
        return new SponsorConfig(
                enabled,
                Math.max(0, cfg.getInt("sponsor.slots", 5)),
                Math.max(1, cfg.getInt("sponsor.duration-days", 7)),
                BigDecimal.valueOf(Math.max(0L, cfg.getLong("sponsor.price", 1000L))),
                cfg.getString("sponsor.currency", "default"),
                Math.max(1, cfg.getInt("sponsor.max-concurrent-per-player", 1)),
                Duration.ofDays(Math.max(0L, cfg.getLong("sponsor.cooldown-days", 3L))));
    }

    /** Read the {@code rent} config block into the immutable {@link RentConfig} the policy and reminders resolve through. */
    private static RentConfig rentConfig(ModuleContext ctx) {
        var cfg = ctx.config();
        return new RentConfig(
                cfg.getBoolean("rent.enabled", false),
                BigDecimal.valueOf(Math.max(0L, cfg.getLong("rent.amount", 100L))),
                cfg.getString("rent.currency", "default"),
                Duration.ofDays(Math.max(1L, cfg.getLong("rent.period-days", 7L))),
                Duration.ofDays(Math.max(0L, cfg.getLong("rent.grace-days", 3L))),
                reminderWindows(cfg.getStringList("rent.reminder-hours", List.of("24", "12", "6", "1"))),
                Set.copyOf(cfg.getStringList("rent.exempt.players", List.of())),
                Set.copyOf(cfg.getStringList("rent.exempt.categories", List.of())),
                Set.copyOf(cfg.getStringList("rent.exempt.worlds", List.of())));
    }

    /** Parse the {@code reminder-hours} list into positive-hour windows, tolerating a malformed entry. */
    private static List<Duration> reminderWindows(List<String> hours) {
        List<Duration> windows = new java.util.ArrayList<>();
        for (String hour : hours) {
            try {
                long value = Long.parseLong(hour.strip());
                if (value > 0) {
                    windows.add(Duration.ofHours(value));
                }
            } catch (NumberFormatException ignored) {
                // tolerate a malformed reminder-hours entry rather than crashing module enable
            }
        }
        return List.copyOf(windows);
    }

    /**
     * Build the jOOQ economy bridge over the resolved provider, or {@link Optional#empty()} when economy is
     * disabled (any of the provider or the two currency registries absent) so a priced warp stays free, the
     * {@code WarpEconomy} soft-coupling precedent. The owner-cut percentage and the auto-payout switch are read
     * from this module's own {@code payout} config block, keeping the seam as narrow as the gate needs.
     */
    private static Optional<PlayerWarpEconomy> playerWarpEconomy(
            ModuleContext ctx,
            Persistence persistence,
            KernelPorts kernel,
            @org.jspecify.annotations.Nullable EconomyProvider economyProvider,
            @org.jspecify.annotations.Nullable CurrencyRegistry economyCurrencies,
            @org.jspecify.annotations.Nullable CurrencyBackendRegistry economyBackends) {
        if (economyProvider == null || economyCurrencies == null || economyBackends == null) {
            return Optional.empty();
        }
        double rawCutPercent = ctx.config().getDouble("payout.cut-percent", 10.0);
        if (rawCutPercent < 0.0 || rawCutPercent > 100.0) {
            // PayoutConfig clamps this to [0, 100] so a typo can never bank a negative or over-price cut; warn once
            // here, at the read site, so the operator sees their configured value was out of range and corrected.
            kernel.log().warn("event=playerwarp_cut_percent_clamped configured={} clamped_to=[0,100]", rawCutPercent);
        }
        BigDecimal cutPercent = BigDecimal.valueOf(rawCutPercent);
        boolean autoPayout = ctx.config().getBoolean("payout.auto-payout", false);
        // Wrapped so every charge it makes reports itself: the entry fee rides along with a teleport, the
        // sponsorship fee with a purchase, and rent arrives on a timer while the owner is doing something else.
        PlayerWarpEconomy economy = new JooqPlayerWarpEconomy(
                persistence,
                economyProvider,
                economyCurrencies,
                economyBackends,
                new JooqPlayerWarpEconomy.PayoutConfig(cutPercent, autoPayout),
                kernel.log());
        return Optional.of(new ReceiptedPlayerWarpEconomy(
                economy, economyCurrencies, new ChargeReceipts(kernel.messages(), kernel.messageSink())));
    }

    /**
     * Build the player-warp management list (now an engine menu) and editor over the shared GUI framework. The
     * editor's back button reopens the list, so a one-slot holder breaks the list↔editor construction cycle (the
     * editor is built first, the list second, and the holder is filled before either is shown), the same pattern
     * the NPC GUI uses. The list's grid geometry and per-entry rendering now live in the {@code playerwarp-list}
     * menu spec rather than the old {@code pwarp-list.conf} layout, so only the editor still reads a GUI layout.
     */
    private static PlayerWarpListMenu buildGui(
            Plugin plugin,
            KernelPorts kernel,
            PlayerWarpRepository repository,
            SetPlayerWarp setPlayerWarp,
            SetPlayerWarpVisibility visibility,
            ArchivePlayerWarp archivePlayerWarp,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus) {
        PlayerWarpEditorSubLayouts subLayouts = PlayerWarpEditorSubLayouts.load(
                plugin.getDataFolder().toPath(), "playerwarps", "pwarp-editor", kernel.log());
        EntityEditorLayout editorLayout =
                guiLayouts.loadEntityEditor("playerwarps", "pwarp-editor", editorCodeDefault());
        PlayerWarpListMenu[] listHolder = new PlayerWarpListMenu[1];
        PlayerWarpEditorView editor = new PlayerWarpEditorView(
                menus,
                guiText,
                kernel.scheduler(),
                repository,
                visibility,
                archivePlayerWarp,
                textInput,
                kernel.messages(),
                editorLayout,
                subLayouts,
                (player, viewer) -> listHolder[0].open(player, viewer));
        PlayerWarpListMenu listMenu = new PlayerWarpListMenu(
                menus,
                kernel.scheduler(),
                kernel.permissions(),
                kernel.messages(),
                repository,
                setPlayerWarp,
                textInput,
                editor);
        listHolder[0] = listMenu;
        return listMenu;
    }

    /** The editor's property-button slots, the code default matching the bundled pwarp-editor.conf. */
    private static final List<Integer> EDITOR_PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22);

    /**
     * The 6-row editor code default used when no {@code pwarp-editor.conf} is present. The shared
     * {@link EntityEditorLayout#withDelete} factory is a 3-row default that cannot hold the property slots, so this
     * builds the layout directly with the bundled geometry (the same fix the holograms/NPC editors apply).
     */
    private static EntityEditorLayout editorCodeDefault() {
        return new EntityEditorLayout(
                6,
                EDITOR_PROPERTY_SLOTS,
                49,
                java.util.OptionalInt.of(53),
                org.bukkit.Material.ARROW,
                org.bukkit.Material.BARRIER,
                org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
    }

    private static PlayerWarpServices assemble(
            KernelPorts kernel,
            PlayerWarpRepository repository,
            UsePlayerWarp usePlayerWarp,
            SetPlayerWarp setPlayerWarp,
            ArchivePlayerWarp archivePlayerWarp,
            SetPlayerWarpVisibility visibility,
            RatePlayerWarp ratePlayerWarp,
            FavouritePlayerWarp favouritePlayerWarp,
            ManageMembers manageMembers,
            ManageWhitelist manageWhitelist,
            ManageBans manageBans,
            WithdrawEarnings withdrawEarnings,
            EditPlayerWarp editPlayerWarp,
            TransferPlayerWarp transferPlayerWarp,
            BuySponsorship buySponsorship,
            SponsorConfig sponsorConfig,
            Notifier notifier,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            PlayerWarpListMenu listMenu,
            PlayerWarpBrowseMenu browseMenu,
            PlayerWarpCategoriesMenu categoriesMenu) {
        return new PlayerWarpServices(
                setPlayerWarp,
                archivePlayerWarp,
                usePlayerWarp,
                new ListPlayerWarps(repository, notifier),
                visibility,
                kernel.playerLookup(),
                repository,
                editorView,
                kernel.scheduler(),
                listMenu,
                browseMenu,
                categoriesMenu,
                ratePlayerWarp,
                favouritePlayerWarp,
                manageMembers,
                manageWhitelist,
                manageBans,
                withdrawEarnings,
                editPlayerWarp,
                transferPlayerWarp,
                buySponsorship,
                sponsorConfig);
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_LIMIT));
    }

    /** The Bayesian smoothing constant C for the rating score, clamped non-negative; the module's {@code ratings} block. */
    private static int ratingConfidence(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("ratings.confidence", DEFAULT_CONFIDENCE));
    }

    /**
     * Build the rating-reward collaborators when the {@code ratings.rewards} sub-group is on, else
     * {@link Optional#empty()} so the rate use case grants nothing and writes no reward row. The granter reuses the
     * already-resolved economy seam for money credits and the shared console-command runner for command rewards; a
     * money reward with no economy present is a logged no-op inside the granter (a command-only reward still fires),
     * so the sub-group is not forced off by a missing economy the way the paid sponsor/rent groups are.
     */
    private static Optional<RatingRewards> ratingRewards(
            ModuleContext ctx, KernelPorts kernel, Persistence persistence, Optional<PlayerWarpEconomy> economy) {
        RatingRewardConfig config = ratingRewardConfig(ctx);
        if (!config.enabled()) {
            return Optional.empty();
        }
        WarpRatingRewardStore store = PlayerWarpRepositories.ratingRewardStore(persistence);
        BukkitRatingRewardGranter granter = new BukkitRatingRewardGranter(
                economy, kernel.scheduler(), new BukkitClickCommandRunner(), kernel.log());
        return Optional.of(new RatingRewards(store, granter, config));
    }

    /** Read the {@code ratings.rewards} config block into the immutable {@link RatingRewardConfig}. */
    private static RatingRewardConfig ratingRewardConfig(ModuleContext ctx) {
        ConfigStore cfg = ctx.config();
        boolean enabled = cfg.getBoolean("ratings.rewards.enabled", false);
        return new RatingRewardConfig(
                enabled, rewardSpec(cfg, "ratings.rewards.rater"), rewardSpec(cfg, "ratings.rewards.owner"));
    }

    /** Read one {@code {money, currency, command}} reward block into a normalised {@link RewardSpec}. */
    private static RewardSpec rewardSpec(ConfigStore cfg, String base) {
        BigDecimal money = BigDecimal.valueOf(Math.max(0L, cfg.getLong(base + ".money", 0L)));
        return RewardSpec.of(money, cfg.getString(base + ".currency", "default"), cfg.getString(base + ".command", ""));
    }

    /**
     * Everything the player-warps module contributes once wired: the Brigadier commands, the join cache-warmer,
     * the read ports the PAPI seam queries, and the optional rent sweep. The only repeating scheduled work is the
     * rent sweep, and it is present only when the {@code rent} sub-group is on with an economy to charge against;
     * {@link #startBackgroundWork()} arms it and {@link #stop()} halts it on module disable/reload.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join cache-warmer the plugin registers
     * @param repository the cached player-warp repository the PAPI seam reads owned warps from
     * @param quota the per-owner count-limit reducer the PAPI seam reads the limit through
     * @param setPlayerWarp the create/re-anchor use case, handed on so the published actions run the same one
     * @param editPlayerWarp the edit use case, handed on for the published rename and relocate
     * @param archivePlayerWarp the removal use case, handed on for the published archive, restore and delete
     * @param rentSweep the off-tick rent lifecycle sweep, or {@code null} when the rent sub-group is disabled
     * @param sponsorSweep the off-tick sponsor expiry sweep, or {@code null} when the sponsor sub-group is disabled
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PlayerWarpRepository repository,
            PlayerWarpBrowse browse,
            PlayerWarpQuota quota,
            SetPlayerWarp setPlayerWarp,
            EditPlayerWarp editPlayerWarp,
            ArchivePlayerWarp archivePlayerWarp,
            @org.jspecify.annotations.Nullable RentSweep rentSweep,
            @org.jspecify.annotations.Nullable SponsorExpirySweep sponsorSweep,
            Runnable legacyMigration) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(browse, "browse");
            Objects.requireNonNull(quota, "quota");
            Objects.requireNonNull(setPlayerWarp, "setPlayerWarp");
            Objects.requireNonNull(editPlayerWarp, "editPlayerWarp");
            Objects.requireNonNull(archivePlayerWarp, "archivePlayerWarp");
            Objects.requireNonNull(legacyMigration, "legacyMigration");
        }

        /** Arm the rent and sponsor sweeps, when they were wired; a no-op for a sub-group that is off. */
        public void startBackgroundWork() {
            if (rentSweep != null) {
                rentSweep.start();
            }
            if (sponsorSweep != null) {
                sponsorSweep.start();
            }
        }

        /** Halt the rent and sponsor sweeps on module disable/reload so no orphaned off-tick task survives. */
        public void stop() {
            if (rentSweep != null) {
                rentSweep.stop();
            }
            if (sponsorSweep != null) {
                sponsorSweep.stop();
            }
        }
    }
}
