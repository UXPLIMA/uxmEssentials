package com.uxplima.uxmessentials.moderation.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.inbound.command.ModerationCommands;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.ModerationGuiCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.JailGuiViews;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationGuiViews;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationHistoryMenu;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationJailedMenu;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentConfirmView;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentGuiFlow;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.CommandSpyListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.FreezeMoveListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.ModerationJoinListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.ModerationLoginListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.MutedCommandListener;
import com.uxplima.uxmessentials.moderation.adapter.outbound.BukkitSanctions;
import com.uxplima.uxmessentials.moderation.adapter.outbound.CombinedJailDirectory;
import com.uxplima.uxmessentials.moderation.adapter.outbound.ConfigJailDirectory;
import com.uxplima.uxmessentials.moderation.adapter.outbound.DiscordPunishmentAudit;
import com.uxplima.uxmessentials.moderation.adapter.outbound.InMemoryCommandSpyStore;
import com.uxplima.uxmessentials.moderation.adapter.outbound.LoggingModerationAudit;
import com.uxplima.uxmessentials.moderation.adapter.outbound.PermissionSanctionBroadcast;
import com.uxplima.uxmessentials.moderation.adapter.outbound.PlayerLookupTargetResolver;
import com.uxplima.uxmessentials.moderation.adapter.outbound.api.ModerationApiWrites;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.CheckBan;
import com.uxplima.uxmessentials.moderation.application.CheckMute;
import com.uxplima.uxmessentials.moderation.application.ClearWarns;
import com.uxplima.uxmessentials.moderation.application.CommandSpy;
import com.uxplima.uxmessentials.moderation.application.DelJail;
import com.uxplima.uxmessentials.moderation.application.EnforceRemoteBan;
import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.IssueWarn;
import com.uxplima.uxmessentials.moderation.application.Jail;
import com.uxplima.uxmessentials.moderation.application.JailCountdown;
import com.uxplima.uxmessentials.moderation.application.Kick;
import com.uxplima.uxmessentials.moderation.application.KickAll;
import com.uxplima.uxmessentials.moderation.application.ListAlts;
import com.uxplima.uxmessentials.moderation.application.ListBans;
import com.uxplima.uxmessentials.moderation.application.ListJailed;
import com.uxplima.uxmessentials.moderation.application.ListJails;
import com.uxplima.uxmessentials.moderation.application.ListMutes;
import com.uxplima.uxmessentials.moderation.application.Lockdown;
import com.uxplima.uxmessentials.moderation.application.LoginEnforcement;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.Mute;
import com.uxplima.uxmessentials.moderation.application.MutedCommandPolicy;
import com.uxplima.uxmessentials.moderation.application.Punish;
import com.uxplima.uxmessentials.moderation.application.PunishmentStats;
import com.uxplima.uxmessentials.moderation.application.RepositoryJailGate;
import com.uxplima.uxmessentials.moderation.application.RepositoryMutePolicy;
import com.uxplima.uxmessentials.moderation.application.ResolveTemplate;
import com.uxplima.uxmessentials.moderation.application.ReviewBanHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewMuteHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewPunishmentStats;
import com.uxplima.uxmessentials.moderation.application.ReviewSanctionHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewStaffHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewWarns;
import com.uxplima.uxmessentials.moderation.application.SanctionDurationLimit;
import com.uxplima.uxmessentials.moderation.application.SanctionHistoryRecorder;
import com.uxplima.uxmessentials.moderation.application.SanctionSummary;
import com.uxplima.uxmessentials.moderation.application.Seen;
import com.uxplima.uxmessentials.moderation.application.SetJail;
import com.uxplima.uxmessentials.moderation.application.StaffRollback;
import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.application.TempBanIp;
import com.uxplima.uxmessentials.moderation.application.TempWarn;
import com.uxplima.uxmessentials.moderation.application.ToggleJail;
import com.uxplima.uxmessentials.moderation.application.Unban;
import com.uxplima.uxmessentials.moderation.application.UnbanIp;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.Unmute;
import com.uxplima.uxmessentials.moderation.application.WarnEscalator;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.persistence.moderation.ModerationStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.ModerationSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/**
 * Constructs the moderation context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces everything the plugin must register: the Brigadier command list and the
 * login/join/freeze listeners. This is the one place the moderation context is wired. Nothing else news up
 * its classes.
 *
 * <p>The audit trail goes to the dedicated {@code com.uxplima.uxmessentials.audit} SLF4J channel (not the
 * plugin log), so an operator routes it to a retained file per docs/09-deployment. The two cross-context
 * gates moderation <em>provides</em> are bound here onto the rebindable holders the messaging
 * ({@code MutableMutePolicy}) and teleport ({@code MutableJailGate}) contexts already hold: a muted player
 * stops being able to {@code /msg} and a jailed player stops being able to {@code /home}/{@code /tpa} the
 * moment this module wires. When moderation is disabled this wiring never runs, so both holders stay on their
 * {@code NEVER} default and the other contexts degrade gracefully.
 */
