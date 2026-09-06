package com.uxplima.uxmessentials.bootstrap.di;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.commandcontrol.application.CommandControlModule;
import com.uxplima.uxmessentials.communication.application.CommunicationModule;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsModule;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusModule;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkModule;
import com.uxplima.uxmessentials.economy.application.EconomyModule;
import com.uxplima.uxmessentials.holograms.application.HologramsModule;
import com.uxplima.uxmessentials.homes.application.HomesModule;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackModule;
import com.uxplima.uxmessentials.itemworld.application.ItemworldModule;
import com.uxplima.uxmessentials.kits.application.KitsModule;
import com.uxplima.uxmessentials.messaging.application.MessagingModule;
import com.uxplima.uxmessentials.moderation.application.ModerationModule;
import com.uxplima.uxmessentials.nametags.application.NametagsModule;
import com.uxplima.uxmessentials.npc.application.NpcModule;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateModule;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsModule;
import com.uxplima.uxmessentials.poses.application.PoseModule;
import com.uxplima.uxmessentials.presence.application.PresenceModule;
import com.uxplima.uxmessentials.ranks.application.RanksModule;
import com.uxplima.uxmessentials.regions.application.RegionsModule;
import com.uxplima.uxmessentials.scoreboard.application.ScoreboardModule;
import com.uxplima.uxmessentials.security.application.SecurityModule;
import com.uxplima.uxmessentials.servertweaks.application.ServerTweaksModule;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.skin.application.SkinModule;
import com.uxplima.uxmessentials.staff.application.StaffModule;
import com.uxplima.uxmessentials.survival.application.SurvivalModule;
import com.uxplima.uxmessentials.tablist.application.TablistModule;
import com.uxplima.uxmessentials.teleport.application.TeleportModule;
import com.uxplima.uxmessentials.trade.application.TradeModule;
import com.uxplima.uxmessentials.vanish.application.VanishModule;
import com.uxplima.uxmessentials.vaults.application.VaultsModule;
import com.uxplima.uxmessentials.villagers.application.VillagersModule;
import com.uxplima.uxmessentials.vote.application.VoteModule;
import com.uxplima.uxmessentials.warps.application.WarpsModule;
import com.uxplima.uxmessentials.worlds.application.WorldsModule;
import org.jspecify.annotations.NullMarked;

/**
 * The single registration site for every {@link FeatureModule}.
 *
 * <p>Registration is explicit and ordered dependency-first (prerequisites before dependents):
 * {@code economy} before {@code warps} and {@code kits} because a warp or kit may charge a cost through
 * the economy provider, {@code teleport} before {@code homes} and {@code warps} because they delegate
 * teleport execution. Each context adds exactly one {@code register(...)} line at its dependency-correct
 * position when it lands. The registry stays the greppable, authoritative answer to "which contexts
 * exist?".
 */
@NullMarked
public final class DefaultModuleRegistry implements ModuleRegistry {

    private final ModuleRegistry delegate;

