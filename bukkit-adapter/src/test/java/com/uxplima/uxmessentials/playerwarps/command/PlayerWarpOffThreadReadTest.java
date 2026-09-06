package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommand;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpBrowseMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpCategoriesMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorSubLayouts;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListMenu;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.FavouritePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.ManageBans;
import com.uxplima.uxmessentials.playerwarps.application.ManageMembers;
import com.uxplima.uxmessentials.playerwarps.application.ManageWhitelist;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.RatePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.TransferPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.application.WithdrawEarnings;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.BayesianRating;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins that {@code /pwarp} resolves the player-warp store <em>off</em> the command (tick/region) thread. A
 * {@code /pwarp} subcommand that reads the database to decide its outcome. Here {@code edit}, which looks the warp
 * up before opening its editor or reporting it missing. Must not read on the thread Brigadier dispatched it on; the
 * read belongs in a {@link Scheduler#async} task that bridges its feedback back to the player's region thread, the
 * same shape {@code /home} uses.
 *
 * <p>The scheduler here is a <em>deferring</em> double: {@code async} captures the task without running it, and
 * {@code onEntity} runs inline (the region bridge). So after dispatch the repository has seen zero reads
 * proving the lookup did not run on the command thread, and only once the captured task is drained does the
 * read happen and the feedback land.
 */
class PlayerWarpOffThreadReadTest {

    private ServerMock server;
    private org.bukkit.plugin.Plugin plugin;
    private PlayerMock player;
    private CountingRepository repository;
    private DeferringScheduler scheduler;
    private RecordingSink sink;
    private PlayerWarpServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true); // the /pwarp node gates on a permission; op satisfies it without a permission wiring
        repository = new CountingRepository();
        scheduler = new DeferringScheduler();
        sink = new RecordingSink();
        services = services();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void editResolvesTheWarpOffTheCommandThread() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "pwarp edit hub");

        // The command returned without touching the database: the existence read was handed to scheduler.async.
        assertThat(repository.reads).isZero();
        assertThat(scheduler.deferred).hasSize(1);

        scheduler.drain();

        // Draining the captured task is what performed the existence read and the not-found feedback, which the
        // region bridge (onEntity, run inline here) delivered to the player: no warp named "hub" exists.
        assertThat(repository.reads).isPositive();
        assertThat(player.nextMessage()).isNotNull();
    }

    @Test
    void accessEditResolvesTheWarpOffTheCommandThread() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "pwarp access hub PUBLIC");

        // The edit verb returned without touching the database: the warp lookup was handed to scheduler.async.
        assertThat(repository.reads).isZero();
        assertThat(scheduler.deferred).hasSize(1);

        scheduler.drain();

        // Draining performs the EditPlayerWarp lookup (a read) off the command thread.
        assertThat(repository.reads).isPositive();
    }

    private CommandDispatcher<CommandSourceStack> registerCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new PlayerWarpCommand(services, new KeyMessages()).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private PlayerWarpServices services() {
        Notifier notifier = new Notifier(new KeyMessages(), sink);
        Permissions permissions = new AllowAllPermissions();
        Messages messages = new KeyMessages();
        SetPlayerWarp setPlayerWarp = new SetPlayerWarp(
                repository,
                new PlayerWarpQuota(permissions, 3),
                notifier,
                event -> {},
                java.time.Clock.systemUTC(),
                List.of());
        ArchivePlayerWarp archivePlayerWarp = new ArchivePlayerWarp(
                repository, new WarpAuthorization(new NoMembers()), notifier, event -> {}, java.time.Clock.systemUTC());
        SetPlayerWarpVisibility visibility =
                new SetPlayerWarpVisibility(repository, notifier, java.time.Clock.systemUTC());
        return new PlayerWarpServices(
                setPlayerWarp,
                archivePlayerWarp,
                new UsePlayerWarp(
                        repository,
                        new NoTeleport(),
                        notifier,
                        position -> true,
                        permissions,
                        new NoBans(),
                        new NoMembers(),
                        new NoWhitelist(),
                        new NoPasswords(),
                        new OpenCooldowns(),
                        Optional.empty(),
                        "local",
                        Optional.empty(),
                        java.time.Clock.systemUTC()),
                new ListPlayerWarps(repository, notifier),
                visibility,
                new NamingLookup(),
                repository,
                null,
                scheduler,
                listView(messages, permissions, setPlayerWarp, visibility, archivePlayerWarp),
                browseView(messages, permissions, notifier),
                categoriesView(messages),
                new RatePlayerWarp(
                        repository,
                        new NoRatings(),
                        notifier,
                        new BayesianRating(10),
                        java.time.Clock.systemUTC(),
                        java.util.Optional.empty()),
                new FavouritePlayerWarp(repository, new NoFavourites(), notifier),
                new ManageMembers(
                        repository,
                        new WarpAuthorization(new NoMembers()),
                        new NoMembers(),
                        notifier,
                        java.time.Clock.systemUTC()),
                new ManageWhitelist(repository, new WarpAuthorization(new NoMembers()), new NoWhitelist(), notifier),
                new ManageBans(
                        repository,
                        new WarpAuthorization(new NoMembers()),
                        new NoBans(),
                        notifier,
                        java.time.Clock.systemUTC()),
                new WithdrawEarnings(repository, new WarpAuthorization(new NoMembers()), notifier, Optional.empty()),
                new EditPlayerWarp(
                        repository,
                        new WarpAuthorization(new NoMembers()),
                        notifier,
                        new NoPasswords(),
                        java.time.Clock.systemUTC()),
                new TransferPlayerWarp(
                        repository, new WarpAuthorization(new NoMembers()), notifier, java.time.Clock.systemUTC()),
                new com.uxplima.uxmessentials.playerwarps.application.BuySponsorship(
                        repository,
                        new WarpAuthorization(new NoMembers()),
                        Optional.empty(),
                        notifier,
                        com.uxplima.uxmessentials.playerwarps.application.SponsorConfig.disabled(),
                        java.time.Clock.systemUTC()),
                com.uxplima.uxmessentials.playerwarps.application.SponsorConfig.disabled());
    }

    /** A minimal real management list (this test does not open it; it only needs a non-null view in services). */
    private PlayerWarpListMenu listView(
            Messages messages,
            Permissions permissions,
            SetPlayerWarp setPlayerWarp,
            SetPlayerWarpVisibility visibility,
            ArchivePlayerWarp archivePlayerWarp) {
        GuiText guiText = new GuiText(messages);
        TextInput textInput =
                TextInputTestKit.create(plugin, guiText, scheduler, java.nio.file.Path.of("nonexistent"), NOOP);
        EntityEditorLayout editorLayout = new EntityEditorLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22),
                49,
                java.util.OptionalInt.of(53),
                org.bukkit.Material.ARROW,
                org.bukkit.Material.BARRIER,
                org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        PlayerWarpEditorView editor = new PlayerWarpEditorView(
                menus,
                guiText,
                scheduler,
                repository,
                visibility,
                archivePlayerWarp,
                textInput,
                messages,
                editorLayout,
                PlayerWarpEditorSubLayouts.codeDefault(),
                (p, v) -> {});
        return new PlayerWarpListMenu(
                menus, scheduler, permissions, messages, repository, setPlayerWarp, textInput, editor);
    }

    /** A minimal browse menu over a stub read model, wired so the services holder is complete; unopened by this test. */
    private PlayerWarpBrowseMenu browseView(Messages messages, Permissions permissions, Notifier notifier) {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        PlayerWarpBrowse browse =
                query -> com.uxplima.uxmessentials.playerwarps.domain.Page.empty(query.page(), query.pageSize());
        UsePlayerWarp use = new UsePlayerWarp(
                repository,
                new NoTeleport(),
                notifier,
                position -> true,
                permissions,
                new NoBans(),
                new NoMembers(),
                new NoWhitelist(),
                new NoPasswords(),
                new OpenCooldowns(),
                Optional.empty(),
                "local",
                Optional.empty(),
                java.time.Clock.systemUTC());
        return new PlayerWarpBrowseMenu(
                menus,
                scheduler,
                browse,
                use,
                messages,
                (viewer, name) -> {},
                com.uxplima.uxmessentials.playerwarps.application.SponsorConfig.disabled());
    }

    /** A minimal categories landing over an empty category set, wired so the services holder is complete; unopened here. */
    private PlayerWarpCategoriesMenu categoriesView(Messages messages) {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        WarpCategoryRepository categories = new WarpCategoryRepository() {
            @Override
            public Optional<WarpCategory> find(String id) {
                return Optional.empty();
            }

            @Override
            public List<WarpCategory> all() {
                return List.of();
            }

            @Override
            public void save(WarpCategory category) {}

            @Override
            public void delete(String id) {}
        };
        PlayerWarpBrowse browse =
                query -> com.uxplima.uxmessentials.playerwarps.domain.Page.empty(query.page(), query.pageSize());
        return new PlayerWarpCategoriesMenu(
                menus,
                scheduler,
                categories,
                messages,
                browse,
                (player, viewer, filters) -> {},
                (viewer, name) -> {},
                com.uxplima.uxmessentials.playerwarps.application.SponsorConfig.disabled());
    }

    /** Counts repository reads and serves warps from memory, assigning a surrogate id on the first save of a warp. */
    private static final class CountingRepository implements PlayerWarpRepository {
        private final Map<String, PlayerWarp> byName = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong();
        private int reads;

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            reads++;
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            reads++;
            return byName.values().stream()
                    .filter(warp -> warp.id().filter(id::equals).isPresent())
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            reads++;
            return List.copyOf(byName.values());
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            reads++;
            return List.copyOf(byName.values());
        }

        @Override
        public int count(PlayerRef owner) {
            reads++;
            return byName.size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            reads++;
            return byName.containsKey(name.value());
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            PlayerWarpId id = warp.id().orElseGet(() -> PlayerWarpId.of(ids.incrementAndGet()));
            byName.put(warp.name().value(), warp.id().isPresent() ? warp : warp.withId(id));
            return id;
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            byName.values().removeIf(warp -> warp.id().filter(id::equals).isPresent());
        }

        @Override
        public void recordVisit(PlayerWarpId id) {}

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {}

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {}

        @Override
        public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
            return Optional.of(List.copyOf(byName.values()));
        }
    }

    /** Captures async tasks without running them; runs the region bridge inline. */
    private static final class DeferringScheduler implements Scheduler {
        private final List<Runnable> deferred = new ArrayList<>();

        void drain() {
            List<Runnable> snapshot = List.copyOf(deferred);
            deferred.clear();
            snapshot.forEach(Runnable::run);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            deferred.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            deferred.add(task);
        }
    }

    private static final class NoTeleport implements PlayerWarpTeleporter {
        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {}
    }

    private static final class NoRatings implements WarpRatingStore {
        @Override
        public void put(PlayerWarpId warp, UUID player, int stars, java.time.Instant at) {}

        @Override
        public RatingTally tally(PlayerWarpId warp) {
            return RatingTally.empty();
        }

        @Override
        public double globalMean() {
            return 0.0;
        }
    }

    private static final class NoFavourites implements WarpFavouriteStore {
        @Override
        public void add(UUID player, PlayerWarpId warp) {}

        @Override
        public void remove(UUID player, PlayerWarpId warp) {}

        @Override
        public boolean contains(UUID player, PlayerWarpId warp) {
            return false;
        }

        @Override
        public List<PlayerWarpId> listFor(UUID player) {
            return List.of();
        }
    }

    private static final class NoBans implements WarpBanStore {
        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {}

        @Override
        public void unban(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private static final class NoMembers implements WarpMemberStore {
        @Override
        public void put(PlayerWarpId warp, WarpMember member) {}

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private static final class NoWhitelist implements WarpWhitelistStore {
        @Override
        public void add(PlayerWarpId warp, UUID player) {}

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            return false;
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private static final class NoPasswords implements PlayerWarpPasswordStore {
        @Override
        public void set(PlayerWarpId warp, String plaintext) {}

        @Override
        public void clear(PlayerWarpId warp) {}

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return false;
        }
    }

    private static final class OpenCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private final class NamingLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.of(ref());
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return Optional.of(ref());
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(ref());
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
