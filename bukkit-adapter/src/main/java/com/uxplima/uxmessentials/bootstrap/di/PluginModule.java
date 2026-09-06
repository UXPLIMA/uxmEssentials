package com.uxplima.uxmessentials.bootstrap.di;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.api.action.UxmDiscordLinkActions;
import com.uxplima.uxmessentials.api.action.UxmEconomyActions;
import com.uxplima.uxmessentials.api.action.UxmHomeActions;
import com.uxplima.uxmessentials.api.action.UxmInvRollbackActions;
import com.uxplima.uxmessentials.api.action.UxmKitActions;
import com.uxplima.uxmessentials.api.action.UxmRanksActions;
import com.uxplima.uxmessentials.api.action.UxmSecurityActions;
import com.uxplima.uxmessentials.api.action.UxmWarpActions;
import com.uxplima.uxmessentials.api.bukkit.UxmApiHolder;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.api.query.UxmDiscordLinkQuery;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.query.UxmInvRollbackQuery;
import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerStateQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmPlaytimeQuery;
import com.uxplima.uxmessentials.api.query.UxmPresenceQuery;
import com.uxplima.uxmessentials.api.query.UxmRanksQuery;
import com.uxplima.uxmessentials.api.query.UxmRegionsQuery;
import com.uxplima.uxmessentials.api.query.UxmSecurityQuery;
import com.uxplima.uxmessentials.api.query.UxmSkinQuery;
import com.uxplima.uxmessentials.api.query.UxmTeleportQuery;
import com.uxplima.uxmessentials.api.query.UxmTradeQuery;
import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.api.query.UxmVoteQuery;
import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmWorldsQuery;
import com.uxplima.uxmessentials.bootstrap.CommandAliasDefaults;
import com.uxplima.uxmessentials.bootstrap.command.BackupCommand;
import com.uxplima.uxmessentials.bootstrap.command.GuiSubcommand;
import com.uxplima.uxmessentials.bootstrap.command.HelpCommand;
import com.uxplima.uxmessentials.bootstrap.command.LangCommand;
import com.uxplima.uxmessentials.bootstrap.command.MigrationImportNode;
import com.uxplima.uxmessentials.bootstrap.command.PermissionsSubcommand;
import com.uxplima.uxmessentials.bootstrap.command.PlaceholdersSubcommand;
import com.uxplima.uxmessentials.bootstrap.command.UxmessCommand;
import com.uxplima.uxmessentials.bootstrap.health.BusTransportHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.ClusterPeersHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.CommandConflictHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.DatabaseHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.EconomyProviderHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.ModuleCountHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.SchedulerHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.SoftDependencyHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.UpdateHealthCheck;
import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlWiring;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.RulesCommandControlPlaceholders;
import com.uxplima.uxmessentials.communication.adapter.CommunicationWiring;
import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandsWiring;
import com.uxplima.uxmessentials.custommenus.adapter.CustomMenusWiring;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordlinkWiring;
import com.uxplima.uxmessentials.discordlink.adapter.outbound.api.DiscordLinkActions;
import com.uxplima.uxmessentials.discordlink.adapter.outbound.api.DiscordLinkQueries;
import com.uxplima.uxmessentials.economy.adapter.EconomyWiring;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderRankEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderSurvivalSales;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderTradeEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.api.EconomyQueries;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.holograms.adapter.HologramsWiring;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardEntry;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders;
import com.uxplima.uxmessentials.homes.adapter.HomesWiring;
import com.uxplima.uxmessentials.homes.adapter.outbound.RepositoryHomeRespawnLocator;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeQueries;
import com.uxplima.uxmessentials.homes.application.HomeRespawnLocator;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.invrollback.adapter.InvrollbackWiring;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.api.InvRollbackActions;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.api.InvRollbackQueries;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldWiring;
import com.uxplima.uxmessentials.kits.adapter.KitsWiring;
import com.uxplima.uxmessentials.kits.adapter.outbound.api.KitQueries;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.messaging.adapter.MessagingWiring;
import com.uxplima.uxmessentials.messaging.adapter.MutableAfkStatus;
import com.uxplima.uxmessentials.messaging.adapter.MutableMutePolicy;
import com.uxplima.uxmessentials.messaging.adapter.outbound.AuthorityVanishVisibility;
import com.uxplima.uxmessentials.messaging.adapter.outbound.PresenceAfkStatus;
import com.uxplima.uxmessentials.messaging.adapter.outbound.api.MessagingQueries;
import com.uxplima.uxmessentials.migration.MigrationModule;
import com.uxplima.uxmessentials.migration.adapter.DataDirBackupSnapshot;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.migration.adapter.MigrationWiring;
import com.uxplima.uxmessentials.moderation.adapter.ModerationWiring;
import com.uxplima.uxmessentials.moderation.adapter.outbound.api.ModerationQueries;
import com.uxplima.uxmessentials.nametags.adapter.NametagsWiring;
import com.uxplima.uxmessentials.nametags.adapter.outbound.PresenterNametagsPlaceholders;
import com.uxplima.uxmessentials.npc.adapter.NpcWiring;
import com.uxplima.uxmessentials.persistence.communication.AnnouncementStores;
import com.uxplima.uxmessentials.persistence.ip.IpHistoryStores;
import com.uxplima.uxmessentials.persistence.ip.LegacyIpHistoryBackfill;
import com.uxplima.uxmessentials.persistence.lookup.PlayerNameRepositories;
import com.uxplima.uxmessentials.persistence.menu.PlayerDataRepositories;
import com.uxplima.uxmessentials.persistence.playerstate.PlaytimeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.security.SecurityKeyFile;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerstateWiring;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.MirrorWindow;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.api.PlayerStateQueries;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.api.PlaytimeQueries;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerwarpsWiring;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.api.PlayerWarpQueries;
import com.uxplima.uxmessentials.poses.adapter.PosesWiring;
import com.uxplima.uxmessentials.presence.adapter.PresenceWiring;
import com.uxplima.uxmessentials.presence.adapter.outbound.api.PresenceQueries;
import com.uxplima.uxmessentials.ranks.adapter.RanksWiring;
import com.uxplima.uxmessentials.ranks.adapter.outbound.api.RanksActions;
import com.uxplima.uxmessentials.ranks.adapter.outbound.api.RanksQueries;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.regions.adapter.RegionsWiring;
import com.uxplima.uxmessentials.regions.adapter.outbound.ServiceRegionsPlaceholders;
import com.uxplima.uxmessentials.regions.adapter.outbound.api.RegionsQueries;
import com.uxplima.uxmessentials.scoreboard.adapter.ScoreboardWiring;
import com.uxplima.uxmessentials.security.adapter.SecurityWiring;
import com.uxplima.uxmessentials.security.adapter.outbound.ModerationLockoutBan;
import com.uxplima.uxmessentials.security.adapter.outbound.SessionSecurityPlaceholders;
import com.uxplima.uxmessentials.security.adapter.outbound.api.SecurityActions;
import com.uxplima.uxmessentials.security.adapter.outbound.api.SecurityQueries;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.port.LockoutBan;
import com.uxplima.uxmessentials.servertweaks.adapter.ServerTweaksWiring;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConfigServerTweaksPlaceholders;
import com.uxplima.uxmessentials.servertweaks.application.ServerTweaksConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.api.EngineMenuApi;
import com.uxplima.uxmessentials.shared.adapter.inbound.api.UxmEssentialsApiImpl;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocalizedCommandVisibilityListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.MenuApi;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.MenuApiImpl;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenuCleanupListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuAntiDupeListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuTextPrompt;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.CommandActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.DataActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.EconomyActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.InfoPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.InputActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.IntegrationConditions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.ItemActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.ListControlActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LiveDataSources;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LuckPermsGroupSource;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuControlActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MessagingActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MovementActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.NumericSpatialConditions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PapiPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PlayerDataPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.SoundActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.StringConditions;
import com.uxplima.uxmessentials.shared.adapter.inbound.ip.IpHistoryRecorder;
import com.uxplima.uxmessentials.shared.adapter.inbound.lookup.PlayerNameRecordingListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.playerdata.PlayerDataLifecycleListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.IpHashing;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ActionContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.BukkitEventBridge;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.QueryContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusWiring;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProvidersConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.config.CommandCatalogConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.EconomyBackends;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadDatabaseHook;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PermissionQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.VaultEconomyHook;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.VaultPermissionHook;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.CachingPlayerNameIndex;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.BukkitPlayerFacts;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.BukkitServerMetrics;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.GateModerationPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.KitAccessPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.MenusMenuPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ProviderEconomyPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RegistryModulesPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryHologramsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryHomesPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryPlayerwarpsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositorySkinPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryVaultsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryVotePlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryWarpsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ServicesTeleportPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StaffStaffPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreCommunicationPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreDiscordlinkPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePlayerstatePlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePosesPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePresencePlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreRanksPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreScoreboardPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.VillagersPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.playerdata.CachingPlayerDataStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.protocol.ViaVersionClientProtocol;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateCheckSettings;
import com.uxplima.uxmessentials.shared.application.command.CommandCatalog;
import com.uxplima.uxmessentials.shared.application.command.CommandCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.command.CommandDefinition;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.LoadCondition;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.ClientProtocol;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.reload.ReloadTask;
import com.uxplima.uxmessentials.skin.adapter.SkinWiring;
import com.uxplima.uxmessentials.skin.adapter.outbound.api.SkinQueries;
import com.uxplima.uxmessentials.staff.adapter.StaffWiring;
import com.uxplima.uxmessentials.survival.adapter.SurvivalWiring;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import com.uxplima.uxmessentials.tablist.adapter.TablistWiring;
import com.uxplima.uxmessentials.tablist.adapter.outbound.RendererTablistPlaceholders;
import com.uxplima.uxmessentials.teleport.adapter.MutableHomeRespawnLocator;
import com.uxplima.uxmessentials.teleport.adapter.MutableJailGate;
import com.uxplima.uxmessentials.teleport.adapter.MutableWarpRespawnLocator;
import com.uxplima.uxmessentials.teleport.adapter.TeleportWiring;
import com.uxplima.uxmessentials.teleport.adapter.outbound.LinkedTeleportFee;
import com.uxplima.uxmessentials.teleport.adapter.outbound.api.TeleportQueries;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.trade.adapter.TradeWiring;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import com.uxplima.uxmessentials.trade.adapter.outbound.SessionsTradePlaceholders;
import com.uxplima.uxmessentials.trade.adapter.outbound.api.TradeQueries;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.vanish.adapter.VanishWiring;
import com.uxplima.uxmessentials.vanish.adapter.outbound.api.VanishQueries;
import com.uxplima.uxmessentials.vaults.adapter.VaultsWiring;
import com.uxplima.uxmessentials.vaults.adapter.outbound.api.VaultQueries;
import com.uxplima.uxmessentials.villagers.adapter.VillagersWiring;
import com.uxplima.uxmessentials.vote.adapter.VoteWiring;
import com.uxplima.uxmessentials.vote.adapter.outbound.api.VoteQueries;
import com.uxplima.uxmessentials.warps.adapter.WarpsWiring;
import com.uxplima.uxmessentials.warps.adapter.outbound.RepositoryWarpRespawnLocator;
import com.uxplima.uxmessentials.warps.adapter.outbound.api.WarpQueries;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.worlds.adapter.WorldsWiring;
import com.uxplima.uxmessentials.worlds.adapter.outbound.LinkedWorldEntryFee;
import com.uxplima.uxmessentials.worlds.adapter.outbound.TeleportRescueTargets;
import com.uxplima.uxmessentials.worlds.adapter.outbound.api.WorldQueries;
import com.uxplima.uxmessentials.worlds.application.port.RescueTargets;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmlib.advancement.Toasts;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single hand-rolled DI site. Consults the {@link ModuleRegistry} so a disabled module wires
 * nothing: no adapters, no commands, no listeners, no migrations, no runtime state.
 *
 * <p>The wiring invariant: the only path to constructing a context's adapters is through its enabled
 * {@code FeatureModule}. Nothing here news up a context's classes directly; the loop starts each
 * enabled module and the module owns its own construction inside {@code start}. The {@code JavaPlugin}
 * is held only here, in bootstrap: it never leaks into application or adapter code.
 */
@NullMarked
public final class PluginModule {

    /**
     * How many known player names the name index holds in memory when the config says nothing. Roughly 120 bytes
     * an entry, and a name outside the window still resolves when its case matches exactly.
     */
    private static final int DEFAULT_NAME_INDEX_SIZE = 50_000;

    private PluginModule() {}

    /**
     * Seed the name index from the server's own user cache the first time it runs, so players who joined before
     * this version resolve by name without having to join again. It only runs while the table is empty; from then
     * on the join listener owns the index.
     */
    private static void backfillNameIndex(
            JavaPlugin plugin,
            PlayerNameRepository store,
            PlayerNameIndex index,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        if (store.count() > 0) {
            return;
        }
        int seeded = 0;
        for (OfflinePlayer known : plugin.getServer().getOfflinePlayers()) {
            String name = known.getName();
            if (name == null) {
                continue;
            }
            index.record(known.getUniqueId(), name);
            seeded++;
        }
        log.info("event=name_index_backfilled players={}", String.valueOf(seeded));
    }

    /**
     * Keeps the pre-0.6.0 {@code MenuApi} service registered, so a plugin that already loads it keeps working while
     * the published {@code UxmEssentialsApi.menus()} surface takes over. Both write into the same bindings, so a
     * duplicate id still throws across the two.
     */
    // The whole point of this method is to use the deprecated surface one last time, in one place.
    @SuppressWarnings("removal")
    private static void registerLegacyMenuApi(
            JavaPlugin plugin, MenuBindings bindings, ItemRenderer renderer, IconProviderRegistry icons) {
        MenuApi legacy = new MenuApiImpl(bindings, renderer, icons);
        plugin.getServer().getServicesManager().register(MenuApi.class, legacy, plugin, ServicePriority.Normal);
    }

    /** Wires the plugin and returns the resources to close on disable. */
    public static CloseableResources wire(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        CloseableResources resources = new CloseableResources(log);
        // Assigned before any module is wired: the plugin loads at STARTUP so that the default world can reach
        // getDefaultWorldGenerator, which means every wiring step below runs with no worlds loaded. Anything that
        // needs one hands its work to this phase (see WorldPhase).
        resources.worldPhase(new WorldPhase(plugin.getServer(), log));
        return closeOnFailure(resources, () -> wireUnchecked(plugin, resources));
    }

    /**
     * Owns the partially built runtime from the first acquired resource onward. A bootstrap failure therefore
     * tears down the same reverse-order hook chain as a normal disable instead of leaking a pool, scheduler,
     * channel or half-started module until the server process exits.
     */
    static CloseableResources closeOnFailure(CloseableResources resources, Supplier<CloseableResources> wiring) {
        try {
            return wiring.get();
        } catch (RuntimeException | Error failure) {
            resources.close();
            throw failure;
        }
    }

