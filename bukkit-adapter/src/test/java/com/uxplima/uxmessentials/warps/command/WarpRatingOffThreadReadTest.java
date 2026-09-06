package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpCommand;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpBrowseMenu;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpInfo;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins that {@code /warp rate} and {@code /warp rating} resolve the rating store <em>off</em> the command
 * (tick/region) thread. The warp name resolve is served from the in-memory set, but a rating is deliberately
 * not cached ({@code rate}/{@code averageRating} pass straight through to the database) so those two paths
 * must hand the read/write to {@link Scheduler#async} and bridge their feedback back to the player's region
 * thread, the same shape {@code /pwarp rate}/{@code /pwarp rating} use.
 *
 * <p>The scheduler here is a <em>deferring</em> double: {@code async} captures the task without running it, and
 * {@code onEntity} runs inline (the region bridge). So after dispatch the rating store has seen zero
 * read/writes (proving the database touch did not run on the command thread) and only once the captured task
 * is drained does the rating land and the feedback reach the player.
 */
class WarpRatingOffThreadReadTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private ServerMock server;
    private PlayerMock player;
    private CountingRatingRepository repository;
    private DeferringScheduler scheduler;
    private WarpServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true); // the /warp rate|rating nodes gate on a permission; op satisfies it without wiring
        repository = new CountingRatingRepository();
        scheduler = new DeferringScheduler();
        services = services();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rateWritesTheRatingOffTheCommandThread() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "warp rate spawn 5.0");

        // The command returned without writing the rating: the write was handed to scheduler.async.
        assertThat(repository.rateWrites).isZero();
        assertThat(scheduler.deferred).hasSize(1);

        scheduler.drain();

        // Draining the captured task is what performed the rating write and the confirmation, which the region
        // bridge (onEntity, run inline here) delivered to the player.
        assertThat(repository.rateWrites).isEqualTo(1);
        assertThat(player.nextMessage()).isNotNull();
    }

    @Test
    void ratingReadsTheAverageOffTheCommandThread() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "warp rating spawn");

        assertThat(repository.averageReads).isZero();
        assertThat(scheduler.deferred).hasSize(1);

        scheduler.drain();

        assertThat(repository.averageReads).isEqualTo(1);
        assertThat(player.nextMessage()).isNotNull();
    }

    private CommandDispatcher<CommandSourceStack> registerCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(new WarpCommand(
                                services,
                                new KeyMessages(),
                                () -> com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.CHAT)
                        .build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private WarpServices services() {
        Messages messages = new KeyMessages();
        RecordingSink sink = new RecordingSink();
        Notifier notifier = new Notifier(messages, sink);
        Permissions permissions = new AllowAllPermissions();
        WarpAccess access = new WarpAccess(permissions, Optional.<WarpEconomy>empty());
        UseWarp useWarp =
                new UseWarp(repository, access, new NoTeleport(), notifier, pos -> true, permissions, scheduler);
        // The browse menu is a WarpServices collaborator this rating test never opens, so it stands up over a bare
        // test engine façade with no spec registered.
        WarpBrowseMenu warpMenu = new WarpBrowseMenu(
                TestMenuEngine.create(messages, scheduler).menus(),
                scheduler,
                useWarp,
                messages,
                new StubWarpCategoryRepository());
        return new WarpServices(
                useWarp,
                new SetWarp(repository, notifier, event -> {}, Clock.systemUTC(), List.of()),
                new DelWarp(repository, notifier, event -> {}),
                new ListWarps(repository, permissions, notifier),
                new WarpInfo(repository, notifier),
                new MoveWarp(repository, notifier),
                warpMenu,
                new NoPlayerLookup(),
                repository,
                null,
                null,
                scheduler);
    }

    /** Serves one warp from memory (the name resolve never touches the database) and counts rating touches. */
    private static final class CountingRatingRepository implements WarpRepository {
        private final Map<String, Warp> byName = new LinkedHashMap<>();
        private int rateWrites;
        private int averageReads;

        CountingRatingRepository() {
            Warp spawn = Warp.create(
                    WarpName.of("spawn"),
                    Position.of(WORLD, 0, 64, 0),
                    new PlayerRef(UUID.randomUUID(), "Owner"),
                    Instant.ofEpochMilli(1_000));
            byName.put(spawn.name().value(), spawn);
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            byName.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            byName.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {
            rateWrites++;
        }

        @Override
        public double averageRating(WarpName name) {
            averageReads++;
            return 4.0;
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

    private static final class NoTeleport implements WarpTeleporter {
        @Override
        public void teleportTo(PlayerRef who, Warp warp) {}
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

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