    public DefaultModuleRegistry() {
        this.delegate = new ListModuleRegistry();
        // Feature modules register here in dependency-first order as each context lands. teleport owns
        // all movement orchestration, so it lands before the homes/warps contexts that delegate here.
        // economy registers before warps because a warp may charge a per-warp cost through the economy
        // provider; the economy WarpEconomy bridge is captured during economy wiring and handed to warps.
        delegate.register(new TeleportModule());
        delegate.register(new WorldsModule()); // worlds delegates teleport execution (sub-project D)
        delegate.register(new HomesModule());
        delegate.register(new EconomyModule());
        delegate.register(new WarpsModule());
        // kits registers after economy because a kit may charge a per-kit cost through the economy provider;
        // the economy KitEconomy bridge is captured during economy wiring and handed to kits.
        delegate.register(new KitsModule());
        // playerstate is self-contained (transient in-memory snapshots, no DB and no cross-context bridge)
        // so its position is not dependency-constrained; it lands after kits.
        delegate.register(new PlayerstateModule());
        // vanish owns the single vanish state every other context reads (messaging's /msg resolution, the nametag
        // viewer cull, staff-mode vanish). It carries no hard dependency edge. Those couplings are soft and degrade
        // to "no one is hidden" when vanish is off, but it lands before the contexts it informs so its store handle
        // is captured and threaded into their vanish gates during wiring (the same capture-early pattern teleport
        // uses).
        delegate.register(new VanishModule());
        // messaging soft-couples to moderation (mute-gated sending) and vanish (vanish-aware /msg
        // resolution); both gates degrade gracefully when the other module is off, so messaging carries no
        // hard dependency edge and lands here independently of either.
        delegate.register(new MessagingModule());
        // presence owns AFK and the transient per-player presence map; the vanished flag it reports is mirrored
        // from the vanish context's authority rather than owned here. Its readers (the placeholders, the sleep
        // exclusion) all degrade gracefully without it, so presence carries no hard dependency edge and lands
        // after the contexts it informs.
        delegate.register(new PresenceModule());
        // moderation provides the real MutePolicy (messaging) and JailGate (teleport) the placeholder NEVER
        // bindings stand in for until it lands; both couplings are soft, so moderation carries no hard
        // dependency edge and lands after the contexts it informs (messaging + teleport were wired first).
        delegate.register(new ModerationModule());
        // itemworld is stateless and ACL-thin (no DB, no persistence) and carries no hard dependency edge, its
        // verbs mutate the live item/entity/world directly, so it lands after the contexts wired above, ahead
        // of vaults, matching the dependency-first ordering documented in docs/10-feature-modules.md §3.
        delegate.register(new ItemworldModule());
        // vaults is DB-persisted player item storage (the 12th and final feature context). It carries no hard
        // dependency edge (no cross-context bridge; its only collaborators are the shared persistence DSL and the
        // Permissions reducer), so it lands last, after itemworld, completing the twelve-context set.
        delegate.register(new VaultsModule());
        // communication is the round-3 feature context (the 13th module). The operator's broadcast surface:
        // connection-message policies, the rotating announcer, and the info pages. It carries no hard dependency
        // edge (its only collaborators are the shared Scheduler, messages, and event ports), and it ships DISABLED
        // by default, so it lands last and wires nothing until an operator enables it in modules.conf.
        delegate.register(new CommunicationModule());
        // holograms is a world-placed display feature (named, multi-line TextDisplay holograms behind
        // /hologram). It carries no hard dependency edge (its only collaborators are the shared persistence
        // DSL, the Scheduler, messages, and event ports), and like warps/vaults it ships ENABLED as a
        // steady-state feature, so it lands last after communication.
        delegate.register(new HologramsModule());
        // playerwarps is the 14th context: player-owned warps keyed (owner, name). Like warps it delegates
        // teleport execution to the teleport engine, so it must land after teleport (it does); it carries no
        // other hard dependency edge (its only collaborators are the shared persistence DSL, the Permissions
        // reducer, and the teleport engine), and like warps/holograms it ships ENABLED, so it lands last.
        delegate.register(new PlayerwarpsModule());
        // scoreboard is the 15th context: a per-player packet sidebar rendered from operator-authored MiniMessage
        // content on the Scheduler refresh timer. Like communication it carries no hard dependency edge (its only
        // collaborators are the shared Scheduler, messages, and event ports) and ships with a working default board.
        delegate.register(new ScoreboardModule());
        // tablist is the 16th context, the per-player tab-list header/footer split out of scoreboard so the sidebar
        // and the tablist enable, author, and refresh independently. It owns its own Scheduler refresh timer over
        // uxmlib-hud's Tablist, carries no hard dependency edge, and like scoreboard ships DISABLED by default (its
        // content is operator data), so it wires nothing until enabled in modules.conf. It lands next to scoreboard.
        delegate.register(new TablistModule());
        // vote is the 17th context: a Votifier-bridged vote-rewards and vote-party feature. It carries no hard
        // dependency edge (its only collaborators are the shared persistence DSL, the Scheduler, messages, and
        // event ports, plus a console-dispatch port), and like the steady-state features it ships ENABLED but
        // inert until rewards/links are authored, so it lands last after tablist.
        delegate.register(new VoteModule());
        // discordlink is the 18th context: Discord account linking (/discordlink in game, /link in the bridge).
        // It carries no hard dependency edge (its only collaborators are the shared persistence DSL, messages,
        // and the player lookup), and the host exposes its ConfirmLink use case through the ServicesManager so the
        // optional Discord jar can redeem a code with no compile-time link. Like the steady-state features it ships
        // ENABLED, so it lands last after vote.
        delegate.register(new DiscordlinkModule());
        // nametags is the 19th context. A per-wearer above-head TextDisplay nametag rendered on the Scheduler refresh
        // timer over uxmLib's packet nametag stack. It soft-couples to vanish (vanish-aware viewer culling through
        // the canSee graph, degrading to "everyone can see everyone" when vanish is off), so it carries no hard
        // dependency edge. It ships ENABLED by default with a single plain-name format and hides the vanilla above-head
        // name under it through a shared scoreboard-team coordinator (re-applied after every per-player board switch),
        // so the default surface is one clean custom nametag per wearer. It lands last after discordlink.
        delegate.register(new NametagsModule());
        // staff is the 20th context, a STAFF-MODE-ONLY toolkit (the /staffmode toggle with an item-loss-safe DB-backed
        // loadout swap, the VANISH + EXAMINE gadget hotbar, and staff chat). The gadgets orchestrate the existing
        // presence/playerstate modules through soft-couple ports that degrade to no-ops when those modules are off, and
        // staff chat fans out through messaging's staff audience, so staff carries no hard dependency edge. It lands
        // last after nametags. Every command and gadget is permission-gated, so a regular player sees nothing change.
        delegate.register(new StaffModule());
        // npc is the 21st context, server-wide fake-player NPCs (the /npc create/delete/list/move/skin/command
        // toolkit). Each NPC is rendered to viewers entirely with packets (no real entity) over the uxmLib NPC
        // packet stack; it is DB-persisted (the npc table is in the persistence V38 baseline) so NPCs come back
        // after a restart. It carries no hard dependency edge (its only collaborators are the shared persistence
        // DSL, the Scheduler, messages, and event ports), and like the steady-state features it ships ENABLED but
        // inert until an operator creates an NPC, so it lands last after staff.
        delegate.register(new NpcModule());
        // custommenus is the 22nd context, the operator surface over the Phase-2 menu engine (/menu open/list/reload
        // over operator-authored menus/*.conf). It consumes the always-on engine bindings rather than owning a domain
        // aggregate, carries no hard dependency edge (its only collaborators are the shared menu façade + bindings,
        // built in bootstrap), and like the steady-state features ships ENABLED but inert until an operator authors a
        // menu, so it lands last after npc.
        delegate.register(new CustomMenusModule());
        // customcommands is a new bounded context: operator-declared Brigadier commands (commands/custom/*.conf) that
        // run the same action and requirement vocabulary the menu engine already speaks. Like custommenus it consumes
        // the engine bindings rather than owning an aggregate, carries no hard dependency edge, and ships ENABLED but
        // inert until an operator writes a definition file, so it lands right after custommenus.
        delegate.register(new CustomCommandsModule());
        // poses is the 23rd context. Built-in GSit-parity sitting and posing (/sit, player-sit, /lay, /bellyflop,
        // /spin, /crawl). It carries no hard dependency edge (its collaborators are the shared Scheduler, messages,
        // permissions, and event ports, plus the claim/region gate it consults through a soft-couple in a later
        // phase), and like the steady-state features it ships ENABLED, so it lands last after custommenus.
        delegate.register(new PoseModule());
        // survival is the 24th context. Opt-in gameplay mechanics (Phase 1: tree-feller + veinminer), each an
        // independently toggleable sub-feature. It carries no hard dependency edge (its collaborators are the shared
        // Scheduler, messages, and event ports), persists nothing (per-player toggles are PDC stamps), and like the
        // steady-state features ships ENABLED, so it lands last after poses.
        delegate.register(new SurvivalModule());
        // ranks is the 25th context. Rankup/prestige/autorank progression, tracking each player's current rank
        // with a DB-backed pointer of our own rather than reading a permission plugin's group. It carries no hard
        // dependency edge in Phase 1 (its collaborators are the shared persistence DSL for the pointer plus the
        // Scheduler, messages and event ports; the economy/permissions/playtime seams it consults on rankup are
        // soft-coupled and land with the later phases), and like the steady-state features it ships ENABLED but
        // inert until an operator authors a ladder, so it lands last after survival.
        delegate.register(new RanksModule());
        // security is a new bounded context. Account-security features (NOT a login system; on an offline-mode
        // server it waits for the login plugin rather than replacing it).
        // Phase 1 is the two-factor enrolment surface (/2fa and /pin) over a DB-backed store with the PIN hashed and
        // the TOTP secret AES-encrypted at rest. It carries no hard dependency edge in Phase 1 (its collaborator is the
        // shared persistence DSL plus the Scheduler, messages and event ports; the join-verification freeze,
        // op-command protection and IP/alt guard land with the later phases), and like the steady-state features it
        // ships ENABLED but inert until a player enrols a factor. It is registered before trade so trade stays last.
        delegate.register(new SecurityModule());
        // commandcontrol is a new bounded context. PlHidePro / CommandWhitelist-parity gating of which commands a
        // player may run. Phase 1 is the per-group command whitelist/blacklist and the custom "unknown command" deny
        // message (a single PlayerCommandPreprocessEvent gate); the tab-completion filter, plugin-hide, and
        // namespace-bypass block land in the later phases. It carries no hard dependency edge (its collaborators are
        // the shared messages/sink ports plus the LuckPerms group seam), persists nothing (the rule set is derived
        // from config), and like the steady-state features ships ENABLED but inert until an operator names commands.
        // It is registered before trade so trade stays last.
        delegate.register(new CommandControlModule());
        // trade is the 26th context. Secure player-to-player trading (/trade) with a live dual-inventory window and,
        // in the later phases, staked money and cross-server escrow. It carries no hard dependency edge in Phase 1
        // (its collaborators, the economy trade port and the bus transport, land with the money and cross-server
        // phases), a same-server trade is transient in-memory state (no DB), and like the steady-state features it
        // ships ENABLED, so it lands last after ranks.
        delegate.register(new TradeModule());
        // villagers is a new bounded context. Villager trade management (Phase 1: infinite trading, a restock timer,
        // instant restock, and a global/per-villager trade toggle), each an independently toggleable sub-feature. It
        // carries no hard dependency edge (its collaborators are the shared Scheduler, messages, and event ports),
        // persists nothing (the last-restock stamp and disable flag are PDC state on the villager entity), and like the
        // steady-state features ships ENABLED but inert until an operator turns a feature on, so it lands last.
        delegate.register(new VillagersModule());
        // invrollback is a new bounded context. AxInventoryRestore-parity inventory snapshots captured on death
        // and logout, DB-backed so a lost inventory survives a world rollback and staff can restore it from a GUI
        // (Phase 2). It carries no hard dependency edge (its collaborators are the shared persistence DSL, the
        // Scheduler, messages, and event ports), and like the steady-state features it ships ENABLED but inert
        // until a capture happens, so it lands last after villagers.
        delegate.register(new InvrollbackModule());
        // regions is a new bounded context: a GUI to manage WorldGuard regions (Phase 1 lists them). WorldGuard is a
        // SOFT dependency: the adapter wiring probes for it and, when it is absent, binds a no-op RegionService so the
        // module is inert (the /regions command reports "WorldGuard not installed"). It carries no hard dependency edge
        // (its only collaborators are the shared Scheduler, messages and the menu engine; WorldGuard is an external
        // soft-dep, not a module), persists nothing (WorldGuard owns the region store), and like the steady-state
        // features ships ENABLED, so it lands last after invrollback.
        delegate.register(new RegionsModule());
        // servertweaks is the last bounded context, a grab-bag of small, independently-toggleable server/infra
        // tweaks (Phase 1: a custom F3 server brand and a console-spam log filter). It carries no hard dependency
        // edge (its collaborators are the shared Scheduler-free config + logger ports and Bukkit's Messenger/Log4j2
        // seams at the adapter edge), persists nothing (each tweak is a live side effect), and ships ENABLED so a
        // single tweak can be turned on in isolation, but every individual tweak defaults OFF, so a fresh install
        // wires nothing until an operator opts one in. It lands last after regions.
        delegate.register(new ServerTweaksModule());
        // skin is a new bounded context: SkinsRestorer-parity skins, so a cracked server looks right and a
        // Bedrock player arrives wearing their own face. It carries no hard dependency edge (its collaborators are
        // the shared persistence DSL, the skin-texture lookup, the Scheduler, messages and event ports; Floodgate
        // is an external soft-dep, not a module) and ships DISABLED, since an online-mode server gains nothing
        // from it, so it lands last after servertweaks.
        delegate.register(new SkinModule());

        // The shared kernel is not a module and never appears here.
    }

    @Override
    public ModuleRegistry register(FeatureModule module) {
        return delegate.register(module);
    }

    @Override
    public List<FeatureModule> all() {
        return delegate.all();
    }

    @Override
    public Optional<FeatureModule> byId(ModuleId id) {
        return delegate.byId(id);
    }

    @Override
    public List<FeatureModule> enabledModules(com.uxplima.uxmessentials.shared.application.port.ConfigStore config) {
        return delegate.enabledModules(config);
    }
}
