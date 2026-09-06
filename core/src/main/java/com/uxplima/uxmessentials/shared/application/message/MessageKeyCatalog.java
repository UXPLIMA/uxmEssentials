package com.uxplima.uxmessentials.shared.application.message;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsMessageKey;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.ranks.application.RanksMessageKey;
import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.scoreboard.application.ScoreboardMessageKey;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.skin.application.SkinMessageKey;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.survival.application.SurvivalMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.vanish.application.VanishMessageKey;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;

/**
 * The single registry of every shipped {@link MessageKey} constant, across the {@code shared} common
 * block and every feature context (the twelve landed contexts plus the round-3 {@code communication} module).
 *
 * <p>This is the one place that enumerates the per-module key enums, so the catalog is whole regardless
 * of which modules are enabled at runtime (docs/13-i18n §6: a disabled module still ships its keys).
 * Two collaborators read it: the {@code GlobalTranslator} wiring (which registers every key for the
 * {@code translatable} path) and the locale-parity guard (which asserts this constant set equals
 * {@code messages_en.conf}'s key set, bidirectionally). Adding a context's enum here is the one edit a
 * new module makes to join the parity matrix.
 */
public final class MessageKeyCatalog {

    /** The per-module key enums, in the registry's dependency-first order; {@code shared} first. */
    private static final List<MessageKey[]> ENUMS = List.of(
            SharedMessageKey.values(),
            GuiMessageKey.values(),
            TeleportMessageKey.values(),
            WorldsMessageKey.values(),
            WorldEditorMessageKey.values(),
            HomesMessageKey.values(),
            EconomyMessageKey.values(),
            WarpsMessageKey.values(),
            KitsMessageKey.values(),
            PlayerstateMessageKey.values(),
            VanishMessageKey.values(),
            MessagingMessageKey.values(),
            PresenceMessageKey.values(),
            ModerationMessageKey.values(),
            ItemworldMessageKey.values(),
            VaultsMessageKey.values(),
            CommunicationMessageKey.values(),
            HologramsMessageKey.values(),
            PlayerwarpsMessageKey.values(),
            ScoreboardMessageKey.values(),
            VoteMessageKey.values(),
            DiscordlinkMessageKey.values(),
            StaffMessageKey.values(),
            NpcMessageKey.values(),
            CustomMenusMessageKey.values(),
            CustomCommandsMessageKey.values(),
            PosesMessageKey.values(),
            SurvivalMessageKey.values(),
            RanksMessageKey.values(),
            SecurityMessageKey.values(),
            CommandControlMessageKey.values(),
            TradeMessageKey.values(),
            VillagersMessageKey.values(),
            InvrollbackMessageKey.values(),
            RegionsMessageKey.values(),
            SkinMessageKey.values());

    private MessageKeyCatalog() {}

    /** Every shipped {@link MessageKey} constant, deduplicated by identity, in declaration order. */
    public static Set<MessageKey> all() {
        Set<MessageKey> keys = new LinkedHashSet<>();
        for (MessageKey[] block : ENUMS) {
            for (MessageKey key : block) {
                keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }

    /** Every shipped catalog lookup string ({@link MessageKey#key()}), for the parity matrix. */
    public static Set<String> allKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (MessageKey key : all()) {
            keys.add(key.key());
        }
        return Set.copyOf(keys);
    }
}
