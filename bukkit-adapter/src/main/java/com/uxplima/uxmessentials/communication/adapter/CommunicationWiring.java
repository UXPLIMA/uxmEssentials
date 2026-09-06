package com.uxplima.uxmessentials.communication.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.communication.adapter.inbound.command.CommunicationCommands;
import com.uxplima.uxmessentials.communication.adapter.inbound.command.CommunicationGuiCommand;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.AnnouncementEditorView;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.CommunicationAdminMenu;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.AdvancementMessageListener;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ChatFormatListener;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ChatLockListener;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ConnectionMessageListener;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.DeathMessageListener;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.AtomicSequenceCounter;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSources;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatPlaceholderExpander;
import com.uxplima.uxmessentials.communication.adapter.outbound.PdcBroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.application.MergeAnnouncements;
import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveDeathMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the communication context's adapters and use cases over the injected kernel ports and the operator
 * content under {@code modules/communication/}, and produces everything the plugin must register: the Brigadier command
 * list (the static {@code /broadcast}, {@code /broadcasttoggle}, and {@code /announce} plus the config-derived
 * info-page commands), the join/quit/death connection listeners, the advancement-notification listener, and the
 * self-rescheduling announcer timer on the {@code Scheduler} port. The announcer and the advancement listener fan
 * out through the shared {@link ChannelBroadcaster} so their multi-channel delivery, PlaceholderAPI expansion, and
 * per-viewer opt-out gating match vote's broadcaster. The advancement listener is registered unconditionally and
 * gated by its live config, so {@code /uxmess reload communication} can enable it without re-registration. This is
 * the one place the communication context is wired: nothing else news up its classes.
 *
 * <p>The context persists nothing: the per-player opt-out bit is PDC-backed (survives relog), the sequence
 * counters are transient, and the announcer schedule and info pages are config-authored. The operator content is
 * read once into {@link CommunicationSettings} and rendered through MiniMessage; the plugin's own
 * {@code /broadcasttoggle} confirmation and missing-page error are {@code MessageKey}s through the
 * {@link Notifier}, keeping the parity-checked keys and the unchecked operator content apart.
 */
@NullMarked
public final class CommunicationWiring {

    private static final String MODULE_DIR = "modules/communication";

    private CommunicationWiring() {}