    private static CloseableResources wireUnchecked(JavaPlugin plugin, CloseableResources resources) {
        Logger log = plugin.getLogger();
        com.uxplima.uxmessentials.shared.application.port.Logger kernelLog = KernelWiring.logger(plugin);
        ConfigStore config = KernelWiring.loadConfig(plugin, kernelLog);
        KernelWiring.Kernel wiredKernel = KernelWiring.wire(plugin, config, kernelLog);
        KernelPorts kernel = wiredKernel.ports();
        ModuleRegistry registry = new DefaultModuleRegistry();
        // The hand-wired hub registry every module's management GUI plugs into. Built once here and handed to
        // the hub command below; module wiring (SP1+) registers each module's opener into it. Empty until then.
        ManagementGuiRegistry guiRegistry = new ManagementGuiRegistry();
        // The two things every /uxmess reload re-reads, registered before any module wiring so they always run
        // first: the config tree (swapped atomically behind the ConfigStore) and the message catalogs. A module
        // step registered later therefore reads the fresh tree, never the one it was wired from. Both are pure
        // file re-reads, which is what lets the whole run go off-tick.
        resources.addReloadTask(ReloadTask.kernel("config", config::reload, "re-read from disk"));
        resources.addReloadTask(
                ReloadTask.kernel("messages", wiredKernel.catalog()::reload, "catalogs re-read from disk"));
        // The colours, from the file the server shares with the other plugins it runs of ours. A third kernel
        // step rather than part of the config step: the theme is its own file, and an operator who changes one
        // colour should see the reason for a failure named as the theme rather than as the config.
        Path themeFolder = plugin.getDataFolder().toPath();
        resources.addReloadTask(ReloadTask.kernel(
                "theme", () -> StyleTags.use(ThemeFile.read(themeFolder)), "colours re-read from disk"));
        // Every published command is wrapped so the requesting player's locale binds at the boundary.
        resources.localeBinding(new LocaleBinding(
                wiredKernel.localeStore(), wiredKernel.serverDefault(), kernel.messages(), kernel.log()));
        // A bare arg-only command answers with its usage instead of Brigadier's red parse error; injected
        // between the catalog and the locale wrap so the usage line resolves in the player's language.
        resources.usageBinding(new UsageBinding(kernel.messages()));

        Persistence persistence = KernelWiring.openPersistence(plugin, config, kernelLog, registry);
        // The pool is closed last (pushed first), after every module has stopped and drained its writes.
        resources.onClose(persistence::close);

        // The name index is where a typed name becomes an account, for every context. Backing it with the
        // database here (rather than in KernelWiring) is what the wiring order allows: the kernel is built before
        // persistence opens. The warm reads the database, so it goes to the async seam; the command path only
        // ever touches the in-memory map the warm fills.
        CachingPlayerNameIndex nameIndex = wiredKernel.nameIndex();
        PlayerNameRepository nameStore = PlayerNameRepositories.jooq(persistence);
        int nameIndexSize = config.getInt("lookup.name-index-size", DEFAULT_NAME_INDEX_SIZE);
        kernel.scheduler().async(() -> {
            nameIndex.backWith(nameStore, nameIndexSize);
            backfillNameIndex(plugin, nameStore, nameIndex, kernelLog);
        });
        resources.addListener(new PlayerNameRecordingListener(nameIndex));

        // The cross-server bus is a kernel concern (one per backend), built before the modules so each context
        // that opts into sync registers its listener and wraps its repository through the Bus handle. The
        // channel is registered only after every listener is in place (start, below); a disabled backend gets a
        // no-op bus and registers no channel, so the single-server path is unchanged.
        BusWiring.Wired bus = BusWiring.wire(plugin, config, kernel.scheduler(), kernel.log());
        resources.onClose(bus::stop);

        // The optional-plugin hook façade: resolved once here (plugin presence is stable for the run) over the
        // registered hooks, each binding to its real impl when its soft-depend is installed or to a no-op default
        // otherwise. The Vault economy/permission, NBT-API and HeadDatabase hooks join the worked PlaceholderAPI
        // example here; later integration features (the item providers) add their hook the same way and read
        // their capability from resources.hooks(). A missing soft-depend never loads an
        // external class: the no-op default carries none, so there is no NoClassDefFoundError path. Resolved
        // before the menu engine so the renderer's skull provider can read the HeadDatabase capability for hdb:<id>.
        Hooks hooks = Hooks.resolve(
                plugin.getServer(),
                kernel.log(),
                List.of(new VaultEconomyHook(), new VaultPermissionHook(), new HeadDatabaseHook(kernel.log())));
        resources.hooks(hooks);

        GuiText guiText = new GuiText(kernel.messages());
        // The data-driven menu engine: one binding façade holds every feature's handlers, and the renderer and
        // click listener share those exact registry instances so a feature registering behaviour after wiring is
        // seen by the already-built engine. The single click listener is installed once here, before any feature
        // wires, and both it and the open windows are torn down on disable so a reload re-installs cleanly. The
        // façade and the bindings are threaded into module wiring (the warp sound selector is the first feature to
        // register its bindings and open a spec through them); Phase 3 reuses the same path for the rest.
        MenuBindings menuBindings = new MenuBindings();
        // The icon chain backs every menu item's material field: the skull and equipment providers, the four
        // custom-item providers (ItemsAdder/Oraxen/Nexo/CraftEngine/MMOItems, reached reflectively behind a guard),
        // and the HeadDatabase provider over the just-resolved hook. So skull:/itemsadder:/hdb: etc. resolve when
        // the backing plugin is installed and degrade to the plain material when it is not. The plain two-arg
        // ItemRenderer used by feature menus uses only the no-dependency default chain (skull + equipment); this
        // composition-root renderer adds the plugin-backed sources. The runtime tail is the seam the dev-API adds
        // to: one shared IconProviderRegistry the renderer's chain consults after every built-in, so a plugin that
        // registers a custom material-spec prefix through MenuApi resolves on the very next render, and, being
        // last, can never shadow a built-in prefix.
        IconProviderRegistry runtimeIcons = new IconProviderRegistry();
        ItemRenderer menuItemRenderer = new ItemRenderer(
                guiText,
                menuBindings.placeholders(),
                IconProviders.full(plugin.getServer(), kernel.log(), hooks.capability(HeadQuery.class))
                        .withRuntime(runtimeIcons));
        MenuRenderer menuRenderer =
                new MenuRenderer(menuItemRenderer, menuBindings.conditions(), menuBindings.contents());
        // The public dev-API: another plugin loads MenuApi from the ServicesManager to register its own actions
        // (which cover custom buttons), requirements, placeholders, list sources and icon providers into these very
        // bindings, and to build a menu-styled item through this same renderer. Registered at Normal so a menu that
        // names a custom id resolves it after the owning plugin has enabled and re-validated (via /uxmess reload or
        // /menu reload). Paper clears the ServicesManager on disable, so no explicit teardown is needed here.
        registerLegacyMenuApi(plugin, menuBindings, menuItemRenderer, runtimeIcons);
        kernel.log().info("event=menu_api_registered");
        // The front door of the published developer API. Both halves are registered: the ServicesManager entry for a
        // consumer that enables after us, and the static holder for one that enables before us or wants its
        // registrations restored after a reload. The holder is withdrawn on disable so a consumer asking during
        // shutdown gets null rather than a façade over a torn-down engine.
        // The read surfaces the API hands out. Created here, before the front door is published, and filled later as
        // each enabled context wires: the front door has to be up early enough for a consumer that enables before us,
        // and the contexts only exist once the modules have wired.
        QueryContexts queries = QueryContexts.empty();
        // The write surfaces, filled the same way. Separate from the read ones because a consumer holding a query
        // surface must not be able to reach a write through it, which is the promise the documentation makes.
        ActionContexts actions = ActionContexts.empty();
        UxmEssentialsApi devApi = new UxmEssentialsApiImpl(
                plugin.getPluginMeta().getVersion(),
                registry,
                () -> config,
                new EngineMenuApi(menuBindings, menuItemRenderer, runtimeIcons),
                queries,
                actions);
        plugin.getServer()
                .getServicesManager()
                .register(UxmEssentialsApi.class, devApi, plugin, ServicePriority.Normal);
        UxmApiHolder.install(devApi);
        resources.onClose(UxmApiHolder::uninstall);
        // Every domain fact the plugin publishes becomes a Bukkit event for whoever is listening. One subscriber for
        // all twenty contexts, and it costs a map lookup when nobody is listening, which is the ordinary case.
        EventBridgeRegistry bridgeRegistry = new EventBridgeRegistry();
        EventBridges.installAll(bridgeRegistry);
        BukkitEventBridge bridge = new BukkitEventBridge(
                bridgeRegistry, kernel.scheduler(), plugin.getServer().getPluginManager(), kernel.log());
        InProcessDomainEventPublisher publishedEvents = (InProcessDomainEventPublisher) kernel.events();
        publishedEvents.subscribe(bridge);
        resources.onClose(() -> publishedEvents.unsubscribe(bridge));
        kernel.log()
                .info(
                        "event=dev_api_registered version={} bridged_events={}",
                        devApi.version(),
                        String.valueOf(bridgeRegistry.bridged().size()));
        // The editor renderer is the typed-property capability the same engine grows: a property editor is a
        // MenuHolder window the one listener routes and the one shutdown tears down, so the renderer is threaded into
        // both the listener (it repaints an editor after a property click) and the façade (it opens one). The façade
        // is built first so the listener can borrow its selector and confirm openers. What a property's click hook
        // uses to open a picker or a remove-confirm as an engine child window, and thread them into the editor click
        // context.
        EditorRenderer menuEditorRenderer = new EditorRenderer(guiText);
        // The action and condition registries are handed to the façade too, so an open runs a spec's open-actions and
        // gates on its open-requirement. The same registries the click listener resolves against, so an open-action
        // and a click action reach the identical handler. A menu's open-command opening it (see MenuOpenCommand)
        // therefore fires that menu's open-actions on open, which is what "pre-open" command actions reduce to.
        // The reopen tracker /menu last reads: one instance owned by the engine, remembering the last subject-less
        // (custom) menu each player had open. The façade records into it on open; a quit listener clears the entry
        // so it never retains an offline player; and the custommenus wiring hands the same instance to the /menu
        // command so `last` reopens what open recorded.
        LastMenu lastMenu = new LastMenu();
        // The Bedrock hybrid seam: resolve once which Bedrock plugin is installed and pick the detector and the
        // form screen accordingly: the Floodgate/Cumulus-backed pair when present, a Geyser-only detector when
        // Geyser runs without Floodgate, otherwise the always-false NONE detector and the no-op NONE screen. Only
        // the enabled branch of each forServer names an org.geysermc class, so a Java-only server never loads
        // Floodgate, Geyser or Cumulus. Both are threaded into the façade so the open choke-point can redirect a
        // Bedrock viewer to a native SimpleForm; the detector is also held on resources.bedrock() for any other
        // consumer. A Java viewer (isBedrock false) opens the chest exactly as before.
        BedrockDetector bedrock = BedrockDetector.forServer(plugin.getServer());
        resources.bedrock(bedrock);
        kernel.log().info("event=bedrock_detector backend={}", bedrock.backend());
        BedrockScreen bedrockScreen = BedrockScreen.forServer(plugin.getServer());
        resources.bedrockScreen(bedrockScreen);
        kernel.log().info("event=bedrock_screen backend={}", bedrockScreen == BedrockScreen.NONE ? "none" : "cumulus");
        Menus menus = new Menus(
                menuRenderer,
                kernel.scheduler(),
                menuBindings.lists(),
                menuEditorRenderer,
                menuBindings.actions(),
                menuBindings.conditions(),
                lastMenu,
                bedrock,
                bedrockScreen,
                menuBindings.pagedLists());
        resources.addListener(new LastMenuCleanupListener(lastMenu));
        // Defence-in-depth over the engine's cancel-all-clicks invariant: strip a marked menu display item that ever
        // escapes into a player's real inventory (close-sweep + join-sweep). A separate listener from the menu router,
        // like the last-menu cleanup above.
        resources.addListener(new MenuAntiDupeListener(kernel.log()));
        // The server-wide click-cooldown floor (milliseconds), read from modules/custommenus/config.conf. Zero (the
        // default, opt-in) means no throttling, so menus open byte-identically until an operator sets a floor; a menu
        // may raise it further with its own click-cooldown key. The system clock is threaded in explicitly so the
        // anti-spam window is the same code path tests drive with a hand-advanced clock.
        long menuClickCooldownMs =
                config.scoped(ModuleId.of("custommenus").configRoot()).getLong("click-cooldown-ms", 0L);
        // The text-input seam an input: menu step drives is built later, inside wireModules (it needs the shared anvil
        // and the resolved Bedrock detector). The listener is built here, so its capability is threaded in through a
        // deferred reference the seam populates on enable, before any menu can be clicked, the same late-init pattern
        // the economy backends use. A confirm: step needs only the confirm opener, already available above.
        AtomicReference<TextInput> menuTextInputRef = new AtomicReference<>();
        MenuTextPrompt menuTextPrompt = (player, key, prompt, initialText, onSubmit, onCancel) -> {
            TextInput seam = menuTextInputRef.get();
            if (seam == null) {
                onCancel.run();
                return;
            }
            seam.promptResolved(player, BukkitRefs.toRef(player), key, prompt, initialText, onSubmit, onCancel);
        };
        MenuListener menuListener = new MenuListener(
                menuRenderer,
                menuBindings.actions(),
                menuBindings.conditions(),
                kernel.scheduler(),
                plugin,
                menuEditorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener(),
                menuClickCooldownMs,
                System::currentTimeMillis,
                menuBindings.pagedLists(),
                menuTextPrompt,
                menuBindings.contents());
        menuListener.install();
        // The console action in an operator menu is privileged, so it stays off unless the operator opts in via
        // modules/custommenus/config.conf (allow-console). Our own code-registered feature menus are unrestricted
        // this flag only governs the generic console action a disk-loaded menu can reference.
        boolean allowMenuConsole =
                config.scoped(ModuleId.of("custommenus").configRoot()).getBoolean("allow-console", false);
        // The proxy connect seam shares the one BungeeCord/Velocity channel (npc and holograms build their own over
        // the same plugin-scoped channel; Paper drops them all on disable). Built once here as a local so the movement
        // slice's connect action is handed it directly, and also exposed on resources.serverConnector() for any other
        // consumer; with no proxy in front the frame is harmlessly discarded, which is the degraded single-server
        // behaviour. It is constructed before the vocab block so the same instance registers into both.
        ServerConnector menuServerConnector = new BukkitServerConnector(plugin, kernel.log());
        resources.serverConnector(menuServerConnector);
        MenuVocabulary.registerActions(menuBindings, menus, allowMenuConsole, kernel.log());
        // The input slice registers input/confirm so a spec that names input:<key>/confirm:<key> passes validation;
        // their real continuation-split behaviour lives in the click dispatcher, keyed off the ref's Continuation.
        InputActions.register(menuBindings, kernel.log());
        MenuVocabulary.registerConditions(menuBindings, kernel.permissions(), kernel.log());
        // The string slice of the condition vocabulary (contains, equals-ignorecase, regex, length, is-integer,
        // is-double, is-object) registers alongside the generic conditions; like the action slices it has its own
        // entry point so the MenuVocabulary.registerConditions signature stays untouched. It reads the placeholder
        // registry off the shared bindings internally to expand its operands, needs nothing else, and every
        // condition fails closed.
        StringConditions.register(menuBindings, kernel.log());
        // The numeric/spatial slice of the condition vocabulary (compare, is-near, cuboid, world) registers alongside
        // the generic conditions with its own entry point, so the MenuVocabulary.registerConditions signature stays
        // untouched. compare is the config-reachable twin of papi-compare (whose named left/op/right args the loader
        // cannot fill from a token); the spatial gates read only the viewer's own location and world, and every
        // condition fails closed. The general numeric comparators/arithmetic are already reachable through expr.
        NumericSpatialConditions.register(menuBindings, kernel.log());
        MenuVocabulary.registerPlaceholders(menuBindings);
        // The built-in time/inventory/statistic placeholder pack (%server_date%, %world_time%, %empty_slots%,
        // %held_item%, %stat_<NAME>%, ...) every custom menu can read the viewer's live state with. Each read is the
        // viewer's own entity/world state resolved on the render thread that already owns it, so no scheduler hop is
        // needed; the stat_ prefix fallback sits beside the disjoint papi_/data_ families on the shared registry.
        InfoPlaceholders.register(menuBindings);
        // The two ready-made live roster sources (online-players, worlds) every custom menu can page with no code.
        // They reuse the engine's own Scheduler so a source can hop onto the global region thread to snapshot live
        // server state before serving it back to the off-tick list resolver. The entity/world API is off-limits on
        // the async thread a source runs on, so the global snapshot is the Folia-safe seam.
        LiveDataSources.register(menuBindings, kernel.scheduler());
        // The ready-made luckperms-groups source: every LuckPerms group as a menu entry (a rank list / rank-picker
        // with no feature code). LuckPerms is a soft-depend reached purely by reflection past a plugin-present guard
        // (an absent one loads no SDK class), and it is async-safe by design, so the source reads directly on the
        // async list thread with no region hop. Hence no Scheduler is threaded here, only the server (present-guard
        // + ServicesManager lookup) and the operator logger the fail-closed degrade warns through.
        LuckPermsGroupSource.register(menuBindings, plugin.getServer(), kernel.log());
        // The messaging slice of the vocabulary (message-to/whisper, broadcast(+json/legacy), action-bar, title,
        // toast, log) registers alongside the generic actions; its own registration entry point keeps the
        // MenuVocabulary signature untouched. The toast action pops through uxmLib's advancement-toast service,
        // which routes its cleanup through the library's Folia-aware scheduler.
        Toasts menuToasts = new Toasts(plugin, new PaperScheduler(plugin));
        MessagingActions.register(menuBindings, menuToasts, kernel.log());
        // The player/command slice (chat-as-player, command-as-op, commandevent, command/console-random) registers
        // alongside the generic actions; like the messaging slice it has its own entry point so the MenuVocabulary
        // signature stays untouched. It reuses the same stateless click-command runner npc/holograms build, so the
        // temporary-op elevation is not reimplemented here; its two elevated actions honour the same allow-console
        // gate as the generic console action.
        CommandActions.register(menuBindings, new BukkitClickCommandRunner(), allowMenuConsole, kernel.log());
        // The movement slice (teleport within this server, connect to another proxy backend) registers alongside the
        // generic actions; like the other slices it has its own entry point so the MenuVocabulary signature stays
        // untouched. It teleports through Paper's Folia-safe teleportAsync and reaches the proxy through the shared
        // connector built just above.
        MovementActions.register(menuBindings, menuServerConnector, kernel.log());
        // The menu-control slice (refresh, refresh-slot, reset-pagination/reset-page) registers alongside the other
        // slices; like them it has its own entry point so the MenuVocabulary signature stays untouched. Its actions
        // drive the very window they fire in through the MenuControl the click supplies, so they need only the shared
        // bindings and the operator logger: no live handle is threaded here.
        MenuControlActions.register(menuBindings, kernel.log());
        // The paged-list-control slice (list-sort, list-filter, list-search) registers alongside the other slices;
        // like them it has its own entry point so the MenuVocabulary signature stays untouched. Its actions drive the
        // very paged list they name through the MenuControl the click supplies, the same page-flip re-query path, so
        // they need only the shared bindings and the operator logger; the search prompt reuses the shared text-input
        // seam the input: step already drives.
        ListControlActions.register(menuBindings, kernel.log());
        // The sound slice (broadcast-sound/soundall to every online player, rawsound/raw-sound for a verbatim
        // namespaced resource-pack key) registers alongside the other slices; like them it has its own entry point
        // so the MenuVocabulary signature stays untouched. It shares the <key> [volume] [pitch] grammar the
        // in-place `sound` action now reads, and needs only the shared bindings and the operator logger.
        SoundActions.register(menuBindings, kernel.log());
        PapiPlaceholders.registerInto(menuBindings);
        resources.onClose(() -> {
            menuListener.uninstall();
            menus.shutdown();
        });

        // The multi-currency seam over the economy's own backend set: a Phase-2 economy action and a warp fee now
        // spend through the same backend. The configured default currency (custommenus config, vault out of the box)
        // is what an action with no explicit currency spec falls back to; Phase-2/3 vocab reads this façade from
        // resources.currencies(). The economy module wires much later (wireEconomy, below), so the façade takes a
        // deferred reference to the registries and reads it on the first click: filled the moment economy is up.
        AtomicReference<EconomyBackends> menuCurrencyBackends = new AtomicReference<>();
        String defaultCurrency =
                config.scoped(ModuleId.of("custommenus").configRoot()).getString("default-currency", "vault");
        Currencies menuCurrencies = new Currencies(menuCurrencyBackends::get, kernel.log(), defaultCurrency);
        resources.currencies(menuCurrencies);
        // The economy slice of the vocabulary (give/take/set-money, give/take exp|levels|permission, points) is the
        // first consumer of that façade; it also grants/revokes permission nodes through the Vault permission seam,
        // a graceful no-op when Vault is absent. Registered here, after the currencies exist, into the same live
        // MenuBindings the earlier slices wrote to: its actions() registry is shared, so a click sees it at once.
        EconomyActions.register(menuBindings, menuCurrencies, hooks.capability(PermissionQuery.class), kernel.log());

        // The persistent player-data store reuses the plugin database (the same Flyway+jOOQ pattern every context
        // uses, V68's menu_player_data table) behind an in-memory cache, so the Phase-2 data actions and the Phase-6
        // %..._value_<key>% placeholders read player-scoped data as entity-thread cache hits and persist their
        // writes off-tick. Cross-cutting menu substrate, so it is built here beside the currencies and hooks; its
        // join/quit cache lifecycle registers as a normal listener through the shared listener path.
        CachingPlayerDataStore playerData =
                new CachingPlayerDataStore(PlayerDataRepositories.jooq(persistence), kernel.scheduler());
        resources.playerData(playerData);
        resources.addListener(new PlayerDataLifecycleListener(playerData, kernel.scheduler()));
        // The item and player-data slices of the vocabulary (give/take/set-item; data-set/add/sub/mul/div/remove and
        // meta-set/add/remove) register alongside the earlier slices; like them they have their own entry points so
        // the MenuVocabulary signature stays untouched. The data actions write through the player-data store built
        // just above; the meta actions store PDC on the online viewer through a PlayerMeta over this plugin's one
        // namespace. Both register into the same live MenuBindings the earlier slices wrote to.
        PlayerMeta menuPlayerMeta = new PlayerMeta(plugin);
        ItemActions.register(menuBindings, kernel.log());
        DataActions.register(menuBindings, playerData, menuPlayerMeta, kernel.log());
        // The read side of the same two stores: %data_value_<k>%/%data_number_<k>% surface the durable player-data the
        // data-* actions write, and %meta_value_<k>% surfaces the PDC the meta-* actions write, so an operator can
        // display what a click stored. One placeholder fallback over the same store and PlayerMeta instances.
        PlayerDataPlaceholders.register(menuBindings, playerData, menuPlayerMeta);
        // The requirement slice of the condition vocabulary (has-money/exp/level/item/meta/empty-slots,
        // check-inventory) registers alongside the generic conditions; like the action slices it has its own entry
        // point so the MenuVocabulary.registerConditions signature stays untouched. It reads the same multi-currency
        // façade the economy actions do and the same PDC accessor the meta actions do, and every condition fails
        // closed. Registered into the live MenuBindings before the specs are validated, so a valued condition
        // resolves at startup.
        RequirementConditions.register(menuBindings, menuCurrencies, menuPlayerMeta, kernel.log());
        // The integration slice of the condition vocabulary (has-group/has-points/weather/cooldown, job,
        // worldguard-region) plus the paired set-cooldown action registers alongside the generic conditions; like the
        // other slices it has its own entry point so the MenuVocabulary.registerConditions signature stays untouched.
        // has-group rides the Vault permission seam (LuckPerms/etc. through Vault), has-points the same PlayerPoints
        // currency the economy actions reach, and cooldown/set-cooldown the shared PDC-backed Cooldowns port; job and
        // worldguard-region are soft-depends reached purely by reflection past a plugin-present guard (an absent one
        // loads no SDK class). Weather reads only the viewer's own world, and every condition fails closed.
        // ViaVersion, when installed, is the only thing that knows what version a translated client actually
        // speaks; without it every player reports UNKNOWN and the client-version condition passes for everyone.
        ClientProtocol clientProtocol = ViaVersionClientProtocol.bind(plugin.getServer(), kernel.log());
        IntegrationConditions.register(
                menuBindings,
                hooks.capability(PermissionQuery.class),
                menuCurrencies,
                kernel.cooldowns(),
                clientProtocol,
                plugin.getServer(),
                kernel.log());

        PlaceholderContexts placeholders = wireModules(
                plugin,
                registry,
                config,
                kernel,
                queries,
                actions,
                persistence,
                resources,
                log,
                bus.bus(),
                hooks,
                guiRegistry,
                menus,
                menuBindings,
                menuCurrencyBackends,
                menuTextInputRef);
        bus.start();
        registerPlaceholders(plugin, placeholders, resources, kernel.log());
        // Cross-cutting server-integration polish (1.21+ pause-menu links + opt-in update checker + map-marker
        // integration). These belong to no feature context: server links apply once on enable, the update checker
        // off by default, built on the uxmLib update toolkit. Self-registers its permission-gated join notice and
        // returns a stop hook for its recurring check, and the map-marker integration renders warps/spawns onto
        // Dynmap/squaremap when one is installed (homes opt-in).
        IntegrationsWiring.Wired integrations = IntegrationsWiring.wire(plugin, config, kernel, persistence);
        resources.onClose(integrations.stop());
        MigrationImportNode importNode = wireMigration(plugin, config, kernel, persistence);
        List<HealthCheck> healthChecks = healthChecks(plugin, registry, config, persistence, bus);
        // The management-GUI hub is bootstrap-level (no feature context owns it): /uxmess gui draws the
        // ManagementGuiRegistry entries the viewer is permitted, each opening that module's own GUI. The
        // registry is constructed here and is threaded to module wiring (SP1+ each registers its opener);
        // an empty registry is the valid first state, so /uxmess gui replies with the empty-hub line until a
        // module plugs in. Geometry/materials load disk-first then bundled from modules/management/gui/hub.conf.
        GuiLayouts guiLayouts = new GuiLayouts(plugin.getDataFolder().toPath(), kernel.log());
        EntityListLayout hubLayout =
                guiLayouts.loadEntityList("management", "hub", EntityListLayout.paginatedDefault(Material.NETHER_STAR));
        ManagementHubView hub =
                new ManagementHubView(menus, guiText, kernel.scheduler(), kernel.permissions(), guiRegistry, hubLayout);
        GuiSubcommand guiNode = new GuiSubcommand(guiRegistry, hub, kernel.permissions(), kernel.messages());
        UxmessCommand uxmessCommand = new UxmessCommand(
                registry,
                config,
                importNode,
                guiNode,
                new PermissionsSubcommand(plugin.getDataFolder().toPath(), kernel.log()),
                new PlaceholdersSubcommand(plugin.getDataFolder().toPath(), kernel.log()),
                kernel.scheduler(),
                healthChecks,
                resources.reloadTasks());
        resources.addCommand(uxmessCommand);
        resources.onClose(uxmessCommand::close);
        // /lang is cross-cutting (not a feature context), so it is wired here in the bootstrap surface.
        resources.addCommand(new LangCommand(
                wiredKernel.localeStore(), wiredKernel.catalog(), kernel.messages(), kernel.messageSink()));
        // /backup reuses the migration importer's data-dir snapshot (a "backup-" prefixed copy of the
        // bundled .conf state), run off-tick through the Scheduler. Operator-level, so it too lives here.
        resources.addCommand(new BackupCommand(
                new DataDirBackupSnapshot(plugin.getDataFolder().toPath(), "backup", kernel.log()),
                kernel.scheduler(),
                kernel.log()));
        // /help is cross-cutting too. It reads the same resolved, module-filtered registration set the plugin
        // publishes (supplied lazily so it reflects every catalog rename and disable) and filters it per the
        // sender's own permission. The supplier is evaluated on each invocation, well after wiring completes.
        resources.addCommand(new HelpCommand(resources::commands, kernel.messages()));
        // Resolved last, once every module and the bootstrap commands have contributed, so the catalog sees
        // the full default surface before it applies the operator's rename/alias/disable choices.
        applyCatalog(plugin, kernel, wiredKernel.localeStore(), wiredKernel.serverDefault(), resources);
        return resources;
    }

