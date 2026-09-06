package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@code /npc skin name:<username>} orchestration with fakes, no MockBukkit, no live Mojang. Pins
 * the four observable behaviours: a missing NPC is rejected before any fetch, a present fetch result is applied
 * through {@link SetNpcSkin} (saved + rendered), an empty result reports a fetch failure, and the apply step
 * runs on the bridged scheduler rather than the calling thread.
 */
class NpcSkinByNameTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final NpcName GUIDE = NpcName.of("guide");

    private FakeRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private FakeSkinService skins;
    private BridgingScheduler scheduler;
    private PlayerRef actor;
    private NpcSkinByName flow;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        skins = new FakeSkinService();
        scheduler = new BridgingScheduler();
        Notifier notifier = new Notifier(new KeyMessages(), sink);
        SetNpcSkin setSkin = new SetNpcSkin(repository, view, notifier);
        flow = new NpcSkinByName(skins, setSkin, repository, notifier, scheduler);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void rejectsAMissingNpcBeforeAnyFetch() {
        boolean accepted = flow.apply(actor, GUIDE, "Notch");

        assertThat(accepted).isFalse();
        assertThat(skins.requested).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }

    @Test
    void sendsFetchingThenAppliesAResolvedSkin() {
        repository.save(Npc.create(GUIDE, AT, null, Instant.ofEpochMilli(1_000)));

        boolean accepted = flow.apply(actor, GUIDE, "Notch");
        assertThat(accepted).isTrue();
        assertThat(skins.requested).containsExactly("Notch");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_FETCHING.key());

        skins.complete(Optional.of(new NpcSkin("notchtex", "sig")));

        assertThat(scheduler.bridged).isTrue();
        assertThat(repository.find(GUIDE).orElseThrow().skin()).isEqualTo(new NpcSkin("notchtex", "sig"));
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_SET.key());
    }

    @Test
    void reportsAFetchFailureWhenNoSkinResolves() {
        repository.save(Npc.create(GUIDE, AT, null, Instant.ofEpochMilli(1_000)));

        flow.apply(actor, GUIDE, "Ghost");
        skins.complete(Optional.empty());

        assertThat(scheduler.bridged).isTrue();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_FETCH_FAILED.key());
    }

    @Test
    void urlFlowRejectsAMissingNpcBeforeAnyGenerate() {
        boolean accepted = flow.applyFromUrl(actor, GUIDE, "https://example.com/skin.png");

        assertThat(accepted).isFalse();
        assertThat(skins.urlRequested).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }

    @Test
    void urlFlowSendsFetchingThenAppliesAGeneratedSkin() {
        repository.save(Npc.create(GUIDE, AT, null, Instant.ofEpochMilli(1_000)));

        boolean accepted = flow.applyFromUrl(actor, GUIDE, "https://example.com/skin.png");
        assertThat(accepted).isTrue();
        assertThat(skins.urlRequested).containsExactly("https://example.com/skin.png");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_FETCHING_URL.key());

        skins.complete(Optional.of(new NpcSkin("gentex", "gensig")));

        assertThat(scheduler.bridged).isTrue();
        assertThat(repository.find(GUIDE).orElseThrow().skin()).isEqualTo(new NpcSkin("gentex", "gensig"));
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_SET.key());
    }

    @Test
    void urlFlowReportsAGenerateFailureWhenNoSkinResolves() {
        repository.save(Npc.create(GUIDE, AT, null, Instant.ofEpochMilli(1_000)));

        flow.applyFromUrl(actor, GUIDE, "https://example.com/missing.png");
        skins.complete(Optional.empty());

        assertThat(scheduler.bridged).isTrue();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_GENERATE_FAILED.key());
    }

    private static final class FakeSkinService implements SkinService {
        private final List<String> requested = new ArrayList<>();
        private final List<String> urlRequested = new ArrayList<>();
        private CompletableFuture<Optional<NpcSkin>> pending = new CompletableFuture<>();

        @Override
        public CompletableFuture<Optional<NpcSkin>> fetchByName(String username) {
            requested.add(username);
            return pending;
        }

        @Override
        public CompletableFuture<Optional<NpcSkin>> fetchFromUrl(String imageUrl) {
            urlRequested.add(imageUrl);
            return pending;
        }

        void complete(Optional<NpcSkin> skin) {
            pending.complete(skin);
        }
    }

    /** Marks that the apply step was bridged through the scheduler rather than run inline on the caller. */
    private static final class BridgingScheduler implements Scheduler {
        private boolean bridged;

        @Override
        public void onGlobal(Runnable task) {
            bridged = true;
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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class FakeRepository implements NpcRepository {
        private final Map<String, Npc> byName = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            byName.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            byName.remove(name.value());
        }
    }

    private static final class RecordingView implements NpcView {
        private final List<Npc> rendered = new ArrayList<>();

        @Override
        public void render(Npc npc) {
            rendered.add(npc);
        }

        @Override
        public void despawn(NpcName name) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        private final Map<UUID, List<String>> delivered = new LinkedHashMap<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.computeIfAbsent(viewer.uuid(), u -> new ArrayList<>()).add(renderedText);
        }

        List<String> textFor(PlayerRef who) {
            return delivered.getOrDefault(who.uuid(), List.of());
        }
    }
}