    /** Build the communication adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            AnnouncementStore announcementStore,
            AnnouncerSettingsStore announcerSettingsStore,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(announcementStore, "announcementStore");
        Objects.requireNonNull(announcerSettingsStore, "announcerSettingsStore");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        CommunicationSettings settings = new CommunicationSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        BroadcastOptOutStore optOutStore = new PdcBroadcastOptOutStore(plugin);
        RandomSource random = new ThreadLocalRandomSource();
        BukkitInfoSender infoSender = new BukkitInfoSender(kernel.messageSink());
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        InfoRegistry registry = settings.infoRegistry();
        ChatLock chatLock = new ChatLock();

        // The announcer rotates over the file config PLUS the enabled editor-managed store announcements, with the
        // global interval/min-players override from the settings screen folded in. This merged supplier is the single
        // source both the NextAnnouncement rotation and the AnnouncerTask override loops read, so an editor
        // create/edit/enable or a settings change takes effect on the next tick with no reload (the supplier re-reads
        // the store and the settings each call).
        MergeAnnouncements merge = new MergeAnnouncements(announcementStore, announcerSettingsStore);
        Supplier<AnnouncerConfig> mergedConfig = () -> merge.merge(settings.announcerConfig());

        CommunicationServices services = assemble(kernel, settings, mergedConfig, optOutStore, random, notifier);
        ChannelBroadcaster channelBroadcaster = new ChannelBroadcaster(kernel.scheduler(), settings.announcerDisplay());
        BukkitAnnouncerBroadcaster broadcaster = new BukkitAnnouncerBroadcaster(
                kernel.messageSink(),
                optOutStore,
                channelBroadcaster,
                CommunicationWiring::conditionContext,
                kernel.scheduler());
        AnnouncerTask announcer = new AnnouncerTask(
                kernel.scheduler(), services.nextAnnouncement(), broadcaster, mergedConfig, running::get);
        // The admin panel reuses the SP0 GUI framework over the shared catalog and the data-folder layout loader. It
        // flips the live ChatLock, runs the /clearchat fan-out behind a confirm, broadcasts a captured line through
        // the same broadcaster as /broadcast, and opens a read-only announcer list. Only the surfaces the commands
        // expose, no new domain logic. /communication gui and the /uxmess gui hub entry both open it.
        GuiText guiText = new GuiText(kernel.messages());
        // The DB-backed announcement editor bare /announce (and /announce editor) opens: a list of the store
        // announcements with a create button, each opening a per-announcement property editor that writes through
        // the AnnouncementStore. The announcer's merged source re-reads the store each tick, so an edit here takes
        // effect on the next tick with no reload. The admin panel's announcer list stays the read-only /announce
        // list surface (it shows the merged config view); the editor is the writable surface, reached via /announce.
        AnnouncementEditorView editorView = new AnnouncementEditorView(
                menus,
                guiText,
                kernel.scheduler(),
                kernel.messages(),
                announcementStore,
                announcerSettingsStore,
                guiLayouts,
                textInput);
        CommunicationAdminMenu adminMenu = new CommunicationAdminMenu(
                menus,
                guiText,
                kernel.scheduler(),
                kernel.messages(),
                chatLock,
                broadcaster,
                CommunicationCommands.BROADCAST_PREFIX,
                mergedConfig,
                notifier,
                kernel.messageSink(),
                textInput);
        adminMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        List<CommandRegistration> commands = new ArrayList<>(CommunicationCommands.all(
                services.broadcastOptOut(),
                registry,
                infoSender,
                notifier,
                kernel.messages(),
                broadcaster,
                announcer,
                mergedConfig,
                kernel.scheduler(),
                kernel.messageSink(),
                chatLock,
                settings,
                editorView));
        commands.add(new CommunicationGuiCommand(adminMenu, kernel.messages()));
        // LuckPerms-backed prefix/suffix/group when installed, an empty fallback otherwise (probed once here).
        ChatMetaSource chatMeta = ChatMetaSources.create(plugin.getServer());
        // PlaceholderAPI-backed %token% expansion for the chat format when installed, identity otherwise (probed once).
        ChatPlaceholderExpander chatPlaceholders = ChatPlaceholderExpander.create();
        List<Listener> listeners = listeners(
                services,
                registry,
                infoSender,
                settings,
                chatLock,
                notifier,
                channelBroadcaster,
                optOutStore,
                kernel.scheduler(),
                chatMeta,
                chatPlaceholders);
        // The same re-read /announce reload performs, exposed so /uxmess reload communication applies the module's
        // file edits through one path rather than a second copy that could drift from it.
        Runnable reload = () -> {
            settings.reload();
            announcer.rearmOverrides();
        };
        return new Wired(
                List.copyOf(commands), listeners, announcer, running, chatLock, optOutStore, adminMenu, reload);
    }

    /**
     * Gather everything an announcement's display condition needs from the live player, their permission check,
     * world and gamemode names, and the per-viewer PlaceholderAPI bridge so a {@code %papi%} comparison expands the
     * same way the rendered announcement lines do. Mirrors the scoreboard/tablist renderers' condition context.
     */
    private static ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    private static CommunicationServices assemble(
            KernelPorts kernel,
            CommunicationSettings settings,
            Supplier<AnnouncerConfig> mergedConfig,
            BroadcastOptOutStore optOutStore,
            RandomSource random,
            Notifier notifier) {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random);
        return new CommunicationServices(
                // The connection policies still read straight off the live settings; only the announcer source
                // widened to the config + enabled-store merge. Join reads the per-group table and the first-join
                // welcome; the listener supplies the joiner's primary group and first-join flag.
                new ResolveJoinMessage(engine, settings::joinPolicies, settings::firstJoinPolicy),
                new ResolveQuitMessage(engine, settings::quitPolicy),
                new ResolveDeathMessage(engine, settings::deathCausePolicies),
                // The rotation cursor selects only over the merged announcements WITHOUT an interval override; each
                // override announcement runs on its own independent timer driven by the AnnouncerTask.
                new NextAnnouncement(() -> mergedConfig.get().rotating(), random),
                new BroadcastOptOut(optOutStore, notifier, kernel.events(), Clock.systemUTC()));
    }

    private static List<Listener> listeners(
            CommunicationServices services,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            CommunicationSettings settings,
            ChatLock chatLock,
            Notifier notifier,
            ChannelBroadcaster channelBroadcaster,
            BroadcastOptOutStore optOutStore,
            Scheduler scheduler,
            ChatMetaSource chatMeta,
            ChatPlaceholderExpander chatPlaceholders) {
        return List.of(
                new ConnectionMessageListener(
                        services.resolveJoin(), services.resolveQuit(), settings, infoSender, chatMeta),
                new DeathMessageListener(services.resolveDeath(), registry, infoSender, settings),
                // English-only project (a founding decision): vanilla advancement titles are translatable components,
                // so the listener renders them through the GlobalTranslator in this locale before flattening to text.
                new AdvancementMessageListener(
                        settings::advancementNotices,
                        channelBroadcaster,
                        optOutStore,
                        CommunicationWiring::probeVanished,
                        scheduler,
                        Locale.ENGLISH),
                new ChatLockListener(chatLock, notifier),
                // Registered at NORMAL, before the ChatLock at HIGH: a locked chat is cancelled there and a
                // cancelled event never reaches the renderer, so the format can never override the lock.
                new ChatFormatListener(settings::chatFormatPolicy, chatMeta, chatPlaceholders));
    }

    /**
     * Whether {@code earner} is currently vanished, derived from Bukkit's own {@code Player#canSee} visibility graph
     * the same soft-coupling seam messaging's {@code CanSeeVanishVisibility}, nametags, and teleport's {@code /tpa}
     * use. The presence module hides a vanished player from those without the vanish-see node, so an earner whom at
     * least one other online player cannot see is treated as vanished and their advancement is suppressed. When
     * presence is disabled nobody is hidden, {@code canSee} is always true, and the earner is never resolved as
     * vanished, so the feature degrades to "broadcast everyone's advancement" without depending on presence directly.
     * A solo earner (no other online player) is never vanished here: there is no one to be hidden from.
     *
     * <p>This enumerates the whole roster and reads every other player's {@code canSee} visibility, a cross-region
     * read that tears on Folia off the global region, so it is only legal on the global region thread. The
     * advancement listener already runs this predicate inside a {@code scheduler.onGlobal} hop, so the read here is a
     * plain inline scan with no marshal of its own.
     */
    private static boolean probeVanished(Player earner) {
        for (Player other : earner.getServer().getOnlinePlayers()) {
            if (!other.equals(earner) && !other.canSee(earner)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything the communication module contributes once wired: the Brigadier commands (static
     * {@code /broadcasttoggle} plus the config-derived info pages), the connection/death listeners, the
     * self-rescheduling announcer timer, and the {@code running} flag the timer observes.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit/death listeners to register
     * @param announcer the self-rescheduling announcer timer, armed by the caller
     * @param running the flag flipped false on stop so the announcer exits
     * @param chatLock the global chat lock the PAPI seam reads the chat-enabled state from
     * @param optOutStore the per-player announcer subscription the PAPI seam reads the broadcast state from
     * @param adminMenu the engine-rendered admin panel the {@code /uxmess gui} hub entry opens
     * @param reload the module's file re-read, shared by {@code /announce reload} and {@code /uxmess reload}
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            AnnouncerTask announcer,
            AtomicBoolean running,
            ChatLock chatLock,
            BroadcastOptOutStore optOutStore,
            CommunicationAdminMenu adminMenu,
            Runnable reload) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(announcer, "announcer");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(chatLock, "chatLock");
            Objects.requireNonNull(optOutStore, "optOutStore");
            Objects.requireNonNull(adminMenu, "adminMenu");
            Objects.requireNonNull(reload, "reload");
        }

        /** Arm the announcer timer. */
        public void startBackgroundWork() {
            announcer.start();
        }

        /** Stop the announcer timer: flip the running flag and cancel every armed override loop. */
        public void stop() {
            running.set(false);
            announcer.stop();
        }
    }
}