    private static void applyCatalog(
            JavaPlugin plugin,
            KernelPorts kernel,
            LocaleStore localeStore,
            Locale serverDefault,
            CloseableResources resources) {
        List<CommandDefinition> defs = CommandAliasDefaults.augment(resources.registered().stream()
                .map(r -> new CommandDefinition(new CommandId(r.commandId()), r.defaultName(), r.defaultAliases()))
                .toList());
        CommandCatalogConfig.Loaded loaded =
                new CommandCatalogConfig(plugin.getDataFolder().toPath(), kernel.log()).loadAll();
        CommandCatalog.Resolution resolution = CommandCatalog.resolve(defs, loaded.overrides(), loaded.guiDefault());
        resolution.warnings().forEach(w -> kernel.log().warn("command catalog: {}", w.message()));
        // Seed the editable root file from the resolved surface so a fresh install lands a self-documenting
        // commands.conf. The loader above first migrates the historical commands/commands.conf location.
        writeDefaultCatalog(plugin, resolution.effective(), kernel.log());
        Map<String, EffectiveCommand> byId =
                resolution.effective().stream().collect(Collectors.toMap(e -> e.id().value(), e -> e));
        resources.catalogBinding(new CatalogBinding(byId));
        resources.addListener(new LocalizedCommandVisibilityListener(
                resolution.effective(),
                localeStore,
                serverDefault,
                plugin.getPluginMeta().getName().toLowerCase(Locale.ROOT)));
        // The GUI binding reuses the same resolved map so a command's gui flag and rename agree on one entry.
        resources.guiRootBinding(new GuiRootBinding(byId));
    }

