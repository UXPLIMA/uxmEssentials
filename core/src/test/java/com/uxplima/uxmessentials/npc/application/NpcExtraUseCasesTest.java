package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.KeyMessages;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingEvents;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers the extra appearance use cases: each new field round-trips through its use case, saves and re-renders. */
class NpcExtraUseCasesTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final NpcName GUIDE = NpcName.of("guide");

    private FakeNpcRepository repository;
    private RecordingView view;
    private Notifier notifier;
    private RecordingEvents events;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        events = new RecordingEvents();
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(GUIDE, AT, null, Instant.ofEpochMilli(1_000)));
    }

    private Npc reload() {
        return repository.find(GUIDE).orElseThrow();
    }

    @Test
    void setsAndClearsTheDisplayName() {
        new SetNpcDisplayName(repository, view, notifier).setDisplayName(actor, GUIDE, "<gold>Town Guide");
        assertThat(reload().displayName()).isEqualTo("<gold>Town Guide");
        assertThat(view.rendered).hasSize(1);

        new SetNpcDisplayName(repository, view, notifier).setDisplayName(actor, GUIDE, "  ");
        assertThat(reload().displayName()).isEqualTo(" ");
    }

    @Test
    void setsAndResetsTheInteractionCooldown() {
        new SetNpcInteractionCooldown(repository, notifier).setCooldown(actor, GUIDE, 1_500);
        assertThat(reload().interactionCooldownMillis()).isEqualTo(1_500);

        new SetNpcInteractionCooldown(repository, notifier).setCooldown(actor, GUIDE, 0);
        assertThat(reload().interactionCooldownMillis()).isZero();
    }

    @Test
    void togglesMirrorCollidableAndShowInTab() {
        new SetNpcMirrorSkin(repository, view, notifier).setMirror(actor, GUIDE, true);
        new SetNpcCollidable(repository, view, notifier).setCollidable(actor, GUIDE, true);
        new SetNpcShowInTab(repository, view, notifier).setShowInTab(actor, GUIDE, true);

        Npc loaded = reload();
        assertThat(loaded.mirrorSkin()).isTrue();
        assertThat(loaded.collidable()).isTrue();
        assertThat(loaded.showInTab()).isTrue();
    }

    @Test
    void setsAndResetsTheViewAndTurnDistances() {
        SetNpcRange range = new SetNpcRange(repository, view, notifier);
        range.setRange(actor, GUIDE, SetNpcRange.Kind.VIEW, 64.0);
        range.setRange(actor, GUIDE, SetNpcRange.Kind.TURN, 8.0);
        assertThat(reload().viewDistance()).isEqualTo(64.0);
        assertThat(reload().turnDistance()).isEqualTo(8.0);

        range.setRange(actor, GUIDE, SetNpcRange.Kind.VIEW, null);
        assertThat(reload().viewDistance()).isNull();
    }

    @Test
    void togglesEachStateFlagIndependently() {
        SetNpcState state = new SetNpcState(repository, view, notifier);
        state.setState(actor, GUIDE, SetNpcState.Flag.ON_FIRE, true);
        state.setState(actor, GUIDE, SetNpcState.Flag.INVISIBLE, true);
        state.setState(actor, GUIDE, SetNpcState.Flag.SILENT, true);

        Npc loaded = reload();
        assertThat(loaded.onFire()).isTrue();
        assertThat(loaded.invisible()).isTrue();
        assertThat(loaded.silent()).isTrue();

        state.setState(actor, GUIDE, SetNpcState.Flag.ON_FIRE, false);
        Npc cleared = reload();
        assertThat(cleared.onFire()).isFalse();
        assertThat(cleared.invisible()).isTrue();
    }

    @Test
    void movesToExplicitCoordinatesKeepingTheWorld() {
        new MoveNpcTo(repository, view, notifier, events).moveTo(actor, GUIDE, 100.5, 70.0, -40.0, 90f, 10f);

        Npc loaded = reload();
        assertThat(loaded.location().x()).isEqualTo(100.5);
        assertThat(loaded.location().y()).isEqualTo(70.0);
        assertThat(loaded.location().z()).isEqualTo(-40.0);
        assertThat(loaded.location().yaw()).isEqualTo(90f);
        assertThat(loaded.location().world()).isEqualTo(WORLD);
    }

    @Test
    void setsTheSkinSlimVariantOnAPlayerNpcWithASkin() {
        repository.save(reload().withSkin(new NpcSkin("tex", "sig")));

        new SetNpcSkinSlim(repository, view, notifier).setSlim(actor, GUIDE, true);

        NpcSkin skin = java.util.Objects.requireNonNull(reload().skin(), "skin");
        assertThat(skin.slim()).isTrue();
    }

    @Test
    void rejectsSkinSlimOnAnNpcWithNoSkin() {
        var result = new SetNpcSkinSlim(repository, view, notifier).setSlim(actor, GUIDE, true);
        // No skin set, so there is nothing to vary: the edit is rejected and nothing re-renders.
        assertThat(result.isOk()).isFalse();
        assertThat(view.rendered).isEmpty();
    }

    @Test
    void rejectsAnUnknownNameForEveryNewUseCase() {
        NpcName ghost = NpcName.of("ghost");
        assertThat(new SetNpcDisplayName(repository, view, notifier)
                        .setDisplayName(actor, ghost, "x")
                        .errorOrThrow())
                .isEqualTo(NpcError.NOT_FOUND);
        assertThat(new SetNpcMirrorSkin(repository, view, notifier)
                        .setMirror(actor, ghost, true)
                        .errorOrThrow())
                .isEqualTo(NpcError.NOT_FOUND);
        assertThat(new MoveNpcTo(repository, view, notifier, events)
                        .moveTo(actor, ghost, 0, 0, 0, 0f, 0f)
                        .errorOrThrow())
                .isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
    }
}
