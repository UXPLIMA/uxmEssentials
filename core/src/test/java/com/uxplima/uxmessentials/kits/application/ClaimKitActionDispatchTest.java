package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitActionRunner;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitAction;
import com.uxplima.uxmessentials.kits.domain.KitActionType;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The action-engine dispatch contract {@link ClaimKit} owns: on a successful claim the before-items claim
 * actions run, then the items are granted, then the after-items claim actions run, in that order, and on a
 * refused claim the kit's deny actions run instead (and nothing is granted). A single shared transcript records
 * every grant and every action the {@link KitActionRunner} is handed, so the assertions read the exact ordering
 * the player would observe.
 */
class ClaimKitActionDispatchTest {

    private final List<String> transcript = new ArrayList<>();
    private FakeKitRepository repository;
    private StubPermissions permissions;
    private FakeCooldowns cooldowns;
    private FakeClaimStore claims;
    private RecordingGranter granter;
    private RecordingRunner runner;
    private Notifier notifier;
    private PlayerRef alice;

    @BeforeEach
    void setUp() {
        repository = new FakeKitRepository();
        permissions = new StubPermissions();
        cooldowns = new FakeCooldowns();
        claims = new FakeClaimStore();
        granter = new RecordingGranter(transcript);
        runner = new RecordingRunner(transcript);
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @Test
    void successRunsBeforeActionsThenGrantThenAfterActions() {
        KitDefinition kit = repeatable("starter")
                .withClaimActions(List.of(
                        new KitAction(KitActionType.BROADCAST, "before", true, false),
                        new KitAction(KitActionType.SOUND, "after", false, false)));
        repository.save(kit);

        Result<Unit, KitError> result = claimKit().claim(alice, KitId.of("starter"));

        assertThat(result.isOk()).isTrue();
        assertThat(transcript).containsExactly("action:BROADCAST", "grant", "action:SOUND");
    }

    @Test
    void aClaimWithNoActionsStillGrantsTheItems() {
        repository.save(repeatable("plain"));

        claimKit().claim(alice, KitId.of("plain"));

        assertThat(transcript).containsExactly("grant");
    }

    @Test
    void aRefusedClaimRunsDenyActionsAndGrantsNothing() {
        KitDefinition gated = KitDefinition.builder()
                .id(KitId.of("vip"))
                .items(items())
                .permission(true)
                .build()
                .withDenyActions(List.of(new KitAction(KitActionType.SOUND, "ENTITY_VILLAGER_NO;1;1", false, false)));
        repository.save(gated);

        Result<Unit, KitError> result = claimKit().claim(alice, KitId.of("vip"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.NO_PERMISSION);
        assertThat(transcript).containsExactly("action:SOUND");
    }

    @Test
    void aSuccessfulClaimRunsNoDenyActions() {
        KitDefinition kit = repeatable("starter")
                .withClaimActions(List.of(new KitAction(KitActionType.MESSAGE, "yay", false, false)))
                .withDenyActions(List.of(new KitAction(KitActionType.SOUND, "no", false, false)));
        repository.save(kit);

        claimKit().claim(alice, KitId.of("starter"));

        assertThat(transcript).containsExactly("grant", "action:MESSAGE");
    }

    private ClaimKit claimKit() {
        KitAccess access = new KitAccess(permissions, cooldowns, claims, Optional.empty());
        return new ClaimKit(
                repository,
                access,
                granter,
                notifier,
                new CapturingEvents(),
                Clock.system(ZoneOffset.UTC),
                Optional.empty(),
                Optional.of(runner));
    }

    private static KitDefinition repeatable(String id) {
        return KitDefinition.repeatable(KitId.of(id), items(), Duration.ZERO);
    }

    private static List<KitItem> items() {
        return List.of(KitItem.of("payload", 1));
    }

    /** A runner that appends each action it is handed to the shared transcript, preserving order. */
    private static final class RecordingRunner implements KitActionRunner {
        private final List<String> transcript;

        RecordingRunner(List<String> transcript) {
            this.transcript = transcript;
        }

        @Override
        public void run(PlayerRef who, KitDefinition kit, List<KitAction> actions) {
            for (KitAction action : actions) {
                transcript.add("action:" + action.type());
            }
        }
    }

    private static final class RecordingGranter implements KitGranter {
        private final List<String> transcript;

        RecordingGranter(List<String> transcript) {
            this.transcript = transcript;
        }

        @Override
        public Grant grant(PlayerRef recipient, KitDefinition kit) {
            transcript.add("grant");
            return Grant.complete();
        }
    }

    private static final class FakeKitRepository implements KitRepository {
        private final Map<String, KitDefinition> byId = new java.util.LinkedHashMap<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public boolean exists(KitId id) {
            return byId.containsKey(id.value());
        }

        @Override
        public void save(KitDefinition definition) {
            byId.put(definition.id().value(), definition);
        }

        @Override
        public void delete(KitId id) {
            byId.remove(id.value());
        }
    }

    private static final class FakeCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            // no-op: cooldown stamping is asserted elsewhere
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            // unused
        }
    }

    private static final class FakeClaimStore implements KitClaimStore {
        private final Map<UUID, Set<String>> claimed = new HashMap<>();

        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return claimed.getOrDefault(who.uuid(), Set.of()).contains(kit.value());
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {
            claimed.computeIfAbsent(who.uuid(), u -> new HashSet<>()).add(kit.value());
        }

        @Override
        public void reset(PlayerRef who, KitId kit) {
            claimed.getOrDefault(who.uuid(), new HashSet<>()).remove(kit.value());
        }

        @Override
        public void resetAll(PlayerRef who) {
            claimed.remove(who.uuid());
        }
    }

    private static final class StubPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: feedback delivery is not under test here
        }
    }

    private static final class CapturingEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event publication asserted elsewhere
        }
    }
}
