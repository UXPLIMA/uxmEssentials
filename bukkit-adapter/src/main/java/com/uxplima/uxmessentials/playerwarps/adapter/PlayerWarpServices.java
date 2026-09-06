package com.uxplima.uxmessentials.playerwarps.adapter;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpBrowseMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpCategoriesMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListMenu;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.BuySponsorship;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.FavouritePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.ManageBans;
import com.uxplima.uxmessentials.playerwarps.application.ManageMembers;
import com.uxplima.uxmessentials.playerwarps.application.ManageWhitelist;
import com.uxplima.uxmessentials.playerwarps.application.RatePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.TransferPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WithdrawEarnings;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed player-warps use cases the Brigadier commands share, built once per module start by
 * {@code PlayerwarpsWiring} from the kernel ports, the cached jOOQ repository, and the teleport-delegating
 * teleporter. Held so every command reads the same use cases; the player-warps context keeps no other
 * adapter-side runtime state, so there is nothing here to drain on stop beyond dropping this holder.
 *
 * @param setPlayerWarp {@code /setpwarp}
 * @param archivePlayerWarp {@code /pwarp del} (archive by default; the admin hard-delete path lives here too)
 * @param usePlayerWarp {@code /pwarp <name> [owner]}
 * @param listPlayerWarps {@code /pwarps [player]}
 * @param visibility {@code /pwarp public|private <name>}
 * @param players name → ref resolution for the {@code [owner]} / {@code [player]} cross-owner forms
 * @param repository the warp store, held only so the name-argument suggesters can peek an owner's warps
 *     without blocking (a join-warmed cache hit completes the names; a cold miss suggests nothing)
 * @param editorView the per-warp settings editor GUI reused from the warps module (opened by {@code /pwarp edit})
 * @param scheduler the kernel scheduler the commands run their repository reads through off the tick thread,
 *     bridging any Bukkit feedback back to the player's region thread (the homes async-read pattern)
 * @param listView the management-GUI list (the owner-scoped {@code /pwarp} edit panel, opened from the admin hub and
 *     the editor's back button), owner-scoped for a player and cross-owner for a holder of {@code uxmessentials.pwarp.gui}
 * @param browseView the paged public browse the landing's quick entries and category buttons open with a preset filter
 * @param categoriesView the {@code pwarp-categories} landing opened by {@code /pwarp} with no arguments, a hub of quick
 *     browse entries (all / mine / favourites / top rated) and one button per defined category
 * @param ratePlayerWarp {@code /pwarp rate <name> <1-5>}. The any-viewer star rating that drives the browse sort
 * @param favouritePlayerWarp {@code /pwarp favourite <name>}, the any-viewer favourite toggle
 * @param manageMembers {@code /pwarp members add|remove}, grant/revoke a co-owner or manager (owner-only)
 * @param manageWhitelist {@code /pwarp whitelist add|remove}. The guest-list verbs (owner/co-owner/manager)
 * @param manageBans {@code /pwarp ban|unban}, bar or restore a player (owner/co-owner/manager)
 * @param withdrawEarnings {@code /pwarp withdraw}, pay the warp bank out to the owner (owner/co-owner)
 * @param editPlayerWarp {@code /pwarp rename|displayname|description|icon|category|access|password|price|move}
 *     the single-warp edit verbs, each gated on its own {@code WarpCapability} inside the use case
 * @param transferPlayerWarp {@code /pwarp transfer <name> <player>}, hand a warp to a new owner (owner-only)
 * @param buySponsorship {@code /pwarp sponsor <name> [days]}. Buy a paid pinned browse slot (owner-only)
 * @param sponsorConfig the sponsor sub-group tunables; the command reads its {@code enabled} flag to gate the
 *     {@code sponsor} subcommand's registration and its {@code duration-days} as the default term
 */
@NullMarked
public record PlayerWarpServices(
        SetPlayerWarp setPlayerWarp,
        ArchivePlayerWarp archivePlayerWarp,
        UsePlayerWarp usePlayerWarp,
        ListPlayerWarps listPlayerWarps,
        SetPlayerWarpVisibility visibility,
        PlayerLookup players,
        PlayerWarpRepository repository,
        @org.jspecify.annotations.Nullable WarpEditorView editorView,
        Scheduler scheduler,
        PlayerWarpListMenu listView,
        PlayerWarpBrowseMenu browseView,
        PlayerWarpCategoriesMenu categoriesView,
        RatePlayerWarp ratePlayerWarp,
        FavouritePlayerWarp favouritePlayerWarp,
        ManageMembers manageMembers,
        ManageWhitelist manageWhitelist,
        ManageBans manageBans,
        WithdrawEarnings withdrawEarnings,
        EditPlayerWarp editPlayerWarp,
        TransferPlayerWarp transferPlayerWarp,
        BuySponsorship buySponsorship,
        SponsorConfig sponsorConfig) {

    public PlayerWarpServices {
        Objects.requireNonNull(setPlayerWarp, "setPlayerWarp");
        Objects.requireNonNull(archivePlayerWarp, "archivePlayerWarp");
        Objects.requireNonNull(usePlayerWarp, "usePlayerWarp");
        Objects.requireNonNull(listPlayerWarps, "listPlayerWarps");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(listView, "listView");
        Objects.requireNonNull(browseView, "browseView");
        Objects.requireNonNull(categoriesView, "categoriesView");
        Objects.requireNonNull(ratePlayerWarp, "ratePlayerWarp");
        Objects.requireNonNull(favouritePlayerWarp, "favouritePlayerWarp");
        Objects.requireNonNull(manageMembers, "manageMembers");
        Objects.requireNonNull(manageWhitelist, "manageWhitelist");
        Objects.requireNonNull(manageBans, "manageBans");
        Objects.requireNonNull(withdrawEarnings, "withdrawEarnings");
        Objects.requireNonNull(editPlayerWarp, "editPlayerWarp");
        Objects.requireNonNull(transferPlayerWarp, "transferPlayerWarp");
        Objects.requireNonNull(buySponsorship, "buySponsorship");
        Objects.requireNonNull(sponsorConfig, "sponsorConfig");
    }

    /**
     * The names of the warps {@code owner} owns if they are already cached, for the name-argument suggesters.
     * Reads only the non-blocking repository peek, so a cold cache (no join-warm yet) yields an empty list and
     * the suggester offers nothing rather than reaching the disk on the tick thread.
     */
    public List<String> ownWarpNames(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return repository.peekOwned(owner).orElseGet(List::of).stream()
                .map(PlayerWarp::name)
                .map(name -> name.value())
                .toList();
    }
}
