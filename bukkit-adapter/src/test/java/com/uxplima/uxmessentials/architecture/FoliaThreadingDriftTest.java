package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The Folia whole-world enumeration guard (CLAUDE.md §1 Folia-ready schedulers, docs/02-concurrency.md §2.6/§6.10).
 *
 * <p>On Folia there is no single main thread that owns every entity. {@code Bukkit.getOnlinePlayers()} (and the
 * equivalent {@code server.getOnlinePlayers()} / {@code plugin.getServer().getOnlinePlayers()} forms) is only
 * coherent when read on the <strong>global region thread</strong>; the same is true of a whole-world entity
 * enumeration ({@code world.getEntities()}), whose entities are each owned by their own region thread. Iterating
 * either on an arbitrary region or async thread and then mutating each entity is the exact assumption Folia breaks
 * (docs/02-concurrency.md §2.6). Phase A of the 0.3.0 work moved every off-global enumeration onto the global
 * thread (or behind a per-entity / per-region hop, or confined it to an off-tick reader). This guard freezes that
 * result: it walks the production source under {@code bukkit-adapter/src/main/java} and fails if a new class
 * enumerates the online roster or a whole world's entities without being on the allowlist below, so a regression
 * that re-introduces an unmarshalled cross-region scan cannot land silently.
 *
 * <p><strong>How it works.</strong> The scan finds every source line that calls {@code getOnlinePlayers()} (any
 * receiver) <em>or</em> references it as a method handle ({@code ::getOnlinePlayers}, so the
 * {@code Bukkit::getOnlinePlayers} / {@code getServer()::getOnlinePlayers} / {@code server::getOnlinePlayers} forms
 * that hand the roster to a {@code Supplier} are caught too) <em>or</em> calls {@code getEntities(} on any receiver
 * (the whole-world entity enumeration. {@code getNearbyEntities} is a bounded single-region read and is
 * deliberately not matched), ignoring comment lines (a leading {@code //}, {@code *}, or {@code /*}, so the many
 * javadoc mentions of these methods do not count). Every such line's declaring class must appear in
 * {@link #ALLOWLIST}. The allowlist is the expected current set; it passes today and fails the moment an
 * enumeration appears in a class that is not listed. Each entry is grouped by <em>why</em> the read is safe.
 *
 * <p><strong>Known limitation. The roster can travel through a {@code Supplier} the scan cannot follow.</strong>
 * A class can hand {@code Bukkit::getOnlinePlayers} to another object as a {@code Supplier<Collection<Player>>}; the
 * <em>creating</em> class is caught by the method-ref match above, but the <em>consuming</em> class enumerates by
 * calling {@code supplier.get()}, which carries no {@code getOnlinePlayers} text for the scan to see. The two known
 * consumers (the tablist {@code RealPlayerRowPainter} and {@code TablistSuppression}, fed from {@code TablistWiring}
 * / {@code TablistRenderer}) are listed in {@link #ALLOWLIST} and tracked in {@link #SUPPLIER_CONSUMERS}, but a
 * reviewer adding a foreign-live read inside one of them, or wiring a <em>new</em> supplier consumer, gets no
 * automatic signal. When a new {@code Supplier} of the roster is injected, the reviewer must hand-check the
 * consuming class for the enumerate-then-hop shape and list it here.
 *
 * <p><strong>The four safe shapes (and the rule for adding an entry).</strong>
 *
 * <ul>
 *   <li><strong>GLOBAL</strong>, the read runs inside a {@code scheduler.onGlobal(...)} / {@code repeatGlobal(...)}
 *       lambda, a globally-scheduled task, or a {@code FeatureModule.stop()} (Folia dispatches {@code /uxmess
 *       reload} and plugin disable on the global region thread). This is the bulk of the Phase A fixes.</li>
 *   <li><strong>COMMAND</strong>, the read runs directly in a Brigadier command handler, which Paper/Folia
 *       dispatch on the global region thread, so no extra hop is needed.</li>
 *   <li><strong>OFFTICK</strong>, a PlaceholderAPI placeholder resolver or a Brigadier suggestion provider. These
 *       run off any tick/region thread; an {@code onGlobal} hop would be wrong (it would block the resolver), and
 *       they only read the roster size or build a name list, never touching a foreign player's live state.</li>
 *   <li><strong>RENDER_SCAN</strong>. The documented self-rescheduling render/visibility pattern
 *       (docs/02-concurrency.md §2.6 line "hop via entity.getScheduler() per player", §6.10): a loop enumerates the
 *       roster on its own (async loop or region) thread and then hops to each player's entity/region thread before
 *       touching that player, or it operates on a region-anchored shared display entity. No foreign {@code Player}
 *       is mutated inline, which is what §2.6 actually forbids. Adding one here means the new class must follow that
 *       same enumerate-then-hop shape, not mutate players on the scanning thread.</li>
 * </ul>
 *
 * <p>To add a site: confirm it matches one of the four shapes above, then list its class under the matching group
 * with a short note. If a new {@code getOnlinePlayers()} fits none of them. It mutates players on a non-global
 * region/async thread. It is a Folia bug, not an allowlist entry: marshal it through {@code scheduler.onGlobal}
 * (or a per-entity hop) instead.
 */
class FoliaThreadingDriftTest {

    private static final String CALL = "getOnlinePlayers()";
    private static final String METHOD_REF = "::getOnlinePlayers";
    /** The whole-world entity enumeration. The leading dot excludes {@code getNearbyEntities(}, a bounded read. */
    private static final String ENTITIES_CALL = ".getEntities(";

    /**
     * The known-safe online-roster enumeration sites, keyed by fully-qualified class name. Grouped by why each is
     * safe (see the class javadoc for the four shapes). This is the expected current set: every production class
     * that reads the roster is here, and nothing else may.
     */
    private static final Map<String, String> ALLOWLIST = buildAllowlist();

    /**
     * Allowlisted classes that receive the online roster through an injected {@code Supplier} rather than naming
     * {@code getOnlinePlayers} themselves. They call {@code viewers.get()}, which carries no literal or method-ref
     * text the scan can see. They are listed (their consumption is hand-verified, see the supplier note in the class
     * javadoc) but exempt from the staleness check, because the scan can never observe them enumerating.
     */
    private static final Set<String> SUPPLIER_CONSUMERS = Set.of(
            "com.uxplima.uxmessentials.tablist.adapter.outbound.RealPlayerRowPainter",
            "com.uxplima.uxmessentials.tablist.adapter.outbound.TablistSuppression");

    private static Map<String, String> buildAllowlist() {
        Map<String, String> allow = new LinkedHashMap<>();
        String pkg = "com.uxplima.uxmessentials.";

        // GLOBAL, resolved on the global region thread (inside scheduler.onGlobal/onRegion/repeatGlobal, a
        // globally-scheduled task, or a FeatureModule.stop() Folia dispatches on the global region thread).
        allow.put(
                pkg + "communication.adapter.CommunicationWiring", "GLOBAL: advancement-vanish probe runs in onGlobal");
        allow.put(
                pkg + "communication.adapter.inbound.command.ClearChatCommand",
                "GLOBAL: clear-chat fan-out wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "communication.adapter.inbound.command.MeCommand",
                "GLOBAL: /me broadcast wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "communication.adapter.inbound.gui.CommunicationAdminMenu",
                "GLOBAL: admin-GUI clear-chat wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "communication.adapter.outbound.BukkitAnnouncerBroadcaster",
                "GLOBAL: announcer broadcast + count run inside scheduler.onGlobal");
        allow.put(
                pkg + "economy.adapter.outbound.PhysicalWalletRepositoryDecorator",
                "GLOBAL: baltop online-ref snapshot resolved via onGlobal");
        allow.put(pkg + "economy.adapter.outbound.SalaryTask", "GLOBAL: salary fan-out inside scheduler.onGlobal");
        allow.put(
                pkg + "itemworld.adapter.inbound.command.GiveAllCommand",
                "GLOBAL: /giveall distribution wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "itemworld.adapter.outbound.BukkitEntityPurger",
                "GLOBAL: world purge snapshots world.getEntities() inside onGlobal, then hops each removal to onRegion");
        allow.put(
                pkg + "poses.adapter.outbound.BukkitSeatPort",
                "GLOBAL: sweepOrphans snapshots world.getEntities() inside onGlobal then hops each removal to onRegion; "
                        + "the chunk/world unload cleanup reads run on the unload event's own owning region thread");
        allow.put(
                pkg + "poses.adapter.outbound.BukkitPacketPosePort",
                "GLOBAL: the lie-down pose packet fans out to the poser's world roster inside scheduler.onGlobal, "
                        + "where getOnlinePlayers() is coherent; the spin loop only iterates its own set and hops each "
                        + "seat rotation to onEntity");
        allow.put(
                pkg + "itemworld.adapter.inbound.command.ShowItemCommand",
                "GLOBAL: /showitem broadcast wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "moderation.adapter.outbound.PermissionSanctionBroadcast",
                "GLOBAL: sanction broadcast fan-out inside scheduler.onGlobal");
        allow.put(
                pkg + "security.adapter.SecurityStaffNotifier",
                "GLOBAL: alt / flagged-client staff notice fan-out inside scheduler.onGlobal");
        allow.put(
                pkg + "ranks.adapter.outbound.AutorankScan",
                "GLOBAL: autorank scan snapshots the online roster inside repeatGlobal, then hops each promotion to "
                        + "onEntity");
        allow.put(
                pkg + "playerstate.adapter.outbound.BukkitNearbyPlayers",
                "GLOBAL: /near position read resolved via onGlobal before per-entity work");
        allow.put(
                pkg + "playerstate.adapter.outbound.BukkitOnlineRoster",
                "GLOBAL: playtime sampler roster snapshot. Get() is only ever called inside the sampler's "
                        + "scheduler.onGlobal lambda, copies uuid/name only, then the writes hop off-tick");
        allow.put(
                pkg + "presence.adapter.inbound.command.GcCommand",
                "GLOBAL: /gc world entity + chunk totals read inside scheduler.onGlobal");
        allow.put(
                pkg + "presence.adapter.inbound.command.ListCommand",
                "GLOBAL: /list visible-name collection wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "presence.adapter.inbound.command.RealnameCommand",
                "GLOBAL: /realname match wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "presence.adapter.inbound.command.StaffCommand",
                "GLOBAL: /staff roster collection wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "presence.adapter.inbound.command.WhoisCommand",
                "GLOBAL: /whois match wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "presence.adapter.inbound.listener.SleepExclusionListener",
                "GLOBAL: sleep-exclusion reconcile hops to onGlobal before the roster scan");
        allow.put(
                pkg + "survival.adapter.inbound.listener.OnePlayerSleepListener",
                "GLOBAL: one-player-sleep counts the world roster inside scheduler.onGlobal before advancing the world");
        allow.put(
                pkg + "vanish.adapter.outbound.BukkitVanishView",
                "GLOBAL: vanish visibility fan-out enumerates inside scheduler.onGlobal");
        allow.put(
                pkg + "vanish.adapter.outbound.VanishConnectionMessenger",
                "GLOBAL: fake join/quit fan-out enumerates inside scheduler.onGlobal, delivering per-viewer via the sink");
        allow.put(
                pkg + "vanish.adapter.outbound.ForeignVanishStore",
                "GLOBAL: the SuperVanish/PremiumVanish reading is taken by a repeatGlobal poll armed in VanishWiring, "
                        + "and read-only: the overlay's own answers never touch the roster");
        allow.put(
                pkg + "scoreboard.adapter.ScoreboardWiring",
                "GLOBAL: module stop() tear-down enumerates inside scheduler.onGlobal");
        allow.put(
                pkg + "tablist.adapter.TablistWiring",
                "GLOBAL: module stop() tear-down enumerates inside scheduler.onGlobal");
        allow.put(
                pkg + "shared.adapter.outbound.bus.PluginMessagingTransport",
                "GLOBAL: cross-server flush is invoked via scheduler.onGlobal");
        allow.put(
                pkg + "shared.adapter.outbound.hud.ChannelBroadcaster",
                "GLOBAL: channel broadcast fan-out wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "shared.adapter.inbound.gui.PlayerPickerView",
                "GLOBAL: online-head roster snapshot taken inside scheduler.onGlobal, then opened per-entity");
        allow.put(
                pkg + "shared.adapter.inbound.gui.menu.Menus",
                "GLOBAL: engine shutdown() closes open menus inside scheduler.onGlobal");
        allow.put(
                pkg + "shared.adapter.inbound.gui.menu.vocab.LiveDataSources",
                "GLOBAL: online-players menu source snapshots the roster inside scheduler.onGlobal (guarded by "
                        + "onGlobalThread), copying name/uuid/world/ping/gamemode into immutable records served async");
        allow.put(
                pkg + "economy.adapter.inbound.gui.EconomyBulkMenu",
                "GLOBAL: give-all/reset-all roster snapshot taken inside scheduler.onGlobal, then applied async");
        allow.put(
                pkg + "staff.adapter.inbound.command.StaffListCommand",
                "GLOBAL: /stafflist staff roster snapshot taken inside scheduler.onGlobal, then opened via the engine");
        allow.put(
                pkg + "staff.adapter.inbound.listener.StaffGadgetActions",
                "GLOBAL: COMPASS navigator + EXAMINE roster snapshots taken inside scheduler.onGlobal, then opened "
                        + "via the engine");
        allow.put(
                pkg + "vote.adapter.VoteWiring", "GLOBAL: vote reminder + party effects run on repeatGlobal/onGlobal");
        allow.put(
                pkg + "vote.adapter.outbound.BukkitRewardApplier",
                "GLOBAL: vote-party broadcast wrapped in scheduler.onGlobal");
        allow.put(
                pkg + "vote.adapter.outbound.BukkitVoteAudience",
                "GLOBAL: snapshot guarded by onGlobalThread / marshalled via onGlobal");
        allow.put(
                pkg + "npc.adapter.outbound.NpcRenderer",
                "GLOBAL: render/despawn/refresh/look all on onGlobal or repeatGlobal; despawnAll from stop()");
        allow.put(
                pkg + "villagers.adapter.outbound.VillagerRestockSweep",
                "GLOBAL: restock sweep snapshots each world's villagers inside repeatGlobal, then hops each restock to "
                        + "onRegion");

        // COMMAND, runs directly in a Brigadier handler, which Paper/Folia dispatch on the global region thread.
        allow.put(
                pkg + "economy.adapter.inbound.command.EcoTargets",
                "COMMAND: /eco + /payall targets read on the Brigadier (global) thread");
        allow.put(
                pkg + "messaging.adapter.inbound.command.MailCommand",
                "COMMAND: /mail sendall recipients read on the Brigadier (global) thread");
        allow.put(
                pkg + "messaging.adapter.outbound.BukkitStaffAudience",
                "COMMAND: /helpop + /staffchat audience read on the Brigadier (global) thread");
        allow.put(
                pkg + "moderation.adapter.outbound.BukkitSanctions",
                "COMMAND: /kickall + online-players read on the Brigadier (global) thread");
        allow.put(
                pkg + "teleport.adapter.inbound.command.TpAllCommand",
                "COMMAND: /tpall roster copied on the Brigadier (global) thread");
        allow.put(
                pkg + "teleport.adapter.inbound.command.TpaAllCommand",
                "COMMAND: /tpaall roster copied on the Brigadier (global) thread");
        allow.put(
                pkg + "teleport.adapter.inbound.command.TpRandomPlayerCommand",
                "COMMAND: /tpr candidate roster read on the Brigadier (global) thread");

        // OFFTICK: PlaceholderAPI resolvers + Brigadier suggestion providers. These run off any tick/region thread;
        // an onGlobal hop would be wrong, and they only read the size or build a name list (no live foreign state).
        allow.put(
                pkg + "playerstate.adapter.inbound.command.PlayerstateCommandSupport",
                "OFFTICK: Brigadier suggestion provider (player-name type-ahead)");
        allow.put(
                pkg + "presence.adapter.inbound.command.NickCommand",
                "OFFTICK: Brigadier suggestion provider (own name + player-name type-ahead)");
        allow.put(
                pkg + "shared.adapter.inbound.command.CommandSuggestions",
                "OFFTICK: Brigadier suggestion provider (player-name type-ahead)");
        allow.put(
                pkg + "teleport.adapter.inbound.command.RtpCommand",
                "OFFTICK: Brigadier suggestion provider (/rtp <target> player-or-world type-ahead)");
        allow.put(
                pkg + "vaults.adapter.inbound.command.VaultCommand",
                "OFFTICK: Brigadier suggestion provider (player-name type-ahead)");
        allow.put(
                pkg + "shared.adapter.outbound.hud.BuiltinTokens",
                "OFFTICK: {online} token reads roster size on the placeholder thread");
        allow.put(
                pkg + "shared.adapter.outbound.papi.BukkitServerMetrics",
                "OFFTICK: PAPI server-metrics placeholder reads roster size");
        allow.put(
                pkg + "shared.adapter.outbound.papi.StaffStaffPlaceholders",
                "OFFTICK: PAPI staff-count placeholder reads roster for a permission count");

        // RENDER_SCAN. The documented enumerate-then-hop render/visibility pattern (docs/02-concurrency.md §2.6,
        // §6.10): the loop enumerates on its own (async-loop or region) thread, then hops per-entity before touching
        // a player, or operates on a region-anchored shared display entity. No foreign Player is mutated inline.
        allow.put(
                pkg + "shared.adapter.outbound.AbstractHudRenderTask",
                "RENDER_SCAN: shared HUD render loop asyncAfter enumerates, then hops per-entity to render "
                        + "(base of ScoreboardRenderTask / TablistRenderTask)");
        // Supplies Bukkit::getOnlinePlayers (a method reference, caught via METHOD_REF) to the tablist renderer and
        // suppression as the injected roster supplier; consumed on the global/region thread, reads uuid/name only.
        allow.put(
                pkg + "tablist.adapter.outbound.TablistRenderer",
                "RENDER_SCAN: passes Bukkit::getOnlinePlayers as the viewers supplier into the skin-row painter");
        // Consumes the injected roster supplier (no literal/method-ref the scanner can see: it calls viewers.get()).
        // Verified enumerate-then-hop: reads uuid/name only, hops onEntity before touching a target.
        allow.put(
                pkg + "tablist.adapter.outbound.RealPlayerRowPainter",
                "RENDER_SCAN: consumes the injected viewers supplier; broadcasts a prebuilt packet per viewer, no foreign mutation");
        allow.put(
                pkg + "tablist.adapter.outbound.TablistSuppression",
                "RENDER_SCAN: consumes the injected viewers supplier; reads only the online uuids for suppress/restore");
        allow.put(
                pkg + "nametags.adapter.outbound.NametagRenderTask",
                "RENDER_SCAN: asyncAfter reconcile loop enumerates, then hops per-entity to update");
        allow.put(
                pkg + "nametags.adapter.outbound.PacketNametagPresenter",
                "RENDER_SCAN: eligible-viewer cull read on the wearer's region thread, no foreign mutation");
        allow.put(
                pkg + "holograms.adapter.outbound.HologramViewers",
                "RENDER_SCAN: visibility scan on the hologram's region thread, show/hide on the shared entity");
        allow.put(
                pkg + "presence.adapter.outbound.BukkitPresenceAudience",
                "RENDER_SCAN: AFK audience scanned per-entity (auto-sweep) or on the /afk command thread, no inline mutation");
        allow.put(
                pkg + "communication.adapter.inbound.listener.ConnectionMessageListener",
                "RENDER_SCAN: join/quit handler reads only roster size on the player's region/event thread");
        allow.put(
                pkg + "communication.adapter.inbound.listener.DeathMessageListener",
                "RENDER_SCAN: death handler reads only roster size on the player's region/event thread");
        allow.put(
                pkg + "shared.adapter.inbound.gui.menu.vocab.SoundActions",
                "RENDER_SCAN: broadcast-sound fans a clientbound sound packet to each online player on the click "
                        + "(viewer region) thread; a sound send is client I/O, no foreign region-state mutation "
                        + "(same shape as the tablist/nametag per-viewer packet fan-outs)");

        return Map.copyOf(allow);
    }

    @Test
    void everyWholeWorldEnumerationIsOnTheFoliaAllowlist() {
        List<Enumeration> found = scan();
        List<String> violations = new ArrayList<>();
        for (Enumeration site : found) {
            if (!ALLOWLIST.containsKey(site.fqcn())) {
                violations.add(site.path() + ":" + site.line() + "  (" + site.fqcn() + ")  " + site.text());
            }
        }
        assertThat(violations)
                .as(
                        "a new whole-world enumeration (Bukkit.getOnlinePlayers() or world.getEntities()) appeared in a "
                                + "class that is not on the Folia allowlist. Read it on the global region thread "
                                + "(scheduler.onGlobal / a Brigadier handler), and for a world-entity sweep hop each "
                                + "mutation to the owning region (scheduler.onRegion), or, if it is an off-tick "
                                + "PAPI/suggestion reader or the documented enumerate-then-hop render pattern, add it to "
                                + "FoliaThreadingDriftTest.ALLOWLIST with a justifying note (see the class javadoc):\n%s",
                        String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void everyAllowlistedClassStillEnumerates() {
        // Keep the allowlist honest: an entry whose class no longer reads the roster or a world's entities is stale
        // and should be removed, so the list never drifts into a graveyard of dead exceptions.
        List<String> enumerating =
                scan().stream().map(Enumeration::fqcn).distinct().toList();
        List<String> stale = ALLOWLIST.keySet().stream()
                .filter(fqcn -> !enumerating.contains(fqcn))
                .filter(fqcn -> !SUPPLIER_CONSUMERS.contains(fqcn))
                .toList();
        assertThat(stale)
                .as(
                        "these allowlisted classes no longer call getOnlinePlayers() or .getEntities(); drop them from "
                                + "the allowlist:\n%s",
                        String.join("\n", stale))
                .isEmpty();
    }

    @Test
    void scannerSelfTest() {
        // The scanner must see real code and ignore comment lines (the many javadoc mentions of the methods).
        assertThat(isCode("        for (Player p : Bukkit.getOnlinePlayers()) {"))
                .isTrue();
        assertThat(isCode("        return server.getOnlinePlayers().size();")).isTrue();
        assertThat(isCode("        for (Entity entity : world.getEntities()) {"))
                .isTrue();
        assertThat(isCode("        entities += world.getEntities().size();")).isTrue();
        assertThat(isCode(" * iterating {@code Bukkit.getOnlinePlayers()} off it is illegal on Folia"))
                .isFalse();
        assertThat(isCode("// Bukkit.getOnlinePlayers() off it); each recipient is delivered on their thread."))
                .isFalse();
        assertThat(isCode("/* Bukkit.getOnlinePlayers() */")).isFalse();
        assertThat(isCode(" * snapshots {@code world.getEntities()} on the global thread"))
                .isFalse();
        // getNearbyEntities is a bounded single-region read and must not be matched as a whole-world enumeration.
        assertThat(isCode("        return actor.getNearbyEntities(radius, radius, radius);"))
                .isFalse();
        assertThat(isCode("        homes.get(index);")).isFalse();
    }

    private static boolean isCode(String line) {
        if (!line.contains(CALL) && !line.contains(METHOD_REF) && !line.contains(ENTITIES_CALL)) {
            return false;
        }
        String stripped = line.stripLeading();
        return !stripped.startsWith("//") && !stripped.startsWith("*") && !stripped.startsWith("/*");
    }

    private List<Enumeration> scan() {
        Path root = sourceRoot();
        List<Enumeration> sites = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> scanFile(root, path, sites));
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to walk " + root, failure);
        }
        return sites;
    }

    private void scanFile(Path root, Path path, List<Enumeration> sites) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isCode(line)) {
                sites.add(
                        new Enumeration(fqcn(root, path), root.relativize(path).toString(), i + 1, line.strip()));
            }
        }
    }

    /** The fully-qualified class name for a source file, derived from its path under the source root. */
    private static String fqcn(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
    }

    private static Path sourceRoot() {
        Path src = repoRoot().resolve("bukkit-adapter").resolve("src/main/java");
        assertThat(Files.isDirectory(src))
                .as("expected the bukkit-adapter production source root at %s", src)
                .isTrue();
        return src;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }

    /** One online-roster enumeration found in production source. */
    private record Enumeration(String fqcn, String path, int line, String text) {}
}