    private static void writeDefaultCatalog(
            JavaPlugin plugin,
            List<EffectiveCommand> effective,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path file = dataFolder.resolve("commands.conf");
        // A split legacy catalog remains supported as a deprecated fallback. Do not silently flatten it because
        // preserving operator comments and unknown future command ids matters more than forcing the new layout.
        if (Files.exists(file) || Files.exists(dataFolder.resolve("commands"))) {
            return;
        }
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(file, CommandCatalogRenderer.render(effective));
            log.info("wrote default command catalog with {} commands", effective.size());
        } catch (IOException failure) {
            log.error("failed to write default command catalog to " + file, failure);
        }
    }

    private static void registerPlaceholders(
            JavaPlugin plugin,
            PlaceholderContexts placeholders,
            CloseableResources resources,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        // The PlaceholderAPI expansion is registered only when the plugin is present and at least one context
        // contributed a read seam; otherwise this is a no-op and nothing is closed on disable. The registration
        // happens after every context is wired so every enabled placeholder group is reachable.
        PlaceholderApiSupport.registerExpansion(
                        placeholders, plugin.getPluginMeta().getVersion(), log)
                .ifPresent(registration -> resources.onClose(registration::close));
    }

    private static MigrationImportNode wireMigration(
            JavaPlugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence) {
        // The migration adapter is command-gated, not a steady-state feature context, so it is wired here in
        // the operator surface rather than registered in the feature-module registry. Its enable gate ships
        // disabled; an enabled module publishes a live /uxmess import, a disabled one a dormant command that
        // reports the importer off. Nothing runs at enable: the importer fires only on the command.
        MigrationModule module = new MigrationModule();
        MigrationImportService service = MigrationWiring.wire(
                plugin,
                persistence,
                config.scoped(module.id().configRoot()),
                config.scoped(ModuleId.of("economy").configRoot()),
                kernel.scheduler(),
                kernel.log(),
                module.enabled(config));
        return new MigrationImportNode(service);
    }

    private static List<HealthCheck> healthChecks(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            Persistence persistence,
            BusWiring.Wired bus) {
        // Assembled after the modules are wired so the set reflects what is actually present: the database and
        // soft-depend probes always apply, the economy-provider ownership check only when economy is enabled.
        // The /uxmess doctor command runs each one off-tick (each is wrapped in HealthCheck.safe so a probe that
        // throws becomes a FAIL line rather than aborting the run).
        List<HealthCheck> checks = new ArrayList<>();
        checks.add(new DatabaseHealthCheck(persistence));
        checks.add(persistence.integrityCheck(
                plugin.getServer().getWorldContainer().toPath()));
        if (economyEnabled(registry, config)) {
            checks.add(new EconomyProviderHealthCheck(plugin.getServer().getServicesManager(), plugin));
        }
        // The Redis probe reads the unified network.redis block from the plugin-wide config, not a per-module one.
        checks.add(new SoftDependencyHealthCheck(plugin.getServer().getPluginManager(), config));
        // Not an integration check: this one names plugins we deliberately do not talk to, whose command
        // names collide with ours. It is informational, so it never fails the doctor run.
        checks.add(new CommandConflictHealthCheck(plugin.getServer().getPluginManager()));
        // The bus line reads the running transport's live healthy() flag (a cheap volatile read) so the operator
        // sees whether cross-server delivery is actually working, not just configured.
        checks.add(new BusTransportHealthCheck(bus.health()));
        // The cluster-peer line reads the in-memory roster the presence heartbeat feeds; only an enabled backend
        // has a roster, so the check is added only when the bus is enabled.
        bus.peers().ifPresent(peers -> checks.add(new ClusterPeersHealthCheck(peers)));
        checks.add(new SchedulerHealthCheck());
        checks.add(new UpdateHealthCheck(UpdateCheckSettings.from(config).enabled()));
        checks.add(new ModuleCountHealthCheck(registry, config));
        return List.copyOf(checks);
    }

    private static boolean economyEnabled(ModuleRegistry registry, ConfigStore config) {
        return moduleEnabled(registry, config, "economy");
    }

    private static boolean moduleEnabled(ModuleRegistry registry, ConfigStore config, String id) {
        return registry.byId(ModuleId.of(id))
                .map(module -> module.enabled(config))
                .orElse(false);
    }

    /**
     * The one IP-history capture, or null when neither of its two readers is enabled. It is kernel infrastructure
     * rather than one module's listener because moderation ({@code /alts}, {@code /seenip}, the STRICT ban fan-out)
     * and security (the per-address account cap, {@code /ipalts}) answer from the same rows: two captures meant two
     * schemas and a privacy claim on one side that the other contradicted. Addresses are tokenised under the same
     * server key-file the two-factor secrets are encrypted with, and the raw address is retained only while
     * moderation, its only consumer, is enabled.
     *
     * <p>The legacy moderation address rows are folded in off-thread on the first enable after the upgrade: SQL
     * alone could not tokenise them, so the migration left them for this.
     */
    private static @Nullable IpCapture wireIpHistory(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            Persistence persistence,
            KernelPorts kernel,
            CloseableResources resources) {
        boolean moderation = moduleEnabled(registry, config, "moderation");
        if (!moderation && !moduleEnabled(registry, config, "security")) {
            return null;
        }
        Path keyFile = plugin.getDataFolder().toPath().resolve("modules/security/secret.key");
        IpHashing tokens = new IpHashing(SecurityKeyFile.loadOrCreate(keyFile));
        IpHistoryStore store = IpHistoryStores.jooq(persistence);
        IpHistoryRecorder recorder =
                new IpHistoryRecorder(store, tokens, kernel.scheduler(), Clock.systemUTC(), moderation);
        resources.addListener(recorder);
        kernel.scheduler().async(() -> {
            int moved = LegacyIpHistoryBackfill.run(persistence, tokens, moderation);
            if (moved > 0) {
                kernel.log().info("folded {} legacy address rows into the shared ip history", moved);
            }
        });
        return new IpCapture(store, tokens, recorder);
    }

    /** The kernel IP-history seam handed to the two contexts that read it. */
    private record IpCapture(IpHistoryStore store, IpTokens tokens, IpHistoryRecorder recorder) {}

    private static PlaceholderContexts wireModules(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            KernelPorts kernel,
            QueryContexts queries,
            ActionContexts actions,
            Persistence persistence,
            CloseableResources resources,
            Logger log,
            Bus bus,
            Hooks hooks,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings,
            AtomicReference<EconomyBackends> menuCurrencyBackends,
            AtomicReference<TextInput> menuTextInputRef) {
        // teleport is wired before homes/warps (registry order is dependency-first), so its engine is
        // captured and handed to the contexts that delegate teleport execution to it.
        ContextLinks links = new ContextLinks(queries, actions);
        // Install uxmLib's single menu listener once, before any GUI-using module (vaults, itemworld) wires,
        // and tear it down on disable so a reload re-installs cleanly (the static install state is reset).
        Guis.install(plugin);
        resources.onClose(Guis::uninstall);
        // One anvil-input listener shared by every GUI-using context's create/rename prompts. Installed once here
        // (before any module wires) and torn down on disable; sessions are keyed per player and a player has at most
        // one anvil open, so a single installed instance is safe and avoids per-module listeners that never fire when
        // a module forgets to install its own.
        com.uxplima.uxmlib.gui.anvil.AnvilInput anvil = new com.uxplima.uxmlib.gui.anvil.AnvilInput(plugin);
        anvil.install();
        resources.onClose(anvil::uninstall);
        // The unified text-input seam: one entry point every GUI uses to capture a line of text, with the operator
        // choosing anvil-or-chat per input point in text-input.conf. It wraps the shared anvil above as one backend
        // and installs a single shared chat listener as the other, replacing the per-context chat-prompt listeners.
        // The Bedrock detector and screen are the same instances the Menus façade holds (captured on resources in
        // wire()); a Bedrock viewer's prompt renders as a Cumulus CustomForm instead. Both fall back to the no-ops if
        // wiring has not captured them, so a Java-only server always gets the anvil/chat path.
        BedrockDetector resolvedBedrock = resources.bedrock();
        BedrockScreen resolvedScreen = resources.bedrockScreen();
        TextInputInstaller.Installed input = TextInputInstaller.install(
                plugin,
                plugin.getDataFolder().toPath(),
                anvil,
                new GuiText(kernel.messages()),
                kernel.scheduler(),
                kernel.log(),
                resolvedBedrock == null ? BedrockDetector.NONE : resolvedBedrock,
                resolvedScreen == null ? BedrockScreen.NONE : resolvedScreen);
        resources.onClose(input.uninstall());
        resources.addReloadTask(ReloadTask.kernel(
                "text-input", input.settings()::reload, "input modes and cancel keywords re-read from disk"));
        TextInput textInput = input.textInput();
        // Hand the just-built seam to the menu listener's deferred reference so an input: menu step can prompt. Set
        // here, on enable, before any menu can be clicked. The listener was constructed earlier (it installs before
        // the modules wire), so this late-init is what closes the ordering gap between the two.
        menuTextInputRef.set(textInput);
        // The one shared target picker. It is infrastructure rather than a module's own screen (moderation's
        // sanction flows and the /eco admin GUI both open it), so it is built and registered with the engine here,
        // once, and handed to those modules through the links bag. Two instances would each try to claim the same
        // spec and binding ids.
        links.playerPicker = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView(
                menus, kernel.scheduler(), textInput, plugin.getServer(), kernel.messages(), kernel.messageSink());
        links.playerPicker.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        // The browse-menu layout loader resolves modules/<m>/gui/<name>.conf disk-first then bundled; built once
        // here with the data folder so every GUI-using context loads its layout the same way.
        GuiLayouts guiLayouts = new GuiLayouts(plugin.getDataFolder().toPath(), kernel.log());
        // Which claim plugins to consult and how to fold their answers is a server-wide choice read once from the
        // root config.conf, then handed to every context whose region gate consults claimed land (homes, teleport,
        // poses) so all three see the same provider set.
        ClaimProvidersConfig claimProviders = ClaimProvidersConfig.from(config, kernel.log());
        // The one join capture behind /alts, /seenip and /ipalts, built before the modules wire so both readers
        // observe the same recorder. Null when neither moderation nor security is enabled: nothing is recorded.
        IpCapture ipCapture = wireIpHistory(plugin, registry, config, persistence, kernel, resources);
        // Which modules truly came up, recorded as each one wires rather than read off the registry: a module can
        // be enabled in the config and still never wire (a capability check it fails, an isolated start that
        // threw), and the module_<id> family has to answer with what the server is actually running.
        Set<String> wiredModules = new HashSet<>();
        loadModulesIsolated(registry.enabledModules(config), resources, log, module -> {
            ConfigStore moduleConfig = config.scoped(module.id().configRoot());
            ModuleContext ctx = new ModuleContext(module.id(), moduleConfig, kernel);
            if (skippedByCapability(module, ctx, log)) {
                return;
            }
            startModule(module, ctx, resources, log);
            wiredModules.add(module.id().value());
            wireAdapters(
                    plugin,
                    module,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    hooks,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings,
                    menuCurrencyBackends,
                    claimProviders,
                    ipCapture);
        });
        // The server-metrics seam belongs to no feature context, it reads Bukkit/JVM globals, so it is wired
        // unconditionally here, after the modules, with the plugin-enable timestamp so its uptime is measured
        // from this enable (a reload restarts it) rather than the whole JVM's age.
        links.placeholders.serverMetrics(new BukkitServerMetrics(plugin.getServer(), Instant.now()));
        // The name lookup belongs to no context either: it is what turns the name in %uxmessentials_p_<name>_<key>%
        // into an account, through the same kernel port every command resolves a name with.
        links.placeholders.players(kernel.playerLookup());
        // The account facts (ping, first join, playtime, the item in hand) are the server's own, not any
        // module's, so they are wired here too and keep answering with every feature switched off.
        links.placeholders.playerFacts(new BukkitPlayerFacts(plugin.getServer()));
        // The generic cooldown family reads the same kernel gate every command-control rule stamps, so a label an
        // operator invented is readable from a scoreboard without the plugin knowing about it in advance.
        links.placeholders.cooldowns(kernel.cooldowns());
        // The relational keys read across two players. Vanish visibility is the same gate messaging resolves a
        // target through, built here from the one vanish authority; with vanish disabled the seam stays absent and
        // "can see" answers yes, which is what a server without vanish means.
        if (links.vanishStore != null && links.vanishLevelResolver != null) {
            links.placeholders.visibility(new AuthorityVanishVisibility(links.vanishStore, links.vanishLevelResolver));
        }
        if (links.tradeSessions != null) {
            links.placeholders.trade(new SessionsTradePlaceholders(links.tradeSessions));
        }
        // The menu-engine source seam belongs to no feature context either. It reads the always-present engine's
        // own runtime state (whether the requester is in a menu, which one, its page/rows, and a typed argument) so
        // scoreboards and tab can read %uxmessentials_menu_*%. Wired unconditionally over the same Menus façade the
        // modules opened their menus through.
        links.placeholders.menu(new MenusMenuPlaceholders(menus));
        // Whether a module is on is a question about the server, not about any one context, and it is most useful
        // exactly when the answer is no: an operator's own template reads it to hide a line rather than print a
        // dash at every player. Wired last, from what the loop above recorded.
        links.placeholders.modules(new RegistryModulesPlaceholders(wiredModules));
        return links.placeholders.build();
    }

    private static void wireAdapters(
            JavaPlugin plugin,
            FeatureModule module,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            Hooks hooks,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            AtomicReference<EconomyBackends> menuCurrencyBackends,
            ClaimProvidersConfig claimProviders,
            @Nullable IpCapture ipCapture) {
        // The bukkit-side adapters of each context are wired here once the context's pure module has
        // started. teleport builds its durable jOOQ spawn directory over persistence.dsl(); homes builds
        // its jOOQ repository the same way and delegates execution to the captured teleport engine.
        if (module.id().equals(ModuleId.of("teleport"))) {
            wireTeleport(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    guiLayouts,
                    guiRegistry,
                    menus,
                    menuBindings,
                    claimProviders);
        } else if (module.id().equals(ModuleId.of("worlds"))) {
            wireWorlds(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings);
        } else if (module.id().equals(ModuleId.of("homes"))) {
            wireHomes(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings,
                    claimProviders);
        } else if (module.id().equals(ModuleId.of("economy"))) {
            wireEconomy(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    hooks,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings,
                    menuCurrencyBackends);
        } else if (module.id().equals(ModuleId.of("warps"))) {
            wireWarps(ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, textInput, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("kits"))) {
            wireKits(plugin, ctx, resources, links, guiLayouts, guiRegistry, textInput, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("playerstate"))) {
            wirePlayerstate(plugin, ctx, persistence, resources, links, guiLayouts, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("messaging"))) {
            wireMessaging(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings);
        } else if (module.id().equals(ModuleId.of("vanish"))) {
            wireVanish(plugin, ctx, resources, links, bus);
        } else if (module.id().equals(ModuleId.of("presence"))) {
            wirePresence(plugin, ctx, resources, links, guiLayouts, guiRegistry, menus);
        } else if (module.id().equals(ModuleId.of("moderation"))) {
            wireModeration(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings,
                    Objects.requireNonNull(ipCapture, "ipCapture"));
        } else if (module.id().equals(ModuleId.of("itemworld"))) {
            wireItemworld(plugin, ctx, resources, links, guiLayouts, guiRegistry, textInput, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("vaults"))) {
            wireVaults(plugin, ctx, persistence, resources, bus, links, guiRegistry, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("communication"))) {
            wireCommunication(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings);
        } else if (module.id().equals(ModuleId.of("holograms"))) {
            wireHolograms(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings);
        } else if (module.id().equals(ModuleId.of("playerwarps"))) {
            wirePlayerwarps(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings);
        } else if (module.id().equals(ModuleId.of("scoreboard"))) {
            wireScoreboard(plugin, ctx, resources, links, guiLayouts, guiRegistry, menus);
        } else if (module.id().equals(ModuleId.of("tablist"))) {
            wireTablist(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("vote"))) {
            wireVote(plugin, ctx, persistence, resources, links, bus, guiRegistry, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("discordlink"))) {
            wireDiscordlink(plugin, ctx, persistence, resources, links, guiLayouts, guiRegistry, menus);
        } else if (module.id().equals(ModuleId.of("nametags"))) {
            wireNametags(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("staff"))) {
            wireStaff(plugin, ctx, persistence, resources, links, guiRegistry, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("npc"))) {
            wireNpc(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    bus,
                    guiLayouts,
                    guiRegistry,
                    textInput,
                    menus,
                    menuBindings);
        } else if (module.id().equals(ModuleId.of("custommenus"))) {
            wireCustomMenus(plugin, ctx, resources, guiLayouts, guiRegistry, textInput, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("customcommands"))) {
            wireCustomCommands(plugin, ctx, resources, menus, textInput);
        } else if (module.id().equals(ModuleId.of("poses"))) {
            wirePoses(plugin, ctx, resources, links, guiLayouts, guiRegistry, menus, claimProviders);
        } else if (module.id().equals(ModuleId.of("survival"))) {
            wireSurvival(plugin, ctx, resources, links, guiLayouts, guiRegistry, menus);
        } else if (module.id().equals(ModuleId.of("ranks"))) {
            wireRanks(plugin, ctx, persistence, resources, links, guiRegistry, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("trade"))) {
            wireTrade(plugin, ctx, persistence, resources, textInput, links, bus, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("security"))) {
            wireSecurity(
                    plugin,
                    ctx,
                    persistence,
                    resources,
                    links,
                    textInput,
                    menus,
                    menuBindings,
                    Objects.requireNonNull(ipCapture, "ipCapture"));
        } else if (module.id().equals(ModuleId.of("commandcontrol"))) {
            wireCommandControl(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("villagers"))) {
            wireVillagers(plugin, ctx, resources, links, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("invrollback"))) {
            wireInvrollback(plugin, ctx, persistence, resources, links, menus, menuBindings);
        } else if (module.id().equals(ModuleId.of("regions"))) {
            wireRegions(plugin, ctx, resources, links, guiRegistry, menus, textInput, guiLayouts);
        } else if (module.id().equals(ModuleId.of("servertweaks"))) {
            wireServerTweaks(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("skin"))) {
            wireSkin(plugin, ctx, persistence, resources, links);
        }
    }

    private static void wireSkin(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        // skin builds its jOOQ repository over persistence.dsl() and registers the /skin command plus the pre-login
        // listener that dresses a joining player before their entity exists (no respawn, no re-send, no flicker).
        // Every lookup that leaves the server - a name at Mojang, an image at MineSkin, a Bedrock skin at the Geyser
        // endpoint - runs on the async pool through the Scheduler port, and the login path waits on it only for the
        // configured timeout, so a slow or missing service lets the player in undressed rather than costing them the
        // login. Floodgate is soft: the xuid comes through the already-resolved BedrockDetector, so a Java-only server
        // never names it. The listener stops dressing anybody through the Wired stop hook, so a disable or reload
        // strands no lookup against a live connection; a disabled module wires none of this.
        SkinWiring.Wired wired = SkinWiring.wire(
                ctx,
                persistence,
                plugin.getServer(),
                Objects.requireNonNullElse(resources.bedrock(), BedrockDetector.NONE),
                plugin.getDataFolder().toPath());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired.stop());
        // The published read is the player's own choice rather than the texture they wear: the blob means nothing
        // outside a client, while the source is what a consumer can act on. Same cached store /skin info reads.
        links.queries.register(
                UxmSkinQuery.class,
                new SkinQueries(wired.repository(), ctx.kernel().scheduler()));
        // A HUD that shows what somebody chose reads the same cache, so a scoreboard refresh costs no query.
        links.placeholders.skin(new RepositorySkinPlaceholders(wired.repository()));
    }

    private static void wireServerTweaks(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // servertweaks is a grab-bag of small server/infra tweaks, each gated by its own switch (both default off), so
        // the wiring registers only the effects an operator has opted into. When f3-brand is on, a join listener
        // re-sends the configured brand over the minecraft:brand channel so it shows on F3; when console-filter is on,
        // a Log4j2 filter is attached to the root logger to drop exactly the configured console spam. The context
        // persists nothing (each effect is a live side effect on the running server) and its stop hook unwinds every
        // one (unregister the channel, detach the filter) so a disable or reload leaves the server as it was found. A
        // disabled module wires none of this.
        ServerTweaksWiring.Wired wired = ServerTweaksWiring.wire(plugin, ctx);
        wired.listeners().forEach(resources::addListener);
        // The brand this server reports is the module's one publicly interesting value, and an operator usually
        // wants a scoreboard or a join line to repeat it rather than restate it in two places.
        links.placeholders.serverTweaks(new ConfigServerTweaksPlaceholders(
                ServerTweaksConfig.from(ctx.config()).f3Brand()));
        resources.onClose(wired::stop);
    }

    private static void wireRegions(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            TextInput textInput,
            GuiLayouts guiLayouts) {
        // regions manages WorldGuard regions behind a SOFT dependency. The wiring probes for the WorldGuard plugin and
        // binds either the reflective WorldGuardRegionService (WG present) or the NoWorldGuardRegionService no-op (WG
        // absent); the /regions command consults RegionService.available() and, on the no-op, replies "WorldGuard not
        // installed" and opens nothing, so the surface is inert without WorldGuard. The region-list panel rides the
        // shared menu engine's paginated list (no raw inventory), reading the region set + each region's priority and
        // roster counts off the tick thread on the global region thread. The context persists nothing and holds no
        // runtime state, so there is no stop hook. A disabled module wires none of this.
        RegionsWiring.Wired wired = RegionsWiring.wire(plugin, ctx, guiRegistry, menus, textInput, guiLayouts);
        wired.commands().forEach(resources::addCommand);
        // Read-only, and on the server thread rather than a worker: WorldGuard's region container is live state.
        // There is no action surface because editing a region is an operator act with its own audit trail.
        links.queries.register(
                UxmRegionsQuery.class,
                new RegionsQueries(
                        wired.service(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().scheduler()));
        // Which region a player stands in is the one regions fact a scoreboard asks for, so the same service the
        // command reads is handed to the expansion. Without WorldGuard the no-op service reports nothing available
        // and every key degrades to the dash, exactly as the command replies.
        links.placeholders.regions(new ServiceRegionsPlaceholders(plugin.getServer(), wired.service()));
    }

    private static void wireInvrollback(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Menus menus,
            MenuBindings menuBindings) {
        // invrollback builds its jOOQ SnapshotRepository over persistence.dsl() and registers the death/logout
        // capture listener, the read-only snapshot-preview listener, and the /invrestore staff command. A capture
        // reads the player's inventory on the tick thread, serializes it there, and hops the DB write off the tick
        // thread through the Scheduler.async port (Folia-safe); the table is bounded per player at write time
        // (deleteBeyondCount) and by age on a scheduled off-tick sweep (deleteOlderThan). /invrestore opens the
        // engine-backed snapshot list; selecting one previews it (a spec whose items sit in a read-only content
        // region) and a restore
        // safety-snapshots the pre-restore inventory before overwriting the target's live inventory. The only runtime
        // state is the repeating retention sweep, cancelled through the Wired stop hook so a disable/reload strands no
        // scheduled work; a disabled module wires none of this.
        InvrollbackWiring.Wired wired = InvrollbackWiring.wire(
                ctx, persistence, menus, menuBindings, plugin.getDataFolder().toPath());
        wired.listeners().forEach(resources::addListener);
        wired.commands().forEach(resources::addCommand);
        resources.onClose(wired.stop());
        // The published restore runs the same three-hop flow the GUI button does, safety copy and all, so a plugin
        // cannot overwrite an inventory in a way staff could not then undo.
        links.queries.register(
                UxmInvRollbackQuery.class,
                new InvRollbackQueries(wired.repository(), ctx.kernel().scheduler()));
        links.actions.register(
                UxmInvRollbackActions.class,
                source -> new InvRollbackActions(wired.restorer(), ctx.kernel().playerLookup()));
        // When this enable last snapshotted the player, read off the capture listener's own record rather than the
        // snapshot table, so a staff HUD line never turns into a query.
        links.placeholders.invrollback(wired.placeholders());
    }

    private static void wireVillagers(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            Menus menus,
            MenuBindings menuBindings) {
        // villagers persists nothing relational: the last-restock stamp, the disable flag, and the manager's custom
        // recipe set are all PDC state on the villager entity, and the config is read once into an immutable snapshot.
        // Each feature wires only when its config switch is on, the trade listener under infinite/instant restock, the
        // restock sweep under the restock timer, the /villager manager command + its menu-engine window + the
        // load-time recipe reapply under trade-manager, and the click-to-trade listener under click-to-trade, while the
        // disable-trades listener always registers so it can honour the per-villager flag the manager sets. The sweep's
        // repeating task is cancelled and any open manager window drained on module stop through the Wired stop hook so
        // a disable strands no scheduled work and loses no edit.
        VillagersWiring.Wired wired = VillagersWiring.wire(
                ctx,
                plugin.getServer(),
                menus,
                menuBindings,
                plugin.getDataFolder().toPath());
        wired.listeners().forEach(resources::addListener);
        wired.commands().forEach(resources::addCommand);
        // Present only when the follow sub-feature wired: with nothing able to follow, there is nothing to count.
        VillagersPlaceholders villagers = wired.placeholders();
        if (villagers != null) {
            links.placeholders.villagers(villagers);
        }
        resources.onClose(wired.stop());
    }

    private static void wireSecurity(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            IpCapture ipCapture) {
        // security stands up the DB-backed two-factor store (the PIN hashed, the TOTP secret AES-encrypted under a
        // key-file kept beside the module's config) and publishes the /2fa and /pin enrolment verbs over the shared
        // persistence DSL (through the security persistence factory, so no jOOQ type reaches this layer). Phase 2 adds
        // the join-verification freeze: an enrolled player on an untrusted device is frozen and made to prove a factor
        // through the keypad GUI (or the TOTP text prompt) before they can act, with a device-trust store and a
        // failure lockout. The transient freeze/enrolment state is cleared and every keypad closed on stop, so a
        // disable or reload leaves no residual secret and no locked player.
        // The lockout escalation goes through moderation's own tempban when that module is enabled, so it lands in
        // the same ban table, history and staff broadcast as every other ban; with moderation off it binds to NONE
        // and the lockout stays the in-memory cooldown.
        LockoutBan lockoutBan = links.securityLockoutBan == null
                ? LockoutBan.NONE
                : new ModerationLockoutBan(
                        links.securityLockoutBan,
                        CommandFeedback.refOf(plugin.getServer().getConsoleSender()));
        // The optional post-verify transfer reuses the one proxy channel the menu connect action already built; a
        // hot-reload that reaches this wiring before that substrate exists builds its own over the same channel, so
        // the transfer works either way and degrades to a logged no-op with no proxy in front.
        ServerConnector resolvedProxy = resources.serverConnector();
        ServerConnector proxy = resolvedProxy != null
                ? resolvedProxy
                : new BukkitServerConnector(plugin, ctx.kernel().log());
        SecurityWiring.Wired wired = SecurityWiring.wire(
                plugin,
                ctx,
                persistence,
                textInput,
                menus,
                menuBindings,
                lockoutBan,
                proxy,
                ipCapture.store(),
                ipCapture.tokens(),
                ipCapture.recorder());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.worldPhase().run("security holding area", wired.holdingArea()::warmUp);
        resources.onClose(wired::stop);
        // The read reports the shape of a registration and never its material, and the two writes both go in the
        // safe direction: make somebody prove themselves again, or let somebody back in early.
        links.queries.register(
                UxmSecurityQuery.class,
                new SecurityQueries(
                        wired.repository(), wired.limiter(), ctx.kernel().scheduler(), wired.clock()));
        links.actions.register(
                UxmSecurityActions.class,
                source -> new SecurityActions(
                        wired.forceReverification(),
                        wired.limiter(),
                        ctx.kernel().scheduler()));
        // Only the in-memory challenge state is exposed to placeholders. What factors an account has enrolled is a
        // database row, and a scoreboard refreshing every second must never become a query.
        links.placeholders.security(new SessionSecurityPlaceholders(
                wired.sessions(),
                SecurityConfig.from(ctx.config()).joinVerification().enabled()));
    }

    private static void wireCommandControl(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // commandcontrol persists nothing: the whitelist/blacklist rule set and the plugin-hide policy are derived once
        // from the module's config into immutable snapshots, so a hot-reload re-runs this wiring and registers fresh
        // listeners. The gate listener consults the rule set on PlayerCommandPreprocessEvent and, on deny, cancels the
        // command and sends the configured deny line; the visibility listener scrubs the sent command list, tab
        // completion, and the scrub-help output so disallowed and hidden commands stay invisible. Both read the
        // player's group (LuckPerms when installed, empty otherwise) and permission facts. There is no runtime state to
        // drain on stop: unregistering the listeners is enough.
        CommandControlWiring.Wired wired = CommandControlWiring.wire(plugin.getServer(), ctx);
        wired.listeners().forEach(resources::addListener);
        // The published check answers from the same rules and the same facts the gate uses, so a plugin hiding a
        // button agrees with what happens when the player types the command instead of guessing alongside it.
        links.queries.register(
                com.uxplima.uxmessentials.api.query.UxmCommandControlQuery.class,
                new com.uxplima.uxmessentials.commandcontrol.adapter.outbound.api.CommandControlQueries(
                        wired.worldRules(),
                        wired.blockNamespaceBypass(),
                        wired.groups(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // The same check, readable from a menu requirement or a HUD line: a button hidden by the placeholder and a
        // command refused by the gate are then the same decision rather than two that drift apart.
        links.placeholders.commandControl(new RulesCommandControlPlaceholders(
                plugin.getServer(), wired.worldRules(), wired.blockNamespaceBypass(), wired.groups()));
    }

    private static void wireTrade(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            TextInput textInput,
            ContextLinks links,
            Bus bus,
            Menus menus,
            MenuBindings menuBindings) {
        // trade persists nothing same-server: a live trade is transient in-memory state (the TradeSessions registry and
        // its per-trade TradeExchange), and the config is read once into an immutable snapshot. The window view and its
        // click/drag/close/quit listener stand up here; Phase 3 adds the money row over the shared TextInput seam and
        // the trade economy port, bridged from the provider captured at economy-wiring time (trade registers after
        // economy), or left empty when economy is disabled, in which case a trade moves items only. closeAll() drains
        // every live trade on module stop or reload, returning both sides' offered items, so a disable leaves
        // nothing.
        var provider = links.economyProvider;
        var currency = links.economyCurrency;
        @org.jspecify.annotations.Nullable TradeEconomy economy = provider != null && currency != null
                ? new ProviderTradeEconomy(provider, currency, ctx.kernel().log(), receipts(ctx))
                : null;
        TradeWiring.Wired wired = TradeWiring.wire(
                ctx,
                textInput,
                economy,
                persistence,
                bus,
                menus,
                menuBindings,
                plugin.getDataFolder().toPath());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::closeAll);
        // The live registry is the whole store a same-server trade has, so the published query reads it directly.
        links.queries.register(UxmTradeQuery.class, new TradeQueries(wired.sessions()));
        // Captured for the relational trade placeholders, which are wired unconditionally after the modules.
        links.tradeSessions = wired.sessions();
    }

    private static void wireRanks(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        // ranks stands up the DB-backed rank pointer, the parsed ladder and the CurrentRank read over the shared
        // persistence DSL (through the ranks persistence factory, so no jOOQ type reaches this layer), then the
        // /rankup and /ranks setrank verbs over the Rankup/SetRank use cases. The rank cost charges through the
        // economy provider captured at economy-wiring time (ranks registers after economy), bridged here into the
        // narrow RankEconomy seam, or left empty when economy is disabled, in which case a priced rank is free. The
        // config-gated /ranks ladder panel registers its spec and bindings into the shared menu engine when on.
        var provider = links.economyProvider;
        var currency = links.economyCurrency;
        Optional<RankEconomy> economy = provider != null && currency != null
                ? Optional.of(new ProviderRankEconomy(provider, currency, receipts(ctx)))
                : Optional.empty();
        RanksWiring.Wired wired = RanksWiring.wire(plugin, ctx, persistence, economy, guiRegistry, menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        // The autorank scan's repeating task is cancelled on module stop so a disable strands no scheduled work.
        resources.onClose(wired.stop());
        // The rank / next-rank / prestige placeholders read the DB-backed pointer through the CurrentRank use case and
        // the parsed ladder, so they answer for an offline player too.
        links.placeholders.ranks(new StoreRanksPlaceholders(wired.currentRank(), wired.ladder()));
        links.queries.register(
                UxmRanksQuery.class,
                new RanksQueries(
                        wired.currentRank(),
                        wired.ladder(),
                        wired.requirements(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                UxmRanksActions.class,
                source -> new RanksActions(
                        wired.apiWrites(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
    }

    private static void wireSurvival(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus) {
        // survival persists nothing: the per-player mechanic toggles are transient PDC stamps and the config is
        // read once into an immutable snapshot. Each mechanic wires only when its config gate is on, so a disabled
        // tree-feller or veinminer contributes no command and no listener. Its one cross-context collaborator is the
        // economy provider auto-sell credits through: survival registers after economy, so the provider captured at
        // economy-wiring time is bridged here into the narrow SurvivalSales seam, or left empty when economy is
        // disabled, in which case auto-sell is inert. There is no runtime state to drain on stop.
        var provider = links.economyProvider;
        var currency = links.economyCurrency;
        Optional<SurvivalSales> sales = provider != null && currency != null
                ? Optional.of(new ProviderSurvivalSales(provider, currency))
                : Optional.empty();
        SurvivalWiring.Wired wired =
                SurvivalWiring.wire(ctx, sales, guiLayouts, guiRegistry, menus, plugin.getServer());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The per-player mechanic switches are what a HUD wants to show ("auto-pickup: on"), so the same toggle
        // store the listeners read is handed to the expansion; with survival off the seam stays absent.
        links.placeholders.survival(wired.placeholders());
    }

    private static void wirePoses(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            ClaimProvidersConfig claimProviders) {
        // Sit on blocks (/sit) and, when features.player-sit is on, sit on players (right-click, /poses toggle to
        // opt out). The seat is a real, tagged, non-persistent marker armour stand for block-sits; a player-sit has
        // no seat entity: the rider mounts straight onto the carrier and addPassenger chains for stacking.
        // sweepOrphans() runs on enable to reap any seat a prior crash left behind (ghost-prevention), and stop()
        // drains every live seat and clears the registry so a disable or reload leaves zero residual state and no
        // ghost entity. PoseSessions is the single source of truth for who is posing; it plus the PDC-backed
        // player-sit opt-out feed the poses_sitting / poses_toggle placeholder seams. The region gate now respects
        // land claims (the shared ClaimService) and WorldGuard's sit/playersit/pose/crawl flags, governed by the
        // respect-claims / respect-worldguard config toggles; the WorldGuard flags themselves are registered at load
        // (UxmEssentialsPlugin.onLoad), before WorldGuard locks its registry. A bare /poses opens a personal
        // settings/status panel (also on the /uxmess gui hub), and every pose start passes an optional shared
        // uxmessentials.poses.cooldown.<seconds> gate before it begins.
        PosesWiring.Wired wired = PosesWiring.wire(plugin, ctx, guiLayouts, guiRegistry, menus, claimProviders);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.seats().sweepOrphans();
        resources.onClose(wired::stop);
        links.placeholders.poses(new StorePosesPlaceholders(wired.sessions(), wired.playerSitPreferences()));
    }

    private static void wireCustomMenus(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // custommenus consumes the always-on menu engine (the façade + bindings built in PluginModule): it loads the
        // operator's menus/*.conf into the engine on enable and registers the /menu command. There is no per-context
        // repository or listener (the single menu click listener is installed once in bootstrap) so the wiring is a
        // loader run plus the command. The console-dispatch flag is read once in PluginModule and threaded into the
        // engine's action vocabulary there, not here. The /menu editor picker (and the /uxmess gui hub entry) opens
        // through the shared GUI framework, so the layouts, the text-input seam and the hub registry are threaded in.
        CustomMenusWiring.Wired wired = CustomMenusWiring.wire(
                plugin,
                menus,
                menuBindings,
                plugin.getDataFolder().toPath(),
                ctx.kernel().log(),
                ctx.kernel().scheduler(),
                ctx.kernel().messages(),
                new GuiText(ctx.kernel().messages()),
                guiLayouts,
                textInput,
                guiRegistry);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
    }

    private static void wireCustomCommands(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, Menus menus, TextInput textInput) {
        // customcommands consumes the always-on menu engine the same way custommenus does: it reads the operator's
        // commands/custom/*.conf into the domain and registers one Brigadier command per definition. Registering
        // here, inside module wiring, is what puts the registrations in front of applyCatalog, so a custom command
        // lands in commands.conf beside the built-in ones and inherits its rename, alias and per-locale alias
        // handling. The multi-currency facade built above prices a definition that declares a cost.
        Currencies currencies = resources.currencies();
        if (currencies == null) {
            ctx.kernel().log().warn("custom commands wired before the currency facade; priced commands will run free");
            return;
        }
        CustomCommandsWiring.Wired wired = CustomCommandsWiring.wire(
                ctx, menus, currencies, plugin.getDataFolder().toPath(), textInput::prompt);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
    }

    private static void wireTeleport(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings,
            ClaimProvidersConfig claimProviders) {
        // The /rtp cost bridges to the resolved economy provider lazily: teleport is wired before economy, so the
        // provider/currency are read through suppliers at charge time (free until economy is up, free for good when
        // economy is disabled), mirroring the worlds entry fee. The affordability is checked before the search and the
        // debit is dispatched off-tick only after a successful landing.
        LinkedTeleportFee fee = new LinkedTeleportFee(
                () -> links.economyProvider,
                () -> links.economyCurrency,
                ctx.kernel().scheduler(),
                ctx.kernel().log(),
                receipts(ctx));
        TeleportWiring.Wired wired = TeleportWiring.wire(
                plugin, ctx, persistence, guiLayouts, guiRegistry, menus, menuBindings, fee, claimProviders);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.worldPhase().run("rtp pool warmup", wired::warmRtpPool);
        resources.onClose(wired::stop);
        links.teleportEngine = wired.services().engine();
        // The teleport PAPI seam reads the same cooldown gate, warmup tracker, /back store, tpa registry and
        // /tptoggle flags the teleport commands do, so a placeholder matches what the player experiences.
        links.placeholders.teleport(new ServicesTeleportPlaceholders(
                ctx.kernel().cooldowns(),
                wired.services().warmupTracker(),
                wired.services().backStore(),
                wired.services().requests(),
                wired.services().flags(),
                java.time.Clock.systemUTC()));
        links.queries.register(
                UxmTeleportQuery.class,
                new TeleportQueries(
                        wired.services().requests(),
                        wired.services().backStore(),
                        ctx.kernel().playerLookup()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmTeleportActions.class,
                source -> new com.uxplima.uxmessentials.teleport.adapter.outbound.api.TeleportActions(
                        wired.services().executor(),
                        wired.services().captureBack(),
                        wired.services().settings(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().permissions(),
                        ctx.kernel().scheduler()));
        // Captured for staff (wired last), which binds its COMPASS gadget and /stafflist to this admin engine.
        links.staffTeleport = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.TeleportSeam(
                wired.services().engine());
        // Captured for moderation, which lands later and rebinds this jail gate to the real jail policy.
        links.jailGate = wired.jailGate();
        // Captured for homes, which lands later and rebinds this seam so the respawn chain's HOME step resolves.
        links.homeRespawnLocator = wired.homeRespawnLocator();
        links.warpRespawnLocator = wired.warpRespawnLocator();
        // Captured for the worlds void rescue, whose "spawn" step must land exactly where /spawn would.
        links.spawnResolver = wired.services().resolveSpawn()::resolveDefault;
    }

    private static void wireWorlds(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // worlds builds its cached jOOQ WorldRepository over persistence.dsl() and its BukkitWorldEngine over the
        // plugin's Server. It delegates /worlds tp and /worlds spawn execution to the captured teleport engine (wired
        // earlier) and charges the per-world entry fee through the economy bridge, but economy lands after worlds, so
        // the fee resolves its provider/currency lazily at charge time (free until economy is up, free for good when
        // economy is disabled). The enable-time reconcile (adopt already-loaded worlds, auto-load registered ones) is
        // kicked on the global region thread the moment wiring completes, then drops the warm snapshot and refreshes
        // the
        // import-folder candidates off-tick. The in-process bus is the concrete publisher so the live-apply subscriber
        // (re-apply stored settings on world load / setting change) can be registered here and unsubscribed on stop;
        // the
        // kernel port exposes only publish.
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine,
                "worlds /worlds tp and /worlds spawn delegate teleport execution but the teleport engine is unavailable");
        WorldEntryFee entryFee = new LinkedWorldEntryFee(() -> links.economyProvider, () -> links.economyCurrency);
        InProcessDomainEventPublisher events =
                (InProcessDomainEventPublisher) ctx.kernel().events();
        // The void rescue resolves its "spawn" step through the same /spawn resolution the teleport context uses
        // and its "warp:" step through the warps snapshot, both captured as seams so worlds keeps its single
        // documented teleport dependency and a disabled warps module simply resolves no warp step.
        var spawnResolver = Objects.requireNonNull(
                links.spawnResolver,
                "the worlds void rescue resolves spawns through the teleport context but it is unavailable");
        MutableWarpRespawnLocator warpLocator = links.warpRespawnLocator;
        RescueTargets rescueTargets = new TeleportRescueTargets(
                spawnResolver, name -> warpLocator == null ? Optional.empty() : warpLocator.respawnWarp(name));
        WorldsWiring.Wired wired = WorldsWiring.wire(
                ctx,
                persistence,
                plugin.getServer(),
                events,
                engine,
                entryFee,
                guiLayouts,
                textInput,
                menus,
                menuBindings,
                rescueTargets,
                plugin.getDataFolder().toPath());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // Capture the generator resolver for the plugin's getDefaultWorldGenerator hook before kicking the
        // reconcile: auto-load may load a world declaring generator: uxmEssentials:void|flat, which routes
        // back through that hook, so the resolver must be reachable first.
        resources.worldGeneratorResolver(wired.generatorResolver());
        links.placeholders.worlds(wired.worldsPlaceholders());
        links.queries.register(
                UxmWorldsQuery.class,
                new WorldQueries(
                        wired.repository(),
                        wired.engine(),
                        wired.accessPolicy(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmWorldsActions.class,
                source -> new com.uxplima.uxmessentials.worlds.adapter.outbound.api.WorldActions(
                        wired.apiWrites(), ctx.kernel().scheduler(), source));
        wired.startReconcile().run();
        resources.onClose(wired.stop());
        // Open the same /world gui world picker from the management hub, gated by the existing world-gui node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "worlds",
                com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey.LIST_TITLE,
                Material.GRASS_BLOCK,
                "uxmessentials.world.gui",
                (player, viewer) -> wired.openWorldList().accept(player, viewer)));
    }

    private static void wireHomes(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            ClaimProvidersConfig claimProviders) {
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine, "homes delegates teleport execution but the teleport engine is unavailable");
        HomesWiring.Wired wired = HomesWiring.wire(
                plugin,
                ctx,
                persistence,
                engine,
                Optional.ofNullable(links.homeEconomy),
                bus,
                guiLayouts,
                resources,
                textInput,
                menus,
                menuBindings,
                claimProviders);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // Rebind the teleport context's home-respawn seam (built while it still resolved to empty) to the
        // cache-backed locator, so a HOME step in a configured respawn chain now lands on the player's home.
        // When teleport is disabled its holder is absent and this bind is a no-op.
        bindHomeRespawn(links, new RepositoryHomeRespawnLocator(wired.repository()));
        links.placeholders.homes(new RepositoryHomesPlaceholders(wired.repository(), wired.quota()));
        links.queries.register(
                UxmHomesQuery.class,
                new HomeQueries(
                        wired.repository(),
                        wired.quota(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                UxmHomeActions.class,
                source -> new com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeActions(
                        wired.apiWrites(),
                        wired.repository(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().scheduler()));
        // Open the same /home slot-grid menu from the management hub, gated by the existing home-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "homes",
                com.uxplima.uxmessentials.homes.application.HomesMessageKey.HOME_MENU_TITLE,
                Material.RED_BED,
                "uxmessentials.home.use",
                (player, viewer) -> wired.listView().open(viewer)));
    }

    private static void bindHomeRespawn(ContextLinks links, HomeRespawnLocator locator) {
        MutableHomeRespawnLocator holder = links.homeRespawnLocator;
        if (holder != null) {
            holder.bind(locator);
        }
    }

    private static void wireEconomy(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            Hooks hooks,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            AtomicReference<EconomyBackends> menuCurrencyBackends) {
        EconomyWiring.Wired wired = EconomyWiring.wire(
                plugin,
                ctx,
                persistence,
                bus,
                hooks,
                textInput,
                links.playerPicker,
                menus,
                menuBindings,
                plugin.getDataFolder().toPath());
        // Fill the deferred reference the menu-currency façade was handed at menu-wiring time (above): from here on
        // a give-money click resolves against the same backend and currency registries a warp fee spends through.
        menuCurrencyBackends.set(new EconomyBackends(wired.backends(), wired.currencies()));
        links.economyProvider = wired.provider();
        links.economyCurrency = wired.defaultCurrency();
        links.economyCurrencies = wired.currencies();
        links.economyBackends = wired.backends();
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.start();
        resources.onClose(wired::stop);
        // Open the same /wallet dashboard from the management hub, gated by the existing wallet node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "economy",
                com.uxplima.uxmessentials.economy.application.EconomyMessageKey.WALLET_GUI_TITLE,
                Material.GOLD_INGOT,
                "uxmessentials.economy.wallet",
                (player, viewer) -> wired.walletView().open(player, wired.defaultCurrency())));
        // Captured for warps, kits, homes, and vaults, which land after economy and charge a recorded cost
        // through it.
        links.warpEconomy = wired.warpEconomy();
        links.kitEconomy = wired.kitEconomy();
        links.homeEconomy = wired.homeEconomy();
        links.vaultEconomy = wired.vaultEconomy();
        links.npcEconomy = wired.npcEconomy();
        links.placeholders.economy(new ProviderEconomyPlaceholders(
                wired.provider(), wired.defaultCurrency(), wired.amountFormat(), BalTop.MAX_PAGE_SIZE));
        links.queries.register(
                UxmEconomyQuery.class,
                new EconomyQueries(
                        wired.provider(),
                        wired.currencies(),
                        wired.snapshots(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                UxmEconomyActions.class,
                source -> new com.uxplima.uxmessentials.economy.adapter.outbound.api.EconomyActions(
                        wired.admin(),
                        wired.provider(),
                        wired.currencies(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler(),
                        source));
        // Publish a balance leaderboard source for the holograms module (wired later): the lock-free baltop
        // snapshot, mapped into ranked name/score rows. The composition root is the only place that may bridge
        // the two contexts, so the provider is a lambda here rather than a class in either context.
        BaltopSnapshots baltop = wired.snapshots();
        Currency currency = wired.defaultCurrency();
        links.balanceLeaderboard = limit -> baltop.top(currency, limit).stream()
                .map(row -> new LeaderboardEntry(row.owner().name(), MoneyFormat.withSymbol(row.balance())))
                .toList();
    }

    private static void bindWarpRespawn(ContextLinks links, RepositoryWarpRespawnLocator locator) {
        MutableWarpRespawnLocator holder = links.warpRespawnLocator;
        if (holder != null) {
            holder.bind(locator);
        }
    }

    private static void wireWarps(
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine, "warps delegates teleport execution but the teleport engine is unavailable");
        WarpsWiring.Wired wired = WarpsWiring.wire(
                ctx,
                persistence,
                engine,
                Optional.ofNullable(links.warpEconomy),
                bus,
                guiLayouts,
                textInput,
                menus,
                menuBindings);
        links.warpEditorView = wired.editorView();
        links.warpPlayerWarpHandle = wired.playerWarpHandle();
        links.warpPlayerWarpGoTo = wired.playerWarpGoTo();
        links.warpTeleportRegistry = wired.teleportRegistry();
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        bindWarpRespawn(links, new RepositoryWarpRespawnLocator(wired.repository()));
        links.placeholders.warps(new RepositoryWarpsPlaceholders(wired.listWarps()));
        links.queries.register(
                UxmWarpsQuery.class,
                new WarpQueries(
                        wired.repository(),
                        wired.listWarps(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                UxmWarpActions.class,
                source -> new com.uxplima.uxmessentials.warps.adapter.outbound.api.WarpActions(
                        wired.setWarp(),
                        wired.moveWarp(),
                        wired.delWarp(),
                        wired.repository(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().scheduler(),
                        source));
        // Open the same /warp list browse menu from the management hub, resolving the viewer's visible warps
        // through the identical ListWarps filter the command uses, gated by the existing warp-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "warps",
                com.uxplima.uxmessentials.warps.application.WarpsMessageKey.WARP_MENU_TITLE,
                Material.ENDER_PEARL,
                "uxmessentials.warp.use",
                (player, viewer) ->
                        wired.warpMenu().open(player, viewer, wired.listWarps().available(viewer))));
    }

    private static void wireKits(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // kits need no database: definitions live in modules/kits/kits/<id>.conf and claim/cooldown state
        // is transient PDC.
        // The per-kit cost charges through the economy bridge captured during economy wiring when present. The admin
        // /kit editor manager renders through the always-on menu engine, so the engine façade + bindings are handed in.
        KitsWiring.Wired wired = KitsWiring.wire(
                plugin, ctx, Optional.ofNullable(links.kitEconomy), guiLayouts, textInput, menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.kits(new KitAccessPlaceholders(wired.repository(), wired.access(), wired.listKits()));
        links.queries.register(
                UxmKitsQuery.class,
                new KitQueries(
                        wired.repository(),
                        wired.access(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                UxmKitActions.class,
                source -> new com.uxplima.uxmessentials.kits.adapter.outbound.api.KitActions(
                        wired.repository(),
                        wired.granter(),
                        wired.claimKit(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // Open the same /kit browse menu from the management hub, resolving the viewer's available kits through
        // the identical ListKits filter the command uses, gated by the existing kit-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "kits",
                com.uxplima.uxmessentials.kits.application.KitsMessageKey.KIT_MENU_TITLE,
                Material.CHEST,
                "uxmessentials.kit.use",
                (player, viewer) ->
                        wired.kitMenu().open(player, viewer, wired.listKits().available(viewer))));
    }

    private static void wirePlayerstate(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            Menus menus,
            MenuBindings menuBindings) {
        // playerstate's only durable state is the per-day playtime ledger behind /playtime, built over
        // persistence.dsl(); the per-player snapshot map stays transient in-memory and all live-player
        // reconciliation routes through the kernel Scheduler port onto the owning region thread. The AFK-aware
        // playtime sampler is armed below and stopped on module disable, leaving no orphaned tick.
        PlaytimeRepository playtimeRepository = PlaytimeRepositories.jooq(persistence);
        MirrorWindow mirrorWindow = new MirrorWindow(
                ctx.kernel().messages(),
                menus,
                ctx.kernel().scheduler(),
                plugin.getDataFolder().toPath(),
                ctx.kernel().log());
        mirrorWindow.register(menuBindings);
        PlayerstateWiring.Wired wired =
                PlayerstateWiring.wire(plugin, ctx, playtimeRepository, guiLayouts, menus, mirrorWindow, links.teams);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.playerstate(new StorePlayerstatePlaceholders(wired.store(), wired.info()));
        links.queries.register(
                UxmPlaytimeQuery.class,
                new PlaytimeQueries(playtimeRepository, ctx.kernel().scheduler(), java.time.Clock.systemUTC()));
        links.queries.register(
                UxmPlayerStateQuery.class,
                new PlayerStateQueries(wired.store(), ctx.kernel().playerLookup()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmPlayerStateActions.class,
                source -> new com.uxplima.uxmessentials.playerstate.adapter.outbound.api.PlayerStateActions(
                        com.uxplima.uxmessentials.playerstate.adapter.outbound.api.PlayerStateApiWrites.of(
                                wired.services()),
                        wired.store(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler(),
                        source));
        // Captured for staff (wired last), which binds its EXAMINE gadget to this /invsee open use case.
        links.staffOpenContainer = wired.services().openContainer();
        // Captured so presence (wired later) rebinds the playtime sampler's AFK seam to its live store, so the
        // sampler splits active vs AFK seconds. Until then, or when presence is disabled, it counts all active.
        links.playtimeAfkStatus = wired.afkStatus();
    }

    private static void wireMessaging(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // messaging builds its jOOQ mail/ignore stores over persistence.dsl() and its transient reply /
        // socialspy / toggle stores in-memory/PDC. The mute gate starts on MutePolicy.NEVER and is captured
        // here so moderation rebinds it when it lands; the vanish gate degrades to "fully visible" without
        // presence.
        // The management GUIs consume the SP0 framework: a GuiText over the shared catalog, the data-folder layout
        // loader, and the shared text-input seam (installed once in wireModules) for the ignore-list add prompt.
        // /msgsettings opens the settings panel; /ignore and /mail with no args open the ignore-list and mailbox.
        GuiText guiText = new GuiText(ctx.kernel().messages());
        // The vanish gate reads the one vanish authority captured during vanish wiring (which lands before messaging),
        // or degrades to ALWAYS_VISIBLE ("no one is hidden") when the vanish module is disabled.
        com.uxplima.uxmessentials.vanish.application.port.VanishStore vanishStore = links.vanishStore;
        com.uxplima.uxmessentials.messaging.application.port.VanishVisibility vanish = vanishStore == null
                ? com.uxplima.uxmessentials.messaging.application.port.VanishVisibility.ALWAYS_VISIBLE
                : new com.uxplima.uxmessentials.messaging.adapter.outbound.AuthorityVanishVisibility(
                        vanishStore, java.util.Objects.requireNonNull(links.vanishLevelResolver));
        MessagingWiring.Wired wired = MessagingWiring.wire(
                plugin,
                ctx,
                persistence,
                Optional.empty(),
                vanish,
                bus,
                guiText,
                guiLayouts,
                textInput,
                menus,
                menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.mutePolicy = wired.mutePolicy();
        links.afkStatus = wired.afkStatus();
        // The messaging PAPI seam reads the same mail/conversation/toggle/socialspy/ignore stores the messaging
        // commands hold, so a placeholder matches the player's in-game mail count and toggle state.
        links.placeholders.messaging(wired.placeholders());
        links.queries.register(
                UxmMessagingQuery.class,
                new MessagingQueries(
                        wired.stores().mail(),
                        wired.stores().ignores(),
                        wired.stores().toggles(),
                        wired.stores().socialSpy(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmMessagingActions.class,
                source -> new com.uxplima.uxmessentials.messaging.adapter.outbound.api.MessagingActions(
                        wired.apiWrites(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler(),
                        source));
        // Register two /uxmess gui hub entries, the settings panel and the mailbox, gated by the messaging GUI
        // node. The ignore-list opens from /ignore with no args; it is not a hub entry of its own.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "messaging-settings",
                com.uxplima.uxmessentials.messaging.application.MessagingMessageKey.GUI_SETTINGS_TITLE,
                Material.WRITABLE_BOOK,
                "uxmessentials.messaging.gui",
                (player, viewer) -> wired.guiViews().openSettings(player, viewer)));
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "messaging-mailbox",
                com.uxplima.uxmessentials.messaging.application.MessagingMessageKey.GUI_MAIL_TITLE,
                Material.PAPER,
                "uxmessentials.messaging.gui",
                (player, viewer) -> wired.guiViews().openMailbox(player, viewer)));
        // Captured for staff (wired last), which binds its staff chat to the messaging staff-audience resolver.
        links.staffAudience = new com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience();
    }

    private static void wireModeration(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            IpCapture ipCapture) {
        // moderation builds its jOOQ ModerationRepository over persistence.dsl(), the audit logger on the
        // dedicated audit channel, and the login/join/freeze listeners. It rebinds the messaging mute gate and
        // the teleport jail gate captured during their wiring to the real policies. When either context is
        // disabled its holder is absent, so the bind is a no-op and that gate stays NEVER. It opts into
        // cross-server live enforcement through the bus handle: a ban on a peer kicks the player here if they
        // are online (the durable ban is already enforced on every backend's login regardless of the bus).
        // The management GUI consumes the SP0 framework (a GuiText over the shared catalog, the data-folder
        // layout loader): /mod with no args and the /uxmess gui hub both open the active-punishments list.
        ModerationWiring.GateSinks gates =
                new ModerationWiring.GateSinks(policy -> bindMute(links, policy), gate -> bindJail(links, gate));
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(
                        ctx.kernel().messages());
        ModerationWiring.Wired wired = ModerationWiring.wire(
                plugin,
                ctx,
                persistence,
                gates,
                bus,
                guiText,
                guiLayouts,
                textInput,
                Objects.requireNonNull(links.playerPicker, "playerPicker"),
                menus,
                menuBindings,
                plugin.getDataFolder().toPath(),
                ipCapture.store(),
                ipCapture.tokens());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.moderation(new GateModerationPlaceholders(
                wired.mutePolicy(), wired.jailGate(), wired.repository(), wired.sanctions(), wired.clock()));
        links.queries.register(
                UxmModerationQuery.class,
                new ModerationQueries(
                        wired.repository(),
                        wired.sanctionHistory(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler(),
                        wired.clock()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmModerationActions.class,
                source -> new com.uxplima.uxmessentials.moderation.adapter.outbound.api.ModerationActions(
                        wired.apiWrites(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler(),
                        source));
        // Register the moderation management GUI on the /uxmess gui hub, gated by the moderation GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "moderation",
                com.uxplima.uxmessentials.moderation.application.ModerationMessageKey.MOD_GUI_LIST_TITLE,
                org.bukkit.Material.IRON_BARS,
                "uxmessentials.moderation.gui",
                (player, viewer) -> wired.guiViews().open(player, viewer)));
        // Captured for staff (wired last), which binds its FREEZE gadget to the audited freeze use case and the
        // live freeze-state read (BukkitSanctions is the Sanctions adapter).
        links.staffModerationFreeze = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.ModerationFreezeSeam(
                wired.freeze(), wired.sanctions());
        // Captured for security (wired later): a verification lockout is issued as an ordinary tempban here rather
        // than the security module keeping a private ban list staff cannot see or lift.
        links.securityLockoutBan = wired.tempBan();
    }

    private static void wireItemworld(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // itemworld persists nothing: it is the full item/world command surface as stateless ACL-thin
        // mutations validated at the adapter boundary and applied through the kernel Scheduler. The only runtime
        // state is the powertool/unlimited per-player toggles and the item-PDC powertool bindings, all transient
        // and dropped with the wiring on module stop. /repair /repairall /hat /more are owned here (playerstate
        // deferred them, §15.6), so they register here and the two modules never double-register. The utilities
        // hub, the /recipe grid, and the /entitycount tally render through the shared menu engine.
        ItemworldWiring.Wired wired = ItemworldWiring.wire(plugin, ctx, guiLayouts, textInput, menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // Write back any still-open in-inventory shulker view before the module stops, so no edit is lost on disable.
        resources.onClose(wired.shulkerView()::flushAll);
        // Register the itemworld utilities hub on the /uxmess gui hub, gated by the itemworld GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "itemworld",
                com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey.GUI_HUB_TITLE,
                Material.CRAFTING_TABLE,
                "uxmessentials.itemworld.gui",
                (player, viewer) -> wired.hubView().open(viewer)));
        // The one readable corner of an otherwise stateless module: the command bindings a player stamped onto
        // their own items, read from the same item PDC /powertoollist reads.
        links.queries.register(
                com.uxplima.uxmessentials.api.query.UxmItemworldQuery.class,
                new com.uxplima.uxmessentials.itemworld.adapter.outbound.api.ItemworldQueries(
                        wired.powertools(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // The same two personal switches and the same item binding, readable from a HUD rather than only from
        // /powertoollist.
        links.placeholders.itemworld(wired.placeholders());
    }

    private static void wireVaults(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            Bus bus,
            ContextLinks links,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        // vaults builds its cached jOOQ VaultRepository over persistence.dsl(), the audit logger on the dedicated
        // audit channel, the inventory-holder GUI and the InventoryClose save listener. It opts into cross-server
        // sync through the bus handle: a remote vault save invalidates exactly that vault here. The economy
        // bridge captured during economy wiring is handed in so a configured vault cost can be charged; when it
        // is absent a configured cost is recorded but never charged. The selector is registered with and opened
        // through the shared menu engine. On stop the still-open vault windows are close-and-saved before the pool
        // closes.
        VaultsWiring.Wired wired = VaultsWiring.wire(
                plugin, ctx, persistence, bus, Optional.ofNullable(links.vaultEconomy), menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.vaults(
                new RepositoryVaultsPlaceholders(wired.repository(), wired.amountQuota(), wired.sizeQuota()));
        links.queries.register(
                UxmVaultsQuery.class,
                new VaultQueries(
                        wired.repository(),
                        wired.amountQuota(),
                        wired.sizeQuota(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmVaultsActions.class,
                source -> new com.uxplima.uxmessentials.vaults.adapter.outbound.api.VaultActions(
                        wired.services().openVault(),
                        wired.services().deleteVault(),
                        wired.services().renameVault(),
                        wired.services().setVaultIcon(),
                        wired.view(),
                        wired.services().allowCustomIcon(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // Open the same /vault selector from the management hub, gated by the existing vault-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "vaults",
                com.uxplima.uxmessentials.vaults.application.VaultsMessageKey.VAULT_SELECTOR_TITLE,
                Material.ENDER_CHEST,
                "uxmessentials.vault.use",
                (player, viewer) -> wired.selector().open(viewer)));
    }

    private static void bindMute(
            ContextLinks links, com.uxplima.uxmessentials.messaging.application.port.MutePolicy policy) {
        MutableMutePolicy holder = links.mutePolicy;
        if (holder != null) {
            holder.bind(policy);
        }
    }

    private static void bindJail(
            ContextLinks links, com.uxplima.uxmessentials.teleport.application.port.JailGate gate) {
        MutableJailGate holder = links.jailGate;
        if (holder != null) {
            holder.bind(gate);
        }
    }

    private static void bindAfk(
            ContextLinks links, com.uxplima.uxmessentials.messaging.application.port.AfkStatus status) {
        MutableAfkStatus holder = links.afkStatus;
        if (holder != null) {
            holder.bind(status);
        }
    }

    private static void bindPlaytimeAfk(
            ContextLinks links, com.uxplima.uxmessentials.playerstate.application.port.AfkStatus status) {
        com.uxplima.uxmessentials.playerstate.adapter.outbound.MutablePlaytimeAfkStatus holder =
                links.playtimeAfkStatus;
        if (holder != null) {
            holder.bind(status);
        }
    }

    private static void wireVanish(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links, Bus bus) {
        // vanish is the single vanish authority: an in-memory ConcurrentHashMap of who is vanished, the /vanish
        // command, the Bukkit hide-show view (which drops both the player entity and their tab entry for ineligible
        // viewers), and the join/quit listener. It persists nothing. It wires before the contexts it informs
        // (messaging, presence, nametags, staff), so its store and toggle are captured here and threaded into their
        // vanish gates during their own wiring; a disabled vanish module leaves those handles null and each consumer
        // degrades to "no one is hidden". On stop the store is cleared so a disable/reload leaves no residual state.
        VanishWiring.Wired wired = VanishWiring.wire(plugin, ctx, bus);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.vanishStore = wired.vanishStore();
        links.vanishToggle = wired.toggleVanish();
        links.vanishLevelResolver = wired.levels();
        links.queries.register(
                UxmVanishQuery.class,
                new VanishQueries(
                        wired.vanishStore(), wired.levels(), ctx.kernel().playerLookup()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmVanishActions.class,
                source -> new com.uxplima.uxmessentials.vanish.adapter.outbound.api.VanishActions(
                        wired.toggleVanish(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // Captured for staff (wired last), which binds its VANISH gadget and vanish-on-enter to the one authority.
        links.staffVanishSeam =
                new com.uxplima.uxmessentials.staff.adapter.StaffWiring.VanishSeam(wired.toggleVanish());
    }

    private static void wirePresence(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus) {
        // presence persists nothing: the per-player PlayerPresence map is transient in-memory state. Vanish now lives
        // in its own context (wired earlier), so presence receives that authority's isVanished lookup, overlaid onto
        // its store so the %..._vanished% placeholder and the sleep exclusion reflect the one vanish state, and a
        // vanish-toggle handle for the settings panel; both degrade to no-ops when the vanish module is off. The
        // AFK soft-couple: presence rebinds messaging's MutableAfkStatus (captured during the earlier messaging
        // wiring) to a PresenceAfkStatus over this store so /msg adds the AFK courtesy notice.
        com.uxplima.uxmessentials.vanish.application.port.VanishStore vanishStore = links.vanishStore;
        java.util.function.Predicate<java.util.UUID> vanishLookup =
                vanishStore == null ? uuid -> false : vanishStore::isVanished;
        com.uxplima.uxmessentials.vanish.application.ToggleVanish vanishToggle = links.vanishToggle;
        java.util.function.Consumer<com.uxplima.uxmessentials.shared.domain.PlayerRef> vanishToggleHandle =
                vanishToggle == null ? who -> {} : vanishToggle::toggle;
        PresenceWiring.Wired wired =
                PresenceWiring.wire(plugin, ctx, vanishLookup, vanishToggleHandle, guiLayouts, guiRegistry, menus);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.presence(new StorePresencePlaceholders(wired.store(), wired.clock()));
        links.queries.register(UxmPresenceQuery.class, new PresenceQueries(wired.store()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmPresenceActions.class,
                source -> new com.uxplima.uxmessentials.presence.adapter.outbound.api.PresenceActions(
                        wired.services().markAfk(),
                        wired.store(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        bindAfk(links, new PresenceAfkStatus(wired.store()));
        // Rebind the playtime sampler's AFK seam (captured during the earlier playerstate wiring) to the live
        // presence store, so the sampler splits each player's seconds into active vs AFK. When playerstate is
        // disabled the holder is absent and this is a no-op.
        bindPlaytimeAfk(
                links, new com.uxplima.uxmessentials.playerstate.adapter.outbound.PresenceAfkStatus(wired.store()));
    }

    private static void wireCommunication(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // communication's only durable state is the DB-backed announcement set the /announce editor owns, built
        // over persistence.dsl(); the per-player broadcast opt-out is PDC-backed (survives relog), the sequence
        // counters are transient, and the connection policies, file announcer schedule, and info pages are
        // config-authored content. The announcer rotates over the file config PLUS the enabled store set. It carries
        // no cross-context bridge, so nothing is captured for a later context. The announcer timer on the Scheduler
        // port is stopped on disable.
        // The admin panel and the announcement editor consume the SP0 GUI framework: the data-folder layout loader
        // plus the shared anvil (installed once in wireModules) for the broadcast and editor prompts. /communication
        // gui and the /uxmess gui hub open the admin panel; bare /announce opens the editor.
        AnnouncementStore announcementStore = AnnouncementStores.jooq(persistence);
        com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore announcerSettingsStore =
                AnnouncementStores.settings(persistence);
        CommunicationWiring.Wired wired = CommunicationWiring.wire(
                plugin, ctx, announcementStore, announcerSettingsStore, guiLayouts, textInput, menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        // The communication PAPI seam reads the same global chat lock /togglechat flips and the per-player announcer
        // subscription /broadcasttoggle flips, so a placeholder matches the live chat state and the player's opt-in.
        links.placeholders.communication(new StoreCommunicationPlaceholders(wired.chatLock(), wired.optOutStore()));
        // communication is genuinely re-readable at runtime: the announcer rotates over a supplier that re-reads the
        // settings each tick, so re-reading the files and re-arming the override loops is enough for the new schedule
        // to take effect. /uxmess reload communication runs the same path /announce reload does.
        resources.addReloadTask(ReloadTask.forModule(
                ctx.moduleId(), wired.reload(), "announcer schedule and info pages re-read from disk"));
        resources.onClose(wired::stop);
        // Register the communication admin panel on the /uxmess gui hub, gated by the communication GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "communication",
                com.uxplima.uxmessentials.communication.application.CommunicationMessageKey.GUI_PANEL_TITLE,
                Material.WRITABLE_BOOK,
                "uxmessentials.communication.gui",
                (player, viewer) -> wired.adminMenu().open(viewer)));
    }

    private static void wireHolograms(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // holograms builds its cached jOOQ HologramRepository over persistence.dsl() and its renderer over the
        // uxmLib native-Display API; the holograms / hologram_lines tables ship in the persistence V13 baseline,
        // always applied. Its one cross-context bridge is the leaderboard data-source registry: the economy module
        // (when enabled, wired earlier) publishes a balance provider onto the links, which a leaderboard hologram
        // renders; with economy disabled the registry is empty and a leaderboard reads "(no data)". On wire every
        // stored hologram is spawned (each on its own region thread); on disable they are despawned cleanly.
        LeaderboardProviders leaderboards = new LeaderboardProviders(
                links.balanceLeaderboard == null ? Map.of() : Map.of("balance", links.balanceLeaderboard));
        // The same shared economy bridge npc charges its COST click actions through (captured during economy
        // wiring, which lands long before holograms); absent on a server without economy, so a hologram COST gate
        // is simply skipped there. It is a generic ClickActionEconomy, not an npc handle: holograms reaches no npc.
        // The management GUI consumes the SP0 framework: a GuiText over the shared catalog and the data-folder
        // layout loader (disk-first, then bundled). The list view (built inside HologramsWiring with the
        // repository + use cases) opens for /hologram with no args and from the /uxmess gui hub.
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(
                        ctx.kernel().messages());
        HologramsWiring.Wired wired = HologramsWiring.wire(
                plugin,
                ctx,
                persistence,
                bus,
                leaderboards,
                Optional.ofNullable(links.npcEconomy),
                guiText,
                guiLayouts,
                textInput,
                menus,
                menuBindings);
        wired.commands().forEach(resources::addCommand);
        resources.worldPhase().run("hologram spawn", wired::spawnStored);
        // The holograms PAPI seam reads the same cached repository /hologram list shows, so the count placeholder
        // matches the registered hologram total (a server-wide value resolved per request).
        links.placeholders.holograms(new RepositoryHologramsPlaceholders(wired.repository()));
        // Register the holograms management GUI on the /uxmess gui hub, gated by the holograms GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "holograms",
                com.uxplima.uxmessentials.holograms.application.HologramsMessageKey.HOLOGRAM_GUI_LIST_TITLE,
                org.bukkit.Material.ARMOR_STAND,
                "uxmessentials.holograms.gui",
                (player, viewer) -> wired.listMenu().open(viewer)));
        links.queries.register(
                com.uxplima.uxmessentials.api.query.UxmHologramsQuery.class,
                new com.uxplima.uxmessentials.holograms.adapter.outbound.api.HologramQueries(
                        wired.repository(), ctx.kernel().scheduler()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmHologramsActions.class,
                source -> new com.uxplima.uxmessentials.holograms.adapter.outbound.api.HologramActions(
                        wired.services().create(),
                        wired.services().delete(),
                        wired.services().move(),
                        wired.services().addLine(),
                        wired.services().setLine(),
                        wired.services().removeLine(),
                        wired.services().clickCommand(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().scheduler()));
        resources.onClose(wired::stop);
    }

    private static void wirePlayerwarps(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // player-warps delegates teleport execution to the captured teleport engine exactly as warps does; its
        // cached jOOQ repository over persistence.dsl() is keyed per owner, and the player_warps table ships in
        // the persistence V14 baseline, always applied. It carries no cross-context bridge beyond the engine.
        // The management GUI consumes the SP0 framework (a GuiText over the shared catalog, the data-folder layout
        // loader, an anvil): /pwarp with no args opens an owner-scoped list (a player sees their own warps, an
        // operator holding uxmessentials.pwarp.gui sees and manages everyone's) → per-warp editor, and it
        // registers the /uxmess gui hub entry. The existing /pwarp edit warps-editor reuse is untouched.
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine,
                "playerwarps delegates teleport execution but the teleport engine is unavailable");
        GuiText guiText = new GuiText(ctx.kernel().messages());
        PlayerwarpsWiring.Wired wired = PlayerwarpsWiring.wire(
                plugin,
                ctx,
                persistence,
                engine,
                bus,
                links.warpEditorView,
                links.warpPlayerWarpHandle,
                links.warpPlayerWarpGoTo,
                links.warpTeleportRegistry,
                guiText,
                guiLayouts,
                textInput,
                guiRegistry,
                menus,
                menuBindings,
                links.economyProvider,
                links.economyCurrencies,
                links.economyBackends);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.worldPhase().run("player-warp legacy migration", wired.legacyMigration());
        // The player-warps PAPI seam reads the same cached repository and count-limit quota the /pwarp commands
        // hold, so a placeholder matches what /setpwarp enforces and the /pwarps list shows.
        links.placeholders.playerwarps(new RepositoryPlayerwarpsPlaceholders(wired.repository(), wired.quota()));
        links.queries.register(
                UxmPlayerWarpsQuery.class,
                new PlayerWarpQueries(
                        wired.repository(),
                        wired.browse(),
                        wired.quota(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmPlayerWarpsActions.class,
                source -> new com.uxplima.uxmessentials.playerwarps.adapter.outbound.api.PlayerWarpActions(
                        wired.setPlayerWarp(),
                        wired.editPlayerWarp(),
                        wired.archivePlayerWarp(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().scheduler()));
        // Arm the rent sweep when the rent sub-group is on (a no-op otherwise), and halt it on disable/reload so no
        // orphaned off-tick task survives.
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
    }

    private static void wireScoreboard(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus) {
        // scoreboard persists nothing: the per-player "hidden" bit is PDC-backed (survives relog) and the sidebar /
        // tablist content is config-authored under modules/scoreboard/config.conf. The renderer owns only its packet
        // objective and never replaces Bukkit's per-player scoreboard, so nametag teams and other scoreboard data stay
        // intact. The render timer on the Scheduler port is stopped and every owned objective torn down on disable.
        // The settings panel consumes
        // the SP0 GUI framework (a GuiText over the shared catalog, the data-folder layout loader) and registers its
        // /uxmess gui hub entry; /scoreboard gui opens the same single-toggle panel, gated on the GUI node.
        ScoreboardWiring.Wired wired = ScoreboardWiring.wire(plugin, ctx, guiLayouts, menus);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The scoreboard PAPI seam reads the same PDC-backed "hidden" bit the /scoreboard toggle flips, so the
        // scoreboard_visible placeholder matches whether the player actually sees the sidebar in game.
        links.placeholders.scoreboard(new StoreScoreboardPlaceholders(wired.visibility(), wired.renderer()));
        resources.addReloadTask(ReloadTask.forModule(
                ctx.moduleId(), wired.reload(), "boards and animations re-read; online sidebars refreshed"));
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        // Register the scoreboard settings panel on the /uxmess gui hub, gated by the player-facing GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "scoreboard",
                com.uxplima.uxmessentials.scoreboard.application.ScoreboardMessageKey.GUI_TITLE,
                Material.PAINTING,
                "uxmessentials.scoreboard.gui",
                (player, viewer) -> wired.settingsView().open(player, viewer)));
        // The published sidebar surface: the same preference /scoreboard flips, and the same renderer, so a
        // consumer that brings a redraw forward or puts the board away is doing what the command does.
        links.queries.register(
                com.uxplima.uxmessentials.api.query.UxmScoreboardQuery.class,
                new com.uxplima.uxmessentials.scoreboard.adapter.outbound.api.ScoreboardQueries(
                        wired.visibility(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler(),
                        wired.renderer()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmScoreboardActions.class,
                source -> new com.uxplima.uxmessentials.scoreboard.adapter.outbound.api.ScoreboardActions(
                        wired.toggle(),
                        wired.visibility(),
                        wired.renderer(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
    }

    private static void wireTablist(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // tablist persists nothing: the header/footer content is config-authored under modules/tablist/config.conf. It
        // carries no cross-context bridge (its only collaborators are the shared Scheduler and log ports) so nothing
        // is captured for a later context, and the tablist is always-on (no per-player toggle) so it publishes no
        // command. The renderer dogfoods uxmlib-hud's Tablist; the render timer on the Scheduler port is stopped and
        // every active header/footer cleared on disable.
        TablistWiring.Wired wired = TablistWiring.wire(plugin, ctx);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The one published verb: the refresh timer's own pass, brought forward for a single viewer. Nothing outside
        // the module owns a row it could set, so there is nothing else honest to offer.
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmTablistActions.class,
                source -> new com.uxplima.uxmessentials.tablist.adapter.outbound.api.TablistActions(
                        wired.renderer(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // Which format a player's tab is drawn from, read off what the renderer last painted so a chat prefix or a
        // hologram line can agree with the tab the player is looking at.
        links.placeholders.tablist(new RendererTablistPlaceholders(wired.renderer()));
        resources.addReloadTask(ReloadTask.forModule(
                ctx.moduleId(), wired.reload(), "formats, layout rows and animations re-read; online tabs refreshed"));
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
    }

    private static void wireNametags(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // nametags persists nothing: the per-wearer formats are config-authored under modules/nametags/config.conf. It
        // soft-couples to presence (vanish-aware viewer culling through Bukkit's canSee graph, degrading to "everyone
        // can see everyone" with presence off). Its one cross-context handle is the shared PlayerTeamCoordinator:
        // the presenter hides a wearer's vanilla above-head name through it while the custom nametag is live. The
        // nametag is always-on (no per-player toggle) so it publishes no command. Rendering goes through uxmLib's
        // packet NametagRenderer: per-viewer spawn/metadata/remove bundles with no real entity, and a per-wearer
        // refresh loop the lib owns. On disable the reconcile timer on the Scheduler port is stopped and
        // presenter.removeAll() restores every online wearer's vanilla name, sends every wearer's remove packets, and
        // cancels each lib refresh task, so a disable/reload leaves no orphan.
        // The vanish gate reads the one vanish authority captured during vanish wiring (which lands before nametags),
        // or degrades to ALWAYS_VISIBLE ("everyone can see everyone") when the vanish module is disabled.
        com.uxplima.uxmessentials.vanish.application.port.VanishStore vanishStore = links.vanishStore;
        com.uxplima.uxmessentials.nametags.application.port.NametagVanish vanish = vanishStore == null
                ? com.uxplima.uxmessentials.nametags.application.port.NametagVanish.ALWAYS_VISIBLE
                : new com.uxplima.uxmessentials.nametags.adapter.outbound.AuthorityNametagVanish(
                        vanishStore, java.util.Objects.requireNonNull(links.vanishLevelResolver));
        NametagsWiring.Wired wired = NametagsWiring.wire(plugin, ctx, vanish, links.teams);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The one published verb: the reconcile pass for a single wearer, which re-selects the format before it
        // redraws. Removing a nametag from outside would last until the next pass, so it is not offered.
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmNametagActions.class,
                source -> new com.uxplima.uxmessentials.nametags.adapter.outbound.api.NametagActions(
                        wired.presenter(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        // The same read for the nametag: the format the wearer is actually shown from, not the one a re-selection
        // would pick.
        links.placeholders.nametags(new PresenterNametagsPlaceholders(wired.presenter()));
        resources.addReloadTask(ReloadTask.forModule(
                ctx.moduleId(), wired.reload(), "formats and animations re-read; online nametags refreshed"));
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
    }

    private static void wireNpc(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        // npc builds its cached jOOQ NpcRepository over persistence.dsl() and its renderer over the uxmLib NPC
        // packet stack; the npc table ships in the persistence V38 baseline, always applied. Its one cross-context
        // edge is soft: a COST click action charges through the economy bridge captured during economy wiring (npc
        // lands after economy), absent on a server without economy so the gate is simply skipped. A fake-player NPC
        // has no real entity: each viewer is sent a spawn (then its tab entry is hidden), range-culled and
        // re-evaluated on a per-second global refresh and on join/quit/world-change. On wire every stored NPC is
        // shown to the online viewers in range; on disable the refresh timer is cancelled and every shown fake
        // player is removed from every viewer so a reload re-spawns cleanly with no ghost. The management GUI
        // consumes the SP0 framework (a GuiText over the shared catalog, the data-folder layout loader, an anvil)
        // and registers its /uxmess gui hub entry; /npc with no args opens the same list, gated on the GUI node.
        GuiText guiText = new GuiText(ctx.kernel().messages());
        NpcWiring.Wired wired = NpcWiring.wire(
                plugin,
                ctx,
                persistence,
                bus,
                Optional.ofNullable(links.npcEconomy),
                guiText,
                guiLayouts,
                textInput,
                guiRegistry,
                menus,
                menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        links.queries.register(
                com.uxplima.uxmessentials.api.query.UxmNpcQuery.class,
                new com.uxplima.uxmessentials.npc.adapter.outbound.api.NpcQueries(
                        wired.repository(), ctx.kernel().scheduler()));
        // The published skin verb names an account rather than a base64 texture, and resolves it through the same
        // server-wide lookup /npc skin uses, so it works on an offline-mode server too.
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmNpcActions.class,
                source -> new com.uxplima.uxmessentials.npc.adapter.outbound.api.NpcActions(
                        wired.repository(),
                        wired.services().create(),
                        wired.services().delete(),
                        wired.services().moveTo(),
                        wired.services().skin(),
                        wired.services().displayName(),
                        wired.services().command(),
                        ctx.kernel().skins(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().worldLookup(),
                        ctx.kernel().scheduler()));
        // How many NPCs a player owns against the quota /npc create resolves, read off the same cached repository
        // so a HUD refresh is an in-memory walk rather than a query.
        links.placeholders.npc(wired.placeholders());
        resources.onClose(wired::stop);
    }

    private static void wireStaff(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        // staff persists the captured loadout through the jOOQ StaffLoadoutRepository over persistence.dsl() (the
        // staff_loadout table ships in the persistence V29 baseline, always applied), the item-loss-safe net, so
        // a crash mid-mode leaves the real loadout recoverable. It wires last, so it binds its three soft-couple
        // holders to the presence/playerstate/messaging seams captured during those contexts' wiring; a seam is
        // absent when its source module is disabled, leaving that gadget or staff chat on NONE (degrade, not fail).
        // On stop it exits every staff member still in staff mode, restoring their real loadout so a disable/reload
        // never strands anyone in the gadget hotbar.
        StaffWiring.StaffSeams seams = new StaffWiring.StaffSeams(
                Optional.ofNullable(links.staffVanishSeam),
                Optional.ofNullable(links.staffOpenContainer),
                Optional.ofNullable(links.staffAudience),
                Optional.ofNullable(links.staffModerationFreeze),
                Optional.ofNullable(links.staffTeleport));
        // The in-process bus is the concrete publisher so the enter/exit alert subscriber can be registered here
        // and unsubscribed on stop (the kernel port exposes only publish). With messaging off no alert is wired.
        InProcessDomainEventPublisher events =
                (InProcessDomainEventPublisher) ctx.kernel().events();
        StaffWiring.Wired wired =
                StaffWiring.wire(plugin, ctx, persistence, seams, events, guiRegistry, menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The staff PAPI seam reads the same staff-mode marker the /staffmode use cases hold and counts the online
        // staff-member holders the /stafflist roster shows, so a placeholder matches what the player sees in game.
        links.placeholders.staff(new StaffStaffPlaceholders(wired.services().store(), plugin.getServer()));
        // Read-only: entering staff mode swaps a real inventory for a loadout, which only the module can undo,
        // so who is on duty is published and turning it on is not.
        links.queries.register(
                com.uxplima.uxmessentials.api.query.UxmStaffQuery.class,
                new com.uxplima.uxmessentials.staff.adapter.outbound.api.StaffQueries(
                        wired.services().store()));
        resources.onClose(wired::stop);
    }

    private static void wireVote(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        // vote builds its counter-cached jOOQ VoteRepository over persistence.dsl() (the vote_party counter and
        // vote_queue offline reward batches ship in the persistence V15 baseline, always applied), the console
        // reward dispatcher on the global region thread, and the online audience for the party rewards and
        // thank-you broadcast. It syncs the server-wide party counter through the bus handle, a counter
        // mutation here announces a VoteCounterChanged, a remote one drops the cached counter, and a remote
        // VotePartyFired echoes the party announcement (never the reward), but carries no cross-context bridge.
        // The reflective Votifier listener self-registers behind a plugin-present guard on start and is dropped
        // on disable, so the module runs unchanged whether or not Votifier is installed. The repository and
        // threshold are surfaced for the PAPI vote placeholder seam registered after all contexts have wired.
        InProcessDomainEventPublisher events =
                (InProcessDomainEventPublisher) ctx.kernel().events();
        VoteWiring.Wired wired =
                VoteWiring.wire(plugin, ctx, persistence, events, bus, guiRegistry, menus, menuBindings);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        // Resolve leaderboard UUIDs to display names for votes_top_<period>_<n>_name, the same lookup
        // /vote top uses, so the placeholder shows a name, not the UUID the repository stores.
        com.uxplima.uxmessentials.shared.application.port.PlayerLookup lookup =
                ctx.kernel().playerLookup();
        java.util.function.Function<java.util.UUID, String> nameResolver = uuid -> lookup.findByUuid(uuid)
                .map(com.uxplima.uxmessentials.shared.domain.PlayerRef::name)
                .orElse(uuid.toString().toLowerCase(java.util.Locale.ROOT));
        links.placeholders.vote(
                new RepositoryVotePlaceholders(wired.repository(), wired.partyThreshold(), nameResolver));
        links.queries.register(
                UxmVoteQuery.class,
                new VoteQueries(wired.repository(), lookup, ctx.kernel().scheduler(), wired.partyThreshold()));
        links.actions.register(
                com.uxplima.uxmessentials.api.action.UxmVoteActions.class,
                source -> new com.uxplima.uxmessentials.vote.adapter.outbound.api.VoteActions(
                        wired.apiWrites(), lookup, ctx.kernel().scheduler(), source));
    }

    private static void wireDiscordlink(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus) {
        // discordlink builds its un-cached jOOQ store over persistence.dsl() (the discord_link_pending and
        // discord_links tables ship in the persistence V16 baseline, always applied) and the /discordlink and
        // /discordunlink commands. It registers its ConfirmLink seam into the ServicesManager so the optional
        // Discord bridge (a separate jar with no compile-time link to this one) can redeem a /link code through
        // the same use case; the registration is dropped on disable so a reload re-exposes it cleanly. The bridge
        // looks the service up once its gateway is ready and forwards nothing while it is absent. The link-status
        // panel consumes the SP0 GUI framework (a GuiText over the shared catalog, the data-folder layout loader)
        // and registers its /uxmess gui hub entry; /discordlink gui opens the same panel, gated on the GUI node.
        com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge bridge =
                new com.uxplima.uxmessentials.discordlink.adapter.outbound.ServicesManagerDiscordBridge(
                        plugin.getServer().getServicesManager());
        DiscordlinkWiring.Wired wired = DiscordlinkWiring.wire(ctx, persistence, guiLayouts, bridge, menus);
        wired.commands().forEach(resources::addCommand);
        // The discordlink PAPI seam reads the same DB-backed link store the /discordlink commands hold, so a
        // placeholder matches the binding the player redeemed (and answers for an offline player too).
        links.placeholders.discordlink(new StoreDiscordlinkPlaceholders(wired.store()));
        // Both directions of the binding are readable, and the one write a plugin has business doing is the
        // removal: a binding written without the Discord-side proof would say something untrue.
        links.queries.register(
                UxmDiscordLinkQuery.class,
                new DiscordLinkQueries(wired.store(), ctx.kernel().scheduler()));
        links.actions.register(
                UxmDiscordLinkActions.class,
                source -> new DiscordLinkActions(
                        wired.unlink(),
                        ctx.kernel().playerLookup(),
                        ctx.kernel().scheduler()));
        plugin.getServer()
                .getServicesManager()
                .register(
                        DiscordLinkConfirmation.class,
                        wired.confirmation(),
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        resources.onClose(() -> plugin.getServer().getServicesManager().unregister(wired.confirmation()));
        // Register the discordlink link-status panel on the /uxmess gui hub, gated by the player-facing GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "discordlink",
                com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey.GUI_TITLE,
                Material.PLAYER_HEAD,
                "uxmessentials.discord.gui",
                (player, viewer) -> wired.view().open(player, viewer)));
    }

    /** Cross-context handles captured during wiring so a dependent context reaches its prerequisite. */
    private static final class ContextLinks {
        // The shared target picker, built and registered at enable before any module wires; moderation and the
        // economy admin GUI open it rather than each building one of their own.
        private com.uxplima.uxmessentials.shared.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerPickerView
                playerPicker;
        private @org.jspecify.annotations.Nullable TeleportEngine teleportEngine;
        // The live economy provider and default currency, captured during economy wiring (which lands after
        // worlds). worlds resolves them lazily at fee-charge time, so a null here simply means "free worlds".
        private com.uxplima.uxmessentials.economy.application.port.@org.jspecify.annotations.Nullable EconomyProvider
                economyProvider;
        private com.uxplima.uxmessentials.economy.domain.@org.jspecify.annotations.Nullable Currency economyCurrency;
        // The currency + backend registries a warp fee resolves through: player-warps builds its jOOQ economy
        // bridge over these plus the live provider, so a null here (economy disabled) leaves a priced warp free.
        private com.uxplima.uxmessentials.economy.domain.@org.jspecify.annotations.Nullable CurrencyRegistry
                economyCurrencies;
        private com.uxplima.uxmessentials.economy.application.port.@org.jspecify.annotations.Nullable CurrencyBackendRegistry
                economyBackends;
        private @org.jspecify.annotations.Nullable WarpEconomy warpEconomy;
        private @org.jspecify.annotations.Nullable KitEconomy kitEconomy;
        private @org.jspecify.annotations.Nullable HomeEconomy homeEconomy;
        private com.uxplima.uxmessentials.vaults.application.port.@org.jspecify.annotations.Nullable VaultEconomy
                vaultEconomy;
        private @org.jspecify.annotations.Nullable ClickActionEconomy npcEconomy;
        private com.uxplima.uxmessentials.holograms.application.port.@org.jspecify.annotations.Nullable LeaderboardProvider
                balanceLeaderboard;
        private @org.jspecify.annotations.Nullable MutableMutePolicy mutePolicy;
        private @org.jspecify.annotations.Nullable MutableAfkStatus afkStatus;
        private com.uxplima.uxmessentials.playerstate.adapter.outbound.@org.jspecify.annotations.Nullable MutablePlaytimeAfkStatus
                playtimeAfkStatus;
        private @org.jspecify.annotations.Nullable MutableJailGate jailGate;
        private @org.jspecify.annotations.Nullable MutableHomeRespawnLocator homeRespawnLocator;
        private @org.jspecify.annotations.Nullable MutableWarpRespawnLocator warpRespawnLocator;
        private java.util.function.@org.jspecify.annotations.Nullable Function<
                        com.uxplima.uxmessentials.shared.domain.WorldRef,
                        Optional<com.uxplima.uxmessentials.shared.domain.Position>>
                spawnResolver;
        private com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                warpEditorView;
        private com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpRepositoryHandle
                warpPlayerWarpHandle;
        private com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpGoToHandle
                warpPlayerWarpGoTo;
        private com.uxplima.uxmessentials.warps.adapter.@org.jspecify.annotations.Nullable WarpTeleportRegistry
                warpTeleportRegistry;
        // The single vanish authority, captured during vanish wiring (which lands before the contexts it informs).
        // The messaging/nametags vanish gates read the store; the presence overlay + settings panel and staff-mode
        // vanish route through the toggle. Both are null when the vanish module is disabled, degrading each consumer.
        private com.uxplima.uxmessentials.vanish.application.port.@org.jspecify.annotations.Nullable VanishStore
                vanishStore;
        private com.uxplima.uxmessentials.vanish.application.@org.jspecify.annotations.Nullable ToggleVanish
                vanishToggle;
        // The see/use level resolver, captured with the store so the messaging/nametags gates read the same layered
        // see level the world does. Null when the vanish module is disabled (the gates degrade to fully-visible).
        private com.uxplima.uxmessentials.vanish.application.port.@org.jspecify.annotations.Nullable VanishLevelResolver
                vanishLevelResolver;
        // The live trade registry, captured during trade wiring so the relational trade placeholders can read it
        // after every module has wired. Null when the trade module is disabled, which reads as "nobody is trading".
        private @org.jspecify.annotations.Nullable TradeSessions tradeSessions;
        // The soft-couple seams staff binds when it wires (it lands last). Each is captured during the source
        // context's wiring and left null when that context is disabled, so staff degrades the matching gadget or
        // staff chat to a no-op rather than failing.
        private com.uxplima.uxmessentials.staff.adapter.StaffWiring.@org.jspecify.annotations.Nullable VanishSeam
                staffVanishSeam;
        private com.uxplima.uxmessentials.playerstate.application.@org.jspecify.annotations.Nullable OpenContainer
                staffOpenContainer;
        private com.uxplima.uxmessentials.messaging.application.port.@org.jspecify.annotations.Nullable StaffAudience
                staffAudience;
        private com.uxplima.uxmessentials.staff.adapter.StaffWiring.@org.jspecify.annotations.Nullable ModerationFreezeSeam
                staffModerationFreeze;
        private com.uxplima.uxmessentials.staff.adapter.StaffWiring.@org.jspecify.annotations.Nullable TeleportSeam
                staffTeleport;
        private com.uxplima.uxmessentials.moderation.application.@org.jspecify.annotations.Nullable TempBan
                securityLockoutBan;
        // The PlaceholderAPI read seams, filled by each enabled context that contributes placeholders.
        private final PlaceholderContexts.Builder placeholders = PlaceholderContexts.builder();
        // The published read surfaces, filled the same way and for the same reason: a context that never wires
        // registers nothing, so the developer API answers "that module is off" rather than "no data". Handed in
        // rather than created here, because the API front door is published to other plugins before any context
        // wires and has to hold the same instance the contexts fill.
        private final com.uxplima.uxmessentials.shared.adapter.outbound.api.QueryContexts queries;
        // The published write surfaces, held for the same reason and filled the same way.
        private final com.uxplima.uxmessentials.shared.adapter.outbound.api.ActionContexts actions;

        private ContextLinks(
                com.uxplima.uxmessentials.shared.adapter.outbound.api.QueryContexts queries,
                com.uxplima.uxmessentials.shared.adapter.outbound.api.ActionContexts actions) {
            this.queries = queries;
            this.actions = actions;
        }
        // Built once for packet-backed nametag presenters. Inert until a nametag hide call marks a player.
        private final PlayerTeamCoordinator teams = new PlayerTeamCoordinator();
    }

    /**
     * The charge receipt the economy seams wired here report through. Each of them debits as a side effect of
     * something else the player asked for, so without it the money leaves with no word for it and only a balance
     * nobody was watching records that it happened.
     */
    private static Optional<com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts> receipts(
            ModuleContext ctx) {
        return Optional.of(new com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts(
                ctx.kernel().messages(), ctx.kernel().messageSink()));
    }

    private static boolean skippedByCapability(FeatureModule module, ModuleContext ctx, Logger log) {
        LoadCondition condition = module.loadCondition();
        Optional<String> unmet = condition.unmetReason(ctx);
        if (unmet.isPresent()) {
            log.warning("module " + module.id() + " skipped, " + unmet.get());
            return true;
        }
        return false;
    }

    /** The per-module wiring body, isolated behind an interface so the load policy is testable on its own. */
    @FunctionalInterface
    interface ModuleLoader {
        void load(FeatureModule module);
    }

    /**
     * Loads each module inside its own fault boundary. A module that throws while starting or wiring is rolled
     * back to the checkpoint taken before it ran and skipped, leaving every sibling module, and the plugin,
     * up; the failure is logged with its cause. A capability-skipped module registers nothing, so its clean
     * skip inside the loader needs no rollback.
     */
    static void loadModulesIsolated(
            Iterable<FeatureModule> modules, CloseableResources resources, Logger log, ModuleLoader loader) {
        for (FeatureModule module : modules) {
            CloseableResources.Scope scope = resources.openScope();
            try {
                loader.load(module);
            } catch (RuntimeException failure) {
                resources.rollbackTo(scope);
                log.log(
                        Level.SEVERE,
                        "module " + module.id() + " failed to load. Skipped; other modules unaffected",
                        failure);
            }
        }
    }

    private static void startModule(FeatureModule module, ModuleContext ctx, CloseableResources resources, Logger log) {
        // Migrations for every enabled, loadable module were applied up front when the persistence layer
        // opened (see KernelWiring.openPersistence). A disabled module contributes no location, so its
        // tables stay absent. By the time start() runs the schema is already in place.
        long startedAt = System.nanoTime();
        module.start(ctx);
        long loadMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        resources.onClose(module::stop);
        log.info("module " + module.id() + " loaded in " + loadMillis + " ms");
    }
}
