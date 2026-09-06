package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The async {@code /npc skin <name>} flows that resolve a skin off-thread, {@code name:<username>} (a Mojang
 * lookup) and {@code url:<image-url>} (a MineSkin generate). Kept Bukkit-free so the orchestration is
 * unit-testable: it touches only the application ports, the {@link SkinService}, the {@link Scheduler}, and the
 * {@link Notifier}, with no {@code org.bukkit} type, so a test drives it with fakes and asserts the
 * message/apply sequence without a live server.
 *
 * <p>Both flows share one shape: reject up front when no NPC exists under the name (so a typo never triggers a
 * network call); send the operator an immediate "working" line; then resolve the skin off-thread through the
 * service. On completion it hops back onto the global region thread before touching the stored model, a present
 * skin is applied through {@link SetNpcSkin} (which saves and re-renders), an empty result is reported as a
 * source-specific failure. The command thread is never blocked on the future. Only the source value, the
 * fetch call, and the two feedback keys differ between the two flows.
 */
@NullMarked
public final class NpcSkinByName {

    private final SkinService skinService;
    private final SetNpcSkin setSkin;
    private final NpcRepository repository;
    private final Notifier notifier;
    private final Scheduler scheduler;

    public NpcSkinByName(
            SkinService skinService,
            SetNpcSkin setSkin,
            NpcRepository repository,
            Notifier notifier,
            Scheduler scheduler) {
        this.skinService = Objects.requireNonNull(skinService, "skinService");
        this.setSkin = Objects.requireNonNull(setSkin, "setSkin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Resolve {@code username}'s skin from Mojang and apply it to NPC {@code name} for {@code actor}. Returns
     * {@code false} (and reports {@link NpcMessageKey#NPC_NOT_FOUND}) when no such NPC exists: nothing is
     * fetched; otherwise sends the fetching feedback, kicks the off-thread lookup, and returns {@code true}
     * immediately.
     */
    public boolean apply(PlayerRef actor, NpcName name, String username) {
        Objects.requireNonNull(username, "username");
        return resolveAndApply(
                actor,
                name,
                username,
                NpcMessageKey.NPC_SKIN_FETCHING,
                NpcMessageKey.NPC_SKIN_FETCH_FAILED,
                skinService::fetchByName);
    }

    /**
     * Generate a skin from {@code imageUrl} through MineSkin and apply it to NPC {@code name} for {@code actor}.
     * Same shape as {@link #apply}: rejected up front (with {@link NpcMessageKey#NPC_NOT_FOUND}) when no such NPC
     * exists, otherwise sends the generating feedback and kicks the off-thread generate, returning {@code true}.
     */
    public boolean applyFromUrl(PlayerRef actor, NpcName name, String imageUrl) {
        Objects.requireNonNull(imageUrl, "imageUrl");
        return resolveAndApply(
                actor,
                name,
                imageUrl,
                NpcMessageKey.NPC_SKIN_FETCHING_URL,
                NpcMessageKey.NPC_SKIN_GENERATE_FAILED,
                skinService::fetchFromUrl);
    }

    /**
     * The shared async flow: reject a missing NPC before any network call, send the {@code working} line, then
     * resolve the skin off-thread through {@code fetch} and apply or report on completion. The {@code source}
     * (a username or an image URL) fills the {@code player} placeholder in both feedback lines.
     */
    private boolean resolveAndApply(
            PlayerRef actor,
            NpcName name,
            String source,
            MessageKey working,
            MessageKey failed,
            Function<String, CompletableFuture<Optional<NpcSkin>>> fetch) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(actor, NpcMessageKey.NPC_NOT_FOUND, Map.of("name", name.value()));
            return false;
        }
        notifier.send(actor, working, Map.of("player", source));
        // The service's future never completes exceptionally, so the dependent stage carries no error to chain;
        // the unused handle is the fire-and-forget completion, deliberately not awaited on the command thread.
        var unused = fetch.apply(source).thenAccept(skin -> onResolved(actor, name, source, failed, skin));
        return true;
    }

    private void onResolved(PlayerRef actor, NpcName name, String source, MessageKey failed, Optional<NpcSkin> skin) {
        // The fetch completes on the async pool; hop back onto the global region thread before saving/rendering.
        scheduler.onGlobal(() -> {
            if (skin.isPresent()) {
                setSkin.setSkin(actor, name, skin.get());
            } else {
                notifier.send(actor, failed, Map.of("player", source));
            }
        });
    }
}
