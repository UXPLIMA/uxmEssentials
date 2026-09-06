package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcSkins;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /npc skin <name> <spec>} subcommand: {@code name:<username>} fetches any account off-thread,
 * {@code url:<image-url>} generates a signed skin off-thread through MineSkin, {@code player:<online-name>} copies
 * an online player, and {@code texture:<value>[:<signature>]} (or a bare value) sets a raw value with no fetch.
 * The async {@code name:}/{@code url:} forms dispatch their own fire-and-forget flow and report success at once;
 * the synchronous {@code player:}/{@code texture:} forms resolve here, sending feedback when the spec cannot be
 * resolved. Collected here so the root {@code /npc} command stays focused while keeping the single literal intact.
 */
@NullMarked
final class NpcSkinCommands extends NpcCommandSupport {

    private static final String PLAYER_PREFIX = "player:";
    private static final String NAME_PREFIX = "name:";
    private static final String URL_PREFIX = "url:";
    private static final String TEXTURE_PREFIX = "texture:";
    /** The keyword that clears an NPC's stored skin, falling it back to the default model. */
    private static final String NONE_KEYWORD = "@none";
    /**
     * The skin-spec prefixes suggested for {@code /npc skin}: {@code name:} fetches any account by username,
     * {@code url:} generates a signed skin from a custom image URL, {@code player:} copies an online player, and
     * {@code texture:} sets a raw base64 value (with an optional {@code :signature}).
     */
    private static final List<String> SKIN_PREFIXES =
            List.of(NAME_PREFIX, URL_PREFIX, PLAYER_PREFIX, TEXTURE_PREFIX, NONE_KEYWORD);

    private final NpcSkinByName skinByName;

    NpcSkinCommands(
            NpcServices services,
            java.util.function.Supplier<? extends java.util.Collection<String>> npcNames,
            NpcSkinByName skinByName,
            Messages messages) {
        super(services, npcNames, messages);
        this.skinByName = Objects.requireNonNull(skinByName, "skinByName");
    }

    /** The skin subcommand node the {@code /npc} literal attaches. */
    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("skin")
                .executes(ctx -> usage(ctx, "npc skin", "<name> <spec>", "Set NPC skin"))
                .then(nameArgument()
                        .executes(ctx -> usage(ctx, "npc skin", "<name> <spec>", "Set NPC skin"))
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests(this::suggestSkinPrefixes)
                                .executes(this::skin)));
    }

    private int skin(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        com.uxplima.uxmessentials.shared.domain.PlayerRef actor = actor(ctx);
        String spec = value(ctx);
        if (spec.strip().equalsIgnoreCase(NONE_KEYWORD) || spec.strip().equalsIgnoreCase("none")) {
            services.skin().clearSkin(actor, nameArg(ctx));
            return Command.SINGLE_SUCCESS;
        }
        if (spec.regionMatches(true, 0, NAME_PREFIX, 0, NAME_PREFIX.length())) {
            // The username path resolves off-thread (a Mojang round-trip), so it dispatches its own async flow
            // and reports success straight away rather than blocking the command thread on the lookup.
            skinByName.apply(
                    actor, nameArg(ctx), spec.substring(NAME_PREFIX.length()).strip());
            return Command.SINGLE_SUCCESS;
        }
        if (spec.regionMatches(true, 0, URL_PREFIX, 0, URL_PREFIX.length())) {
            // The image-URL path generates off-thread through MineSkin, the same fire-and-forget async shape.
            skinByName.applyFromUrl(
                    actor, nameArg(ctx), spec.substring(URL_PREFIX.length()).strip());
            return Command.SINGLE_SUCCESS;
        }
        NpcSkin skin = resolveSkin(sender, spec);
        if (skin == null) {
            return 0; // the unresolvable-skin feedback was already sent
        }
        services.skin().setSkin(actor, nameArg(ctx), skin);
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestSkinPrefixes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, SKIN_PREFIXES);
    }

    /**
     * Parse the synchronous skin spec: {@code player:<online-name>} copies that online player's skin (feedback
     * when they are offline or carry no skin), {@code texture:<value>[:<signature>]} (or a bare value) uses the
     * raw strings directly with no fetch. Returns {@code null} after sending feedback when the spec cannot be
     * resolved. The {@code name:}/{@code url:} async forms are dispatched by the caller before reaching here.
     */
    private @Nullable NpcSkin resolveSkin(CommandSender sender, String spec) {
        if (spec.regionMatches(true, 0, PLAYER_PREFIX, 0, PLAYER_PREFIX.length())) {
            return skinFromPlayer(sender, spec.substring(PLAYER_PREFIX.length()).strip());
        }
        String raw = spec.regionMatches(true, 0, TEXTURE_PREFIX, 0, TEXTURE_PREFIX.length())
                ? spec.substring(TEXTURE_PREFIX.length())
                : spec;
        return skinFromTexture(sender, raw.strip());
    }

    private @Nullable NpcSkin skinFromPlayer(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_PLAYER_OFFLINE, Map.of("player", targetName));
            return null;
        }
        Optional<NpcSkin> skin = BukkitNpcSkins.of(target);
        if (skin.isEmpty()) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_UNAVAILABLE, Map.of("player", targetName));
        }
        return skin.orElse(null);
    }

    /**
     * Set the skin from a raw {@code texture:<value>[:<signature>]} spec directly, no fetch. A blank value is
     * rejected with {@link NpcMessageKey#NPC_SKIN_INVALID_TEXTURE}. A value with no signature is allowed: it
     * renders on our packet NPC, but a one-line note recommends a signature, since a strict client may show an
     * unsigned skin from another account as the default Steve/Alex.
     */
    private @Nullable NpcSkin skinFromTexture(CommandSender sender, String raw) {
        int separator = raw.indexOf(':');
        String texture = separator < 0 ? raw : raw.substring(0, separator);
        String signature = separator < 0 ? null : raw.substring(separator + 1);
        if (texture.isBlank()) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_INVALID_TEXTURE, Map.of("player", raw));
            return null;
        }
        String resolvedSignature = (signature == null || signature.isBlank()) ? null : signature;
        if (resolvedSignature == null) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_UNSIGNED, Map.of());
        }
        return new NpcSkin(texture, resolvedSignature);
    }
}
