package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetNpcSkinTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetNpcSkin setSkin;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        setSkin = new SetNpcSkin(repository, view, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void reskinsSavesRendersAndNotifies() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = setSkin.setSkin(actor, NpcName.of("guide"), new NpcSkin("tex", "sig"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().skin()).isEqualTo(new NpcSkin("tex", "sig"));
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_SET.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = setSkin.setSkin(actor, NpcName.of("ghost"), NpcSkin.unsigned("tex"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }

    @Test
    void rejectsReskinningAMobNpcLeavingItUntouched() {
        repository.save(Npc.create(NpcName.of("mob"), AT, null, Instant.ofEpochMilli(1_000))
                .withEntityType("VILLAGER"));

        Result<Unit, NpcError> result = setSkin.setSkin(actor, NpcName.of("mob"), new NpcSkin("tex", "sig"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.SKIN_ONLY_PLAYER);
        // The stored NPC is untouched (no skin applied, no render): it stays a skinless villager.
        assertThat(repository.find(NpcName.of("mob")).orElseThrow().skin()).isNull();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_SKIN_ONLY_PLAYER.key());
    }
}