@NullMarked
public final class ModerationWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    /** The locale dimension the shared chat prefix is resolved against: the console has no per-viewer locale. */
    private static final com.uxplima.uxmessentials.shared.domain.PlayerRef BROADCAST_PREFIX_VIEWER =
            com.uxplima.uxmessentials.shared.domain.PlayerRef.system("console");

    private ModerationWiring() {}

    /**
     * Build the moderation adapters and use cases from {@code ctx}, the {@code persistence} DSL, the gate sinks
     * and the cross-server {@code bus}.
     *
     * <p>Cross-server live enforcement rides the {@link Bus} handle: a successful {@code /ban}/{@code /tempban}
     * publishes a {@code BanChanged} (and {@code /mute} a {@code MuteChanged}) through the
     * {@link SanctionSync} bound to the bus publisher, and the wiring registers a {@link ModerationSync}
     * listener that kicks a player a peer just banned if they are online here. The durable enforcement is free
     * already (shared DB re-read on every login); this only closes the live gap. With the bus disabled the
     * publisher is {@link SanctionSync#NONE} and the listener is never invoked, so the single-server path is
     * unchanged.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            GateSinks gates,
            Bus bus,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            PlayerPickerView picker,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder,
            IpHistoryStore ipHistory,
            IpTokens ipTokens) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(gates, "gates");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(picker, "picker");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(ipHistory, "ipHistory");
        Objects.requireNonNull(ipTokens, "ipTokens");
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        ModerationSettings settings = new ModerationSettings(ctx.config(), kernel.log());
        ModerationRepository repository = ModerationStores.repository(persistence);
        JailLocationStore jailLocations = ModerationStores.jailLocationStore(persistence);
        SanctionHistory sanctionHistory = ModerationStores.sanctionHistory(persistence);
        SanctionHistoryRecorder historyRecorder = new SanctionHistoryRecorder(sanctionHistory, clock);
        BukkitSanctions sanctions =
                new BukkitSanctions(plugin.getServer(), kernel.scheduler(), settings, jailLocations);
        ModerationGuard guard = new ModerationGuard(kernel.permissions());
        InMemoryCommandSpyStore commandSpyStore = new InMemoryCommandSpyStore();
        SanctionSync sync = ModerationSync.publisher(bus.publisher());
        ModerationServices services = assemble(
                plugin,
                kernel,
                settings,
                repository,
                jailLocations,
                sanctionHistory,
                historyRecorder,
                sanctions,
                guard,
                commandSpyStore,
                sync,
                ipHistory,
                ipTokens,
                clock);
        // The live kick: on a remote ban frame, re-evaluate the now-authoritative ban here and kick an online
        // target. Self-origin frames are dropped by the bus client before dispatch, so the local /ban (which
        // already kicked) never double-kicks; with the bus disabled this listener is never invoked.
        EnforceRemoteBan enforce = new EnforceRemoteBan(
                repository, sanctions, new Notifier(kernel.messages(), kernel.messageSink()), clock);
        bus.registry().register(ModerationSync.listener(enforce::onRemoteBan));
        RepositoryMutePolicy mutePolicy = new RepositoryMutePolicy(repository, clock);
        RepositoryJailGate jailGate = new RepositoryJailGate(repository, clock);
        gates.bindMute(mutePolicy);
        gates.bindJail(jailGate);
        // The management GUI's three views (active-punishments list → per-punishment detail/revoke → player history)
        // read FRESH from the same repository / history port the list commands use and revoke through the same
        // audited unban/unmute/unjail use cases the /un* commands take. The /mod command and the /uxmess gui hub
        // entry both open the active-punishments list.
        // The active-punishments list, the history list and the jailed-players release list all render through the
        // shared menu engine: each registers its spec and bindings once here, then opens through the Menus façade.
        // Every list's read (with its name/kind/remaining resolution) runs off the tick thread at the open site, so
        // the list sources only read a pre-resolved subject. The active list's left click opens the still-bespoke
        // PunishmentDetailView; that view's back reopens the engine list, a cycle broken inside ModerationGuiViews.
        ModerationHistoryMenu historyMenu = new ModerationHistoryMenu(menus, kernel.scheduler(), sanctionHistory);
        historyMenu.register(menuBindings, dataFolder, kernel.log());
        ModerationJailedMenu jailedMenu = new ModerationJailedMenu(
                menus, kernel.scheduler(), services.unjail(), repository, kernel.playerLookup(), clock);
        jailedMenu.register(menuBindings, dataFolder, kernel.log());
        ModerationGuiViews guiViews = ModerationGuiViews.create(
                guiText,
                kernel.scheduler(),
                services,
                repository,
                kernel.playerLookup(),
                kernel.messages(),
                historyMenu,
                clock,
                guiLayouts,
                menus,
                menuBindings,
                dataFolder,
                kernel.log());
        // The bare-command GUI flow for the named sanctions (/ban /mute /tempban /tempmute /warn /banip): the
        // reusable player picker (and, for the timed verbs, the reusable duration picker) into the per-target
        // confirm screen, ending in the same audited use cases the raw subcommands take. The views stay generic:
        // the flow supplies the moderation TargetResolver as its offline-name resolver, the unknown-target reply,
        // and the SanctionDuration-backed validator for the timed verbs.
        DurationPickerView durationPicker = new DurationPickerView(
                menus, guiText, kernel.scheduler(), textInput, kernel.messages(), kernel.messageSink());
        PunishmentConfirmView confirmView = new PunishmentConfirmView(menus, kernel.scheduler(), textInput);
        confirmView.register(menuBindings, dataFolder, kernel.log());
        PunishmentGuiFlow guiFlow = new PunishmentGuiFlow(
                services, picker, durationPicker, confirmView, kernel.messages(), kernel.messageSink());
        // The bare-/jail management GUI: the jail-a-player flow (picker → jail chooser → duration, permanent
        // allowed) plus the [Jails] manager and [Jailed players] release list reached from the hub's footer.
        // It reuses the same shared player/duration pickers and reads FRESH from the same jail directory and
        // repository the /jails and /jailedplayers commands use, executing through the same audited
        // jail/unjail/setjail/del use cases the raw commands take.
        com.uxplima.uxmessentials.moderation.application.port.JailLocator jailLocator =
                new com.uxplima.uxmessentials.moderation.adapter.outbound.BukkitJailLocator(settings, jailLocations);
        JailGuiViews jailGui = JailGuiViews.create(
                menus,
                menuBindings,
                guiText,
                kernel.scheduler(),
                services,
                sanctions,
                jailLocator,
                jailedMenu,
                picker,
                durationPicker,
                textInput,
                kernel.messages(),
                kernel.messageSink(),
                guiLayouts,
                dataFolder,
                kernel.log());
        java.util.List<CommandRegistration> commands = new java.util.ArrayList<>(ModerationCommands.all(
                services,
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                settings.silentByDefault(),
                guiFlow,
                guiViews,
                picker,
                guiText,
                textInput,
                jailGui));
        commands.add(new ModerationGuiCommand(services, kernel.messages(), kernel.messageSink(), guiViews));
        return new Wired(
                commands,
                listeners(services, sanctions, repository, kernel, settings, guard, commandSpyStore, clock),
                sanctions,
                commandSpyStore,
                mutePolicy,
                jailGate,
                services.freeze(),
                services.tempBan(),
                repository,
                sanctionHistory,
                clock,
                guiViews,
                new ModerationApiWrites(
                        services.ban(),
                        services.tempBan(),
                        services.unban(),
                        services.mute(),
                        services.unmute(),
                        services.kick(),
                        services.warn(),
                        services.jail(),
                        services.unjail()));
    }

    private static ModerationServices assemble(
            Plugin plugin,
            KernelPorts kernel,
            ModerationSettings settings,
            ModerationRepository repository,
            JailLocationStore jailLocations,
            SanctionHistory sanctionHistory,
            SanctionHistoryRecorder history,
            BukkitSanctions sanctions,
            ModerationGuard guard,
            InMemoryCommandSpyStore commandSpyStore,
            SanctionSync sync,
            IpHistoryStore ipHistory,
            IpTokens ipTokens,
            Clock clock) {
        // One alt lookup for every flow that asks "who else uses this address": the login audit, /banip,
        // /tempbanip and /seenip. It tokenises first, so all four match the same rows /alts groups over.
        IpAlts ipAlts = new IpAlts(ipHistory, ipTokens);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        // The operator audit is wrapped so a successful punishment also emits a name-based Discord notice on the
        // same channel when discord-notify is on; disabled or bridge-absent, it is a no-op.
        ModerationAudit audit = new DiscordPunishmentAudit(
                new LoggingModerationAudit(auditLogger()), auditLogger(), settings.discordNotify());
        CombinedJailDirectory jails = new CombinedJailDirectory(new ConfigJailDirectory(settings), jailLocations);
        Sanctions sanctionPort = sanctions;
        Jail jail = new Jail(repository, jails, sanctionPort, guard, notifier, audit, kernel.events(), clock);
        Unjail unjail = new Unjail(repository, sanctionPort, notifier, audit, kernel.events(), clock);
        SanctionBroadcast broadcast = new PermissionSanctionBroadcast(
                plugin.getServer(),
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                broadcastPrefix(kernel));
        SanctionDurationLimit limit = new SanctionDurationLimit(kernel.permissions());
        // The escalated sanctions a warning auto-applies run through their own use cases, never back through a
        // warn, so escalation cannot recurse; they are built first so the escalator can drive them.
        Mute mute =
                new Mute(repository, guard, notifier, audit, kernel.events(), history, limit, broadcast, sync, clock);
        TempBan tempBan = new TempBan(
                repository,
                sanctionPort,
                guard,
                notifier,
                audit,
                kernel.events(),
                history,
                limit,
                broadcast,
                sync,
                clock);
        Ban ban = new Ban(
                repository,
                sanctionPort,
                guard,
                notifier,
                audit,
                kernel.events(),
                history,
                limit,
                broadcast,
                sync,
                settings.addressStrictness(),
                ipHistory,
                clock);
        Kick kick = new Kick(sanctionPort, guard, notifier, audit, history, broadcast);
        // /punish resolves a configured template to a preset reason + duration and dispatches to the same
        // audited ban/tempban use cases above, so no punish logic is duplicated.
        Punish punish = new Punish(new ResolveTemplate(settings.templates()), ban, tempBan, notifier);
        WarnEscalator escalator = new WarnEscalator(settings.warnEscalation(), mute, tempBan, ban, kick, notifier);
        // The revoke use cases are built as named locals so /staffrollback drives the same instances the
        // standalone /unmute, /unban and /unwarn commands do: one audited path, no parallel rollback logic.
        Unmute unmute = new Unmute(repository, notifier, audit, kernel.events(), history, clock);
        Unban unban = new Unban(repository, notifier, audit, history);
        ClearWarns clearWarns = new ClearWarns(repository, notifier, audit);
        StaffRollback staffRollback =
                new StaffRollback(sanctionHistory, repository, unban, unmute, notifier, audit, clock);
        return new ModerationServices.Builder()
                .mute(mute)
                .unmute(unmute)
                .jail(jail)
                .unjail(unjail)
                .toggleJail(new ToggleJail(repository, jails, jail, unjail))
                .tempBan(tempBan)
                .ban(ban)
                .punish(punish)
                .unban(unban)
                .kick(kick)
                .kickAll(new KickAll(sanctionPort, guard, notifier, audit))
                .warn(new IssueWarn(
                        repository, guard, notifier, audit, kernel.events(), history, broadcast, escalator, clock))
                .tempWarn(new TempWarn(
                        repository, guard, notifier, audit, kernel.events(), history, broadcast, escalator, clock))
                .reviewWarns(new ReviewWarns(repository, notifier, clock))
                .clearWarns(clearWarns)
                .sanctionSummary(new SanctionSummary(repository, notifier, clock))
                .listJails(new ListJails(jails, notifier))
                .listJailed(new ListJailed(repository, kernel.playerLookup(), notifier, clock))
                .setJail(new SetJail(jailLocations, notifier, audit, kernel.events(), clock))
                .delJail(new DelJail(jailLocations, notifier, audit, kernel.events(), clock))
                .listBans(new ListBans(repository, kernel.playerLookup(), notifier, clock))
                .listMutes(new ListMutes(repository, kernel.playerLookup(), notifier, clock))
                .reviewBanHistory(new ReviewBanHistory(sanctionHistory, notifier))
                .reviewMuteHistory(new ReviewMuteHistory(sanctionHistory, notifier))
                .reviewSanctionHistory(new ReviewSanctionHistory(sanctionHistory, notifier))
                .reviewStaffHistory(new ReviewStaffHistory(sanctionHistory, kernel.playerLookup(), notifier))
                .reviewPunishmentStats(
                        new ReviewPunishmentStats(sanctionHistory, new PunishmentStats(), notifier, clock))
                .checkBan(new CheckBan(repository, notifier, clock))
                .checkMute(new CheckMute(repository, notifier, clock))
                .banIp(new BanIp(repository, ipAlts, notifier, audit, kernel.events(), history, clock))
                .tempBanIp(new TempBanIp(repository, ipAlts, notifier, audit, kernel.events(), history, clock))
                .unbanIp(new UnbanIp(repository, notifier, audit, history))
                .freeze(new Freeze(sanctionPort, guard, notifier, audit))
                .seen(new Seen(
                        repository, ipAlts, kernel.playerLookup(), notifier, settings.censorIpAddresses(), clock))
                .listAlts(new ListAlts(ipHistory, kernel.playerLookup(), notifier))
                .commandSpy(new CommandSpy(commandSpyStore, notifier))
                .staffRollback(staffRollback)
                .jailCountdown(new JailCountdown(repository, sanctionPort, audit, kernel.events(), clock))
                .loginEnforcement(new LoginEnforcement(repository, ipAlts, notifier, audit, clock))
                .lockdown(new Lockdown(repository, notifier, broadcast, audit))
                .repository(repository)
                .players(kernel.playerLookup())
                .targets(new PlayerLookupTargetResolver(kernel.playerLookup()))
                .build();
    }

    private static List<Listener> listeners(
            ModerationServices services,
            BukkitSanctions sanctions,
            ModerationRepository repository,
            KernelPorts kernel,
            ModerationSettings settings,
            ModerationGuard guard,
            InMemoryCommandSpyStore commandSpyStore,
            Clock clock) {
        MutedCommandPolicy mutedCommands = new MutedCommandPolicy(settings.mutedBlockedCommands());
        return List.of(
                new ModerationLoginListener(services.loginEnforcement()),
                new ModerationJoinListener(services.jailCountdown(), repository, clock),
                new FreezeMoveListener(sanctions),
                new MutedCommandListener(
                        repository, mutedCommands, guard, kernel.messages(), kernel.messageSink(), clock),
                new CommandSpyListener(commandSpyStore, kernel.messages(), kernel.messageSink()));
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * The shared chat {@code <prefix>} template, resolved through the kernel {@link
     * com.uxplima.uxmessentials.shared.application.port.Messages} catalog so the console-facing broadcast line
     * frames identically to the player-facing one (which the {@code MessageSink} prefixes on the player path).
     * {@code Messages.resolve} substitutes only {@code {name}} placeholders, leaving MiniMessage tags intact, so
     * this returns the raw prefix source string.
     */
    private static String broadcastPrefix(KernelPorts kernel) {
        return kernel.messages().resolve(BROADCAST_PREFIX_VIEWER, () -> "prefix", java.util.Map.of());
    }

    /**
     * The cross-context gate sinks moderation rebinds when it wires: the messaging mute holder and the
     * teleport jail holder. Passing them as a narrow callback pair keeps {@code ModerationWiring} from
     * importing the other contexts' adapter types directly: bootstrap supplies the binders.
     *
     * @param bindMute rebinds the messaging mute gate to the supplied policy
     * @param bindJail rebinds the teleport jail gate to the supplied gate
     */
    public record GateSinks(
            java.util.function.Consumer<com.uxplima.uxmessentials.messaging.application.port.MutePolicy> bindMute,
            java.util.function.Consumer<com.uxplima.uxmessentials.teleport.application.port.JailGate> bindJail) {

        public GateSinks {
            Objects.requireNonNull(bindMute, "bindMute");
            Objects.requireNonNull(bindJail, "bindJail");
        }

        void bindMute(com.uxplima.uxmessentials.messaging.application.port.MutePolicy policy) {
            bindMute.accept(policy);
        }

        void bindJail(com.uxplima.uxmessentials.teleport.application.port.JailGate gate) {
            bindJail.accept(gate);
        }
    }

    /**
     * Everything the moderation module contributes once wired: the Brigadier commands, the
     * login/join/freeze listeners, and the {@link BukkitSanctions} adapter (held so stop drains its freeze
     * set).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the login/join/freeze/commandspy listeners to register
     * @param sanctions the live-player sanction adapter, for the stop-time freeze drain
     * @param commandSpyStore the session-scoped commandspy set, for the stop-time drain
     * @param mutePolicy the mute read side the {@code muted} placeholder queries
     * @param jailGate the jail read side the {@code jailed} placeholder queries
     * @param freeze the audited freeze use case the staff FREEZE gadget orchestrates (with {@link #sanctions} as
     *     the live freeze-state read)
     * @param repository the sanction-state read side the {@code moderation_ban_*}/{@code moderation_mute_*}/
     *     {@code moderation_warns} placeholders query (clock-gated through {@link #clock})
     * @param tempBan the tempban use case, lent to security so a verification lockout is an ordinary ban
     * @param clock the clock the placeholder seam gates active ban/mute reads against
     * @param guiViews the management GUI views, whose active-punishments list the {@code /uxmess gui} hub opens
     * @param apiWrites the nine punishment use cases the published API runs, the very ones behind the commands
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            BukkitSanctions sanctions,
            InMemoryCommandSpyStore commandSpyStore,
            RepositoryMutePolicy mutePolicy,
            RepositoryJailGate jailGate,
            Freeze freeze,
            TempBan tempBan,
            ModerationRepository repository,
            SanctionHistory sanctionHistory,
            Clock clock,
            ModerationGuiViews guiViews,
            ModerationApiWrites apiWrites) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(sanctions, "sanctions");
            Objects.requireNonNull(commandSpyStore, "commandSpyStore");
            Objects.requireNonNull(mutePolicy, "mutePolicy");
            Objects.requireNonNull(jailGate, "jailGate");
            Objects.requireNonNull(freeze, "freeze");
            Objects.requireNonNull(tempBan, "tempBan");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(sanctionHistory, "sanctionHistory");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(guiViews, "guiViews");
            Objects.requireNonNull(apiWrites, "apiWrites");
        }

        /** Drop the session-scoped freeze and commandspy sets. Called on module stop. */
        public void stop() {
            sanctions.clear();
            commandSpyStore.clear();
        }
    }
}
