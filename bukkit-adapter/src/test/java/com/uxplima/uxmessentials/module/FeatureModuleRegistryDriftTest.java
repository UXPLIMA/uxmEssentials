package com.uxplima.uxmessentials.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Skeleton of the headline drift guard for first-class feature modules.
 *
 * <p>The shipped guard enforces a four-way set-equality (registry ⇔ context packages ⇔ {@code
 * modules.conf} switches ⇔ this document's per-context rows) and boots under MockBukkit to prove a
 * disabled module registers zero commands and zero listeners. No bounded context has shipped yet, so
 * here we (1) assert the registry contract on the real {@link DefaultModuleRegistry} and (2) prove the
 * independently-disableable property the four-way guard relies on, using stand-in modules. Each real
 * context plugs into the same property by adding itself to {@link DefaultModuleRegistry}.
 */
class FeatureModuleRegistryDriftTest {

    @Test
    void defaultRegistryExposesAnImmutableDeduplicatedSet() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();

        // teleport, homes, economy, warps, kits, playerstate, messaging, presence and moderation are the
        // landed contexts, registered dependency-first: teleport before the homes/warps contexts that delegate
        // teleport execution to it, and economy before warps and kits because each may charge a cost through
        // the economy provider. playerstate is self-contained (transient in-memory snapshots, no DB, no
        // cross-context bridge) and lands after kits. messaging soft-couples to moderation (mute) and
        // presence (vanish), both gates degrade gracefully, so it carries no hard dependency edge. presence
        // owns the vanish state messaging and teleport read through the canSee graph; that coupling is soft.
        // moderation provides the real mute/jail gates messaging and teleport hold placeholders for, a soft
        // couple too. itemworld is stateless and ACL-thin (no DB, no cross-context bridge) and lands after
        // moderation, ahead of vaults. vaults is DB-persisted player item storage (the 12th and final feature
        // context); it carries no cross-context bridge and lands last. The registry is a valid, immutable,
        // ordered set that resolves each by id and rejects a not-yet-landed context.
        assertThat(registry.byId(ModuleId.of("teleport"))).isPresent();
        assertThat(registry.byId(ModuleId.of("worlds"))).isPresent();
        assertThat(registry.byId(ModuleId.of("homes"))).isPresent();
        assertThat(registry.byId(ModuleId.of("economy"))).isPresent();
        assertThat(registry.byId(ModuleId.of("warps"))).isPresent();
        assertThat(registry.byId(ModuleId.of("kits"))).isPresent();
        assertThat(registry.byId(ModuleId.of("playerstate"))).isPresent();
        assertThat(registry.byId(ModuleId.of("vanish"))).isPresent();
        assertThat(registry.byId(ModuleId.of("messaging"))).isPresent();
        assertThat(registry.byId(ModuleId.of("presence"))).isPresent();
        assertThat(registry.byId(ModuleId.of("moderation"))).isPresent();
        assertThat(registry.byId(ModuleId.of("itemworld"))).isPresent();
        assertThat(registry.byId(ModuleId.of("vaults"))).isPresent();
        assertThat(registry.byId(ModuleId.of("communication"))).isPresent();
        assertThat(registry.byId(ModuleId.of("holograms"))).isPresent();
        assertThat(registry.byId(ModuleId.of("playerwarps"))).isPresent();
        assertThat(registry.byId(ModuleId.of("scoreboard"))).isPresent();
        assertThat(registry.byId(ModuleId.of("tablist"))).isPresent();
        assertThat(registry.byId(ModuleId.of("vote"))).isPresent();
        assertThat(registry.byId(ModuleId.of("discordlink"))).isPresent();
        assertThat(registry.byId(ModuleId.of("nametags"))).isPresent();
        assertThat(registry.byId(ModuleId.of("staff"))).isPresent();
        assertThat(registry.byId(ModuleId.of("npc"))).isPresent();
        assertThat(registry.byId(ModuleId.of("custommenus"))).isPresent();
        assertThat(registry.byId(ModuleId.of("poses"))).isPresent();
        assertThat(registry.byId(ModuleId.of("survival"))).isPresent();
        assertThat(registry.byId(ModuleId.of("ranks"))).isPresent();
        assertThat(registry.byId(ModuleId.of("security"))).isPresent();
        assertThat(registry.byId(ModuleId.of("commandcontrol"))).isPresent();
        assertThat(registry.byId(ModuleId.of("trade"))).isPresent();
        assertThat(registry.byId(ModuleId.of("villagers"))).isPresent();
        assertThat(registry.byId(ModuleId.of("invrollback"))).isPresent();
        assertThat(registry.byId(ModuleId.of("regions"))).isPresent();
        assertThat(registry.byId(ModuleId.of("servertweaks"))).isPresent();
        assertThat(registry.byId(ModuleId.of("skin"))).isPresent();
        assertThat(registry.all().stream().map(m -> m.id().value()))
                .containsExactly(
                        "teleport",
                        "worlds",
                        "homes",
                        "economy",
                        "warps",
                        "kits",
                        "playerstate",
                        "vanish",
                        "messaging",
                        "presence",
                        "moderation",
                        "itemworld",
                        "vaults",
                        "communication",
                        "holograms",
                        "playerwarps",
                        "scoreboard",
                        "tablist",
                        "vote",
                        "discordlink",
                        "nametags",
                        "staff",
                        "npc",
                        "custommenus",
                        "customcommands",
                        "poses",
                        "survival",
                        "ranks",
                        "security",
                        "commandcontrol",
                        "trade",
                        "villagers",
                        "invrollback",
                        "regions",
                        "servertweaks",
                        "skin");
        assertThatThrownBy(() -> registry.all().add(new FakeModule("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void itemworldIsIndependentlyDisableableAndPublishesItsFullVerbSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule itemworld = registry.byId(ModuleId.of("itemworld"))
                .orElseThrow(() -> new AssertionError("itemworld is not registered"));

        // Disabling exactly itemworld removes only it from the enabled set; every sibling stays on.
        ConfigStore off = new FixedConfig(Map.of("modules.itemworld.enabled", false));
        Set<String> enabled =
                registry.enabledModules(off).stream().map(m -> m.id().value()).collect(Collectors.toSet());
        assertThat(enabled).doesNotContain("itemworld");
        assertThat(enabled).contains("teleport", "economy", "moderation");

        // Enabled, itemworld contributes its full ~40-verb surface: the group-B verbs owned here and the
        // /repair /repairall /hat /more verbs playerstate deferred (§15.6), registered here, never twice.
        Set<String> literals =
                itemworld.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals)
                .contains(
                        "give",
                        "giveall",
                        "item",
                        "firework",
                        "potion",
                        "book",
                        "more",
                        "repair",
                        "repairall",
                        "hat",
                        "enchant",
                        "itemdb",
                        "recipe",
                        "showitem",
                        "unbreakable",
                        "disenchant",
                        "itemmodel",
                        "editsign",
                        "iteminfo",
                        "copyinv",
                        "endercopy", // item utils
                        "anvil",
                        "workbench",
                        "enderchest",
                        "furnace", // workstations
                        "disposal",
                        "condense",
                        "enderclear", // cleanup
                        "powertool",
                        "powertooltoggle",
                        "powertoollist", // powertool
                        "spawnmob",
                        "spawner",
                        "kill",
                        "butcher",
                        "killall",
                        "remove",
                        "entitycount",
                        "unlimited", // mob/entity
                        "time",
                        "weather",
                        "day",
                        "night",
                        "sun",
                        "rain",
                        "thunder", // time/weather
                        "lightning",
                        "fireball",
                        "kittycannon",
                        "antioch",
                        "beezooka",
                        "break",
                        "tree",
                        "bigtree",
                        "nuke"); // admin-fun
        // The full surface: 27 item-utils + 9 workstations + 3 cleanup + 3 powertool + 8 mob/entity
        // + 7 time/weather + 9 admin-fun = 66 distinct literals, no verb dropped and none registered twice.
        assertThat(itemworld.commands()).hasSize(66);
        assertThat(literals).hasSize(66);
        assertThat(itemworld.migrations()).isEmpty(); // itemworld is stateless: no persistence, no migration
    }

    @Test
    void vaultsIsTheLastDbBackedContextAndIndependentlyDisableable() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule vaults =
                registry.byId(ModuleId.of("vaults")).orElseThrow(() -> new AssertionError("vaults is not registered"));

        // Disabling exactly vaults removes only it from the enabled set; every sibling stays on.
        ConfigStore off = new FixedConfig(Map.of("modules.vaults.enabled", false));
        Set<String> enabled =
                registry.enabledModules(off).stream().map(m -> m.id().value()).collect(Collectors.toSet());
        assertThat(enabled).doesNotContain("vaults");
        assertThat(enabled).contains("teleport", "economy", "moderation", "itemworld");

        // Enabled, vaults contributes its single /vault command and owns no extra Flyway location (its table is
        // in the persistence V6 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                vaults.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("vault");
        assertThat(vaults.migrations()).isEmpty();
    }

    @Test
    void communicationShipsEnabledAndPublishesItsStaticSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule communication = registry.byId(ModuleId.of("communication"))
                .orElseThrow(() -> new AssertionError("communication is not registered"));

        // communication now ships ENABLED, but inert: every content file defaults to the vanilla behaviour, so being
        // on changes nothing until an operator authors a template. With no modules.conf override it is on, and
        // disabling exactly it removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("communication", "teleport", "economy", "moderation", "itemworld", "vaults");

        // Explicitly disabling exactly communication removes only it; the rest are unchanged.
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.communication.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("communication");
        assertThat(off).contains("teleport", "vaults");

        // Its static surface is the plugin's own /broadcast, /broadcastworld, /me, /broadcasttoggle, /clearchat,
        // and /togglechat; the operator-configured info-page commands (/rules, /motd, …) are dynamic and not part
        // of this fixed table. It persists nothing.
        Set<String> literals =
                communication.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals)
                .containsExactlyInAnyOrder(
                        "broadcast", "broadcastworld", "me", "broadcasttoggle", "clearchat", "togglechat");
        assertThat(communication.migrations()).isEmpty();
    }

    @Test
    void hologramsIsTheLastModuleShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule holograms = registry.byId(ModuleId.of("holograms"))
                .orElseThrow(() -> new AssertionError("holograms is not registered"));

        // holograms is the round-4 world-placed display context, registered after the prior modules; the later
        // playerwarps context now lands last, so holograms must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("holograms"))).isPresent();

        // It ships ENABLED (a steady-state world-placed feature like warps/vaults): with no modules.conf override
        // it is on, and disabling exactly holograms removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("holograms", "teleport", "vaults");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.holograms.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("holograms");
        assertThat(off).contains("teleport", "economy", "vaults");

        // Enabled, holograms contributes its single /hologram command and owns no extra Flyway location (its
        // tables are in the persistence V13 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                holograms.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("hologram");
        assertThat(holograms.migrations()).isEmpty();
    }

    @Test
    void playerwarpsShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule playerwarps = registry.byId(ModuleId.of("playerwarps"))
                .orElseThrow(() -> new AssertionError("playerwarps is not registered"));

        // playerwarps is the 14th context. Player-owned warps keyed (owner, name), delegating teleport
        // execution like warps. The later scoreboard context now lands last, so playerwarps must merely be
        // registered, not last.
        assertThat(registry.byId(ModuleId.of("playerwarps"))).isPresent();

        // It ships ENABLED (a steady-state feature like warps/vaults/holograms): with no modules.conf override it
        // is on, and disabling exactly playerwarps removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("playerwarps", "teleport", "warps", "holograms");
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.playerwarps.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("playerwarps");
        assertThat(off).contains("teleport", "warps", "holograms");

        // Enabled, playerwarps contributes exactly /pwarp /setpwarp /pwarps (warp removal folded into
        // /pwarp del, the way /warp del sits under /warp) and owns no extra Flyway location (its player_warps
        // table is in the persistence V14 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                playerwarps.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("pwarp", "setpwarp", "pwarps");
        assertThat(playerwarps.migrations()).isEmpty();
    }

    @Test
    void scoreboardShipsDisabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule scoreboard = registry.byId(ModuleId.of("scoreboard"))
                .orElseThrow(() -> new AssertionError("scoreboard is not registered"));

        // scoreboard is the 15th context, a per-player sidebar on uxmlib-hud (the tablist header/footer is its own
        // context now). The later tablist/vote contexts land after it, so scoreboard must merely be registered, not
        // last.
        assertThat(registry.byId(ModuleId.of("scoreboard"))).isPresent();

        // It ships DISABLED: a sidebar rewrites what every player sees and fights whatever HUD plugin a server
        // already runs, so a fresh install stays quiet and an operator opts in. With no override it is absent from
        // the enabled set; an explicit switch turns it on and leaves every sibling untouched.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("scoreboard");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.scoreboard.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("scoreboard", "teleport", "holograms");

        // Enabled, scoreboard contributes its single /scoreboard command and persists nothing (the display content
        // is config-authored and the per-player visibility bit is PDC-backed), so it declares no MigrationSet.
        Set<String> literals =
                scoreboard.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("scoreboard");
        assertThat(scoreboard.migrations()).isEmpty();
    }

    @Test
    void tablistShipsDisabledAndPublishesNoCommandSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule tablist = registry.byId(ModuleId.of("tablist"))
                .orElseThrow(() -> new AssertionError("tablist is not registered"));

        // tablist is the 16th context, the per-player tab-list header/footer split out of scoreboard so the two
        // enable, author, and refresh independently. It is registered next to scoreboard.
        assertThat(registry.byId(ModuleId.of("tablist"))).isPresent();

        // Like scoreboard it ships DISABLED (a rewritten header/footer is a visual decision an operator makes), and
        // crucially the two toggle independently: turning exactly tablist on leaves scoreboard's gate untouched.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("tablist", "scoreboard");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.tablist.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("tablist", "teleport", "holograms");
        assertThat(on).doesNotContain("scoreboard");

        // The tablist is always-on for every viewer when enabled, there is no per-player visibility toggle, so it
        // publishes no command, and it persists nothing (the header/footer is config-authored), so it declares no
        // MigrationSet.
        assertThat(tablist.commands()).isEmpty();
        assertThat(tablist.migrations()).isEmpty();
    }

    @Test
    void voteShipsDisabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule vote =
                registry.byId(ModuleId.of("vote")).orElseThrow(() -> new AssertionError("vote is not registered"));

        // vote is the 17th context: a Votifier-bridged vote-rewards and vote-party feature. The later
        // discordlink context now lands last, so vote must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("vote"))).isPresent();

        // It ships DISABLED: nothing here can happen without Votifier and a vote site listing the server, so a fresh
        // install carries the feature without offering /vote. Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("vote");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.vote.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("vote", "teleport", "economy", "playerwarps");

        // Enabled, vote contributes exactly /vote and /voteparty (the /vote testreward action is a subcommand,
        // not a literal) and owns no extra Flyway location (its vote_party and vote_queue tables are in the
        // persistence V15 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                vote.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("vote", "voteparty");
        assertThat(vote.migrations()).isEmpty();
    }

    @Test
    void discordlinkShipsDisabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule discordlink = registry.byId(ModuleId.of("discordlink"))
                .orElseThrow(() -> new AssertionError("discordlink is not registered"));

        // discordlink is the 18th context, Discord account linking, registered after the seventeen prior modules.
        // The later nametags context now lands last, so discordlink must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("discordlink"))).isPresent();

        // It ships DISABLED: linking accounts needs a Discord bot token, so the feature does nothing on an install
        // that has not configured one. Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("discordlink");
        assertThat(defaults).contains("teleport", "economy");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.discordlink.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("discordlink", "teleport", "economy");
        assertThat(on).doesNotContain("vote");

        // Enabled, discordlink contributes exactly /discordlink and /discordunlink (/discordlink status is a
        // subcommand, not a literal) and owns no extra Flyway location (its discord_links and
        // discord_link_pending tables are in the persistence V16 baseline, always applied), so it declares no
        // MigrationSet of its own.
        Set<String> literals =
                discordlink.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("discordlink", "discordunlink");
        assertThat(discordlink.migrations()).isEmpty();
    }

    @Test
    void nametagsShipsDisabledAndPublishesNoCommandSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule nametags = registry.byId(ModuleId.of("nametags"))
                .orElseThrow(() -> new AssertionError("nametags is not registered"));

        // nametags is the 19th context, a per-wearer above-head TextDisplay nametag on the Scheduler refresh timer.
        // The later staff context now lands last, so nametags must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("nametags"))).isPresent();

        // It ships DISABLED, with the rest of the HUD trio: a replacement above-head nametag hides the vanilla one and
        // spawns a display entity per wearer, which is a look an operator chooses rather than inherits. Turning
        // exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("nametags");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.nametags.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("nametags", "teleport", "holograms");

        // The nametag is always-on for every wearer when enabled, there is no per-player visibility toggle, so it
        // publishes no command, and it persists nothing (the formats are config-authored), so it declares no
        // MigrationSet.
        assertThat(nametags.commands()).isEmpty();
        assertThat(nametags.migrations()).isEmpty();
    }

    @Test
    void staffShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule staff =
                registry.byId(ModuleId.of("staff")).orElseThrow(() -> new AssertionError("staff is not registered"));

        // staff is the 20th context, a STAFF-MODE-ONLY toolkit (the /staffmode toggle + gadget hotbar + staff chat).
        // The later npc context now lands last, so staff must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("staff"))).isPresent();

        // It ships ENABLED with a default gadget hotbar; every command and gadget is permission-gated, so a regular
        // player sees nothing change. With no modules.conf override it is on, and disabling exactly it removes only it
        // while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("staff", "teleport", "economy", "holograms", "playerwarps");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.staff.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("staff");
        assertThat(off).contains("teleport", "holograms");

        // Enabled, staff contributes /staffmode, /staffchat (the /sc alias is not a separate literal) and
        // /stafflist (the online-staff GUI), and owns no extra Flyway location (its staff_loadout table is in the
        // persistence V29 baseline, always applied), so it declares no MigrationSet of its own. STAFF-MODE ONLY:
        // no sanction command is published.
        Set<String> literals =
                staff.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("staffmode", "staffchat", "stafflist");
        assertThat(staff.migrations()).isEmpty();
    }

    @Test
    void npcShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule npc =
                registry.byId(ModuleId.of("npc")).orElseThrow(() -> new AssertionError("npc is not registered"));

        // npc is the 21st context, server-wide fake-player NPCs behind /npc. The later custommenus context now lands
        // last, so npc must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("npc"))).isPresent();

        // It ships ENABLED but inert (a steady-state feature like holograms. Nothing renders until an operator
        // creates an NPC): with no modules.conf override it is on, and disabling exactly npc removes only it while
        // every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("npc", "teleport", "economy", "holograms", "staff");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.npc.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("npc");
        assertThat(off).contains("teleport", "holograms", "staff");

        // Enabled, npc contributes its single /npc command and owns no extra Flyway location (its npc table is in
        // the persistence V38 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals = npc.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("npc");
        assertThat(npc.migrations()).isEmpty();
    }

    @Test
    void customMenusShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule custommenus = registry.byId(ModuleId.of("custommenus"))
                .orElseThrow(() -> new AssertionError("custommenus is not registered"));

        // custommenus is the 22nd context, the operator surface over the menu engine (/menu). The later poses context
        // now lands last, so custommenus must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("custommenus"))).isPresent();

        // It ships ENABLED but inert (a steady-state feature. Nothing opens until a player runs /menu open): with no
        // modules.conf override it is on, and disabling exactly custommenus removes only it while every sibling stays
        // on. Crucially a disabled custommenus contributes no command and loads no menus: the four-way guard's
        // "disabled means absent" property is exactly what makes the /menu surface vanish when it is off.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("custommenus", "teleport", "economy", "holograms", "npc");
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.custommenus.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("custommenus");
        assertThat(off).contains("teleport", "holograms", "npc");

        // Enabled, custommenus contributes its single /menu command (the real Brigadier node is built in the adapter
        // wiring over the menu engine; this is its catalog/drift descriptor) and persists nothing (its menus are
        // operator-authored .conf files), so it declares no MigrationSet of its own.
        Set<String> literals =
                custommenus.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("menu");
        assertThat(custommenus.migrations()).isEmpty();
    }

    @Test
    void posesShipsEnabledAndPublishesNoCommandSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule poses =
                registry.byId(ModuleId.of("poses")).orElseThrow(() -> new AssertionError("poses is not registered"));

        // poses is the 23rd context: built-in GSit-parity sitting and posing. The later survival context now lands
        // last, so poses must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("poses"))).isPresent();

        // It ships ENABLED (a steady-state feature. The common poses are on out of the box, player-sit is opt-in):
        // with no modules.conf override it is on, and disabling exactly poses removes only it while every sibling
        // stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("poses", "teleport", "economy", "holograms", "custommenus");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.poses.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("poses");
        assertThat(off).contains("teleport", "holograms", "custommenus");

        // The pose verbs (/sit, /lay, /bellyflop, /spin, /crawl) and their listeners are contributed through the
        // adapter wiring rather than the declarative lists, so the module publishes no command here, and it persists
        // nothing (a pose is transient in-memory state held in PoseSessions), so it declares no MigrationSet.
        assertThat(poses.commands()).isEmpty();
        assertThat(poses.migrations()).isEmpty();
    }

    @Test
    void survivalShipsDisabledAndPublishesNoDeclarativeCommandSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule survival = registry.byId(ModuleId.of("survival"))
                .orElseThrow(() -> new AssertionError("survival is not registered"));

        // survival is the 24th context: opt-in gameplay mechanics (Phase 1: tree-feller + veinminer). The later
        // ranks context now lands last, so survival must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("survival"))).isPresent();

        // It ships DISABLED: tree-feller and veinminer change how vanilla blocks break, which is a gameplay decision
        // an operator makes rather than something an essentials install imposes. Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("survival");
        assertThat(defaults).contains("teleport", "economy", "holograms", "poses");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.survival.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("survival", "teleport", "holograms", "poses");

        // The /treefeller and /veinminer toggle commands and the BlockBreakEvent listeners are contributed through
        // the adapter wiring (gated per mechanic), not the declarative lists, so the module publishes no command
        // here, and it persists nothing (the per-player toggles are PDC stamps), so it declares no MigrationSet.
        assertThat(survival.commands()).isEmpty();
        assertThat(survival.migrations()).isEmpty();
    }

    @Test
    void ranksShipsEnabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule ranks =
                registry.byId(ModuleId.of("ranks")).orElseThrow(() -> new AssertionError("ranks is not registered"));

        // ranks is the 25th context: rankup/prestige/autorank progression with a DB-backed rank pointer. The later
        // trade context now lands last, so ranks must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("ranks"))).isPresent();

        // It ships ENABLED but inert (nothing happens until an operator authors a ladder): with no modules.conf
        // override it is on, and disabling exactly ranks removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("ranks", "teleport", "economy", "holograms");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.ranks.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("ranks");
        assertThat(off).contains("teleport", "holograms");

        // The /rankup, /prestige and /ranks verbs are contributed through the adapter wiring in the later phases,
        // not the declarative lists, so the module publishes no command here, and its player_ranks table is in the
        // persistence V74 baseline (always applied), so it declares no MigrationSet of its own.
        assertThat(ranks.commands()).isEmpty();
        assertThat(ranks.migrations()).isEmpty();
    }

    @Test
    void commandControlShipsEnabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule commandControl = registry.byId(ModuleId.of("commandcontrol"))
                .orElseThrow(() -> new AssertionError("commandcontrol is not registered"));

        // commandcontrol is a new context: command whitelist/blacklist gating. It is registered before trade so trade
        // stays last, so it must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("commandcontrol"))).isPresent();

        // It ships ENABLED but inert (the default config is a blacklist with empty lists, so nothing is gated until an
        // operator names commands): with no modules.conf override it is on, and disabling exactly it removes only it
        // while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("commandcontrol", "teleport", "economy", "holograms", "security");
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.commandcontrol.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("commandcontrol");
        assertThat(off).contains("teleport", "holograms", "security");

        // The PlayerCommandPreprocessEvent gate is contributed through the adapter wiring (it needs the live player and
        // the group/permission lookups), not the declarative lists, so the module publishes no command here, and it
        // persists nothing (the rule set is derived from config), so it declares no MigrationSet.
        assertThat(commandControl.commands()).isEmpty();
        assertThat(commandControl.migrations()).isEmpty();
    }

    @Test
    void tradeShipsEnabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule trade =
                registry.byId(ModuleId.of("trade")).orElseThrow(() -> new AssertionError("trade is not registered"));

        // trade is the 26th context: secure player-to-player trading (/trade). The later villagers context now lands
        // last, so trade must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("trade"))).isPresent();

        // It ships ENABLED (a steady-state feature. /trade is offered out of the box): with no modules.conf override
        // it is on, and disabling exactly trade removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("trade", "teleport", "economy", "holograms", "ranks");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.trade.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("trade");
        assertThat(off).contains("teleport", "holograms", "ranks");

        // The /trade verbs and the trade-window listeners are contributed through the adapter wiring in the later
        // phases, not the declarative lists, so the module publishes no command here, and a same-server trade is
        // transient in-memory state (no DB) in Phase 1, so it declares no MigrationSet.
        assertThat(trade.commands()).isEmpty();
        assertThat(trade.migrations()).isEmpty();
    }

    @Test
    void villagersShipsDisabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule villagers = registry.byId(ModuleId.of("villagers"))
                .orElseThrow(() -> new AssertionError("villagers is not registered"));

        // villagers is a bounded context. Villager trade management (Phase 1: infinite trading, restock timer,
        // instant restock, disable trades). The later invrollback context now lands last, so villagers must merely
        // be registered, not last.
        assertThat(registry.byId(ModuleId.of("villagers"))).isPresent();

        // It ships DISABLED: every feature here rewrites how villager trading works, and a server that wants vanilla
        // villagers should get vanilla villagers. Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("villagers");
        assertThat(defaults).contains("teleport", "economy", "holograms", "trade");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.villagers.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("villagers", "teleport", "holograms", "trade");

        // The trade / interact listeners and the restock sweep are contributed through the adapter wiring (gated per
        // feature), not the declarative lists, so the module publishes no command here, and it persists nothing (the
        // last-restock stamp and disable flag are PDC state on the villager), so it declares no MigrationSet.
        assertThat(villagers.commands()).isEmpty();
        assertThat(villagers.migrations()).isEmpty();
    }

    @Test
    void invrollbackShipsDisabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule invrollback = registry.byId(ModuleId.of("invrollback"))
                .orElseThrow(() -> new AssertionError("invrollback is not registered"));

        // invrollback is a bounded context: AxInventoryRestore-parity inventory snapshots on death and logout. The
        // later regions context now lands last, so invrollback must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("invrollback"))).isPresent();

        // It ships DISABLED: every death and every logout would write an inventory snapshot to the database, a cost
        // only a server whose staff actually restore inventories should pay. Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("invrollback");
        assertThat(defaults).contains("teleport", "economy", "holograms");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.invrollback.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("invrollback", "teleport", "holograms");
        assertThat(on).doesNotContain("villagers");

        // The death/logout capture listeners are contributed through the adapter wiring, not the declarative lists,
        // so the module publishes no command here, and its inv_snapshots table is in the persistence V79 baseline
        // (always applied), so it declares no MigrationSet of its own.
        assertThat(invrollback.commands()).isEmpty();
        assertThat(invrollback.migrations()).isEmpty();
    }

    @Test
    void regionsShipsDisabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule regions = registry.byId(ModuleId.of("regions"))
                .orElseThrow(() -> new AssertionError("regions is not registered"));

        // regions is a bounded context: a GUI to manage WorldGuard regions behind a SOFT dependency. The later
        // servertweaks context now lands last, so regions must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("regions"))).isPresent();

        // It ships DISABLED: the whole module is a front end for WorldGuard, so on a server without it there is
        // nothing to manage and /regions would only answer "not installed". Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("regions");
        assertThat(defaults).contains("teleport", "economy", "holograms");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.regions.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("regions", "teleport", "holograms");
        assertThat(on).doesNotContain("invrollback");

        // The /regions command is Bukkit-facing and bound only when WorldGuard is present, so it is contributed
        // through the adapter wiring rather than the declarative list; the module publishes no command here, and it
        // persists nothing (WorldGuard owns the region store), so it declares no MigrationSet.
        assertThat(regions.commands()).isEmpty();
        assertThat(regions.migrations()).isEmpty();
    }

    @Test
    void serverTweaksShipsEnabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule serverTweaks = registry.byId(ModuleId.of("servertweaks"))
                .orElseThrow(() -> new AssertionError("servertweaks is not registered"));

        // servertweaks is a bounded context: a grab-bag of small server/infra tweaks (custom F3 brand,
        // console-spam filter). The later skin context now lands last, so servertweaks must merely be registered.
        assertThat(registry.byId(ModuleId.of("servertweaks"))).isPresent();

        // It ships ENABLED but inert (every individual tweak defaults off, so being on changes nothing until an
        // operator opts a tweak in): with no modules.conf override it is on, and disabling exactly it removes only it
        // while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("servertweaks", "teleport", "economy", "holograms");
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.servertweaks.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("servertweaks");
        assertThat(off).contains("teleport", "holograms");

        // The tweaks are Bukkit-facing side effects (a brand plugin-message on join, a Log4j2 console filter)
        // contributed through the adapter wiring, gated per tweak, not the declarative lists, so the module publishes
        // no command here, and it persists nothing (each tweak is a live side effect), so it declares no MigrationSet.
        assertThat(serverTweaks.commands()).isEmpty();
        assertThat(serverTweaks.migrations()).isEmpty();
    }

    @Test
    void skinIsTheLastModuleShipsDisabledAndPublishesNoDeclarativeSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule skin =
                registry.byId(ModuleId.of("skin")).orElseThrow(() -> new AssertionError("skin is not registered"));

        // skin is the last bounded context: SkinsRestorer-parity skins by name, url, file and Bedrock, registered
        // last.
        assertThat(registry.all().get(registry.all().size() - 1).id().value()).isEqualTo("skin");

        // It ships DISABLED: on an online-mode server every player already wears their own skin, so the module
        // would buy nothing but extra lookups. Turning exactly it on adds only it.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("skin");
        assertThat(defaults).contains("teleport", "economy", "holograms");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.skin.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("skin", "teleport", "holograms");
        assertThat(on).doesNotContain("invrollback");

        // The /skin command and the pre-login listener are Bukkit-facing and contributed through the adapter
        // wiring, and player_skins ships in the persistence baseline, so the module declares no MigrationSet.
        assertThat(skin.commands()).isEmpty();
        assertThat(skin.migrations()).isEmpty();
    }

    @Test
    void worldsShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule worlds =
                registry.byId(ModuleId.of("worlds")).orElseThrow(() -> new AssertionError("worlds is not registered"));

        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("worlds", "teleport");

        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.worlds.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("worlds");
        assertThat(off).contains("teleport");

        Set<String> literals =
                worlds.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("worlds");
        assertThat(worlds.migrations()).isEmpty();
    }

    @Test
    void registryRejectsDuplicateIds() {
        ModuleRegistry registry = new ListModuleRegistry().register(new FakeModule("homes"));

        assertThatThrownBy(() -> registry.register(new FakeModule("homes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate module id");
    }

    @Test
    void lookupResolvesRegisteredModulesAndPreservesOrder() {
        FakeModule economy = new FakeModule("economy");
        FakeModule homes = new FakeModule("homes");
        ModuleRegistry registry = new ListModuleRegistry().register(economy).register(homes);

        assertThat(registry.all()).containsExactly(economy, homes); // dependency-first order preserved
        assertThat(registry.byId(ModuleId.of("homes"))).contains(homes);
        assertThat(registry.byId(ModuleId.of("warps"))).isEmpty();
    }

    @Test
    void everyRegisteredModuleIsIndependentlyDisableable() {
        List<String> ids = List.of("economy", "teleport", "homes", "warps");
        ModuleRegistry registry = new ListModuleRegistry();
        ids.forEach(id -> registry.register(new FakeModule(id)));

        // Disabling exactly one module removes only that module from the enabled set; the rest stay.
        for (String disabled : ids) {
            ConfigStore config = new FixedConfig(Map.of("modules." + disabled + ".enabled", false));
            Set<String> enabled = idsOf(registry.enabledModules(config));

            assertThat(enabled).doesNotContain(disabled);
            assertThat(enabled).containsExactlyInAnyOrderElementsOf(others(ids, disabled));
        }
    }

    @Test
    void disablingEveryModuleWiresNothing() {
        List<String> ids = List.of("economy", "homes", "warps");
        ModuleRegistry registry = new ListModuleRegistry();
        ids.forEach(id -> registry.register(new FakeModule(id)));

        Map<String, Object> allOff =
                ids.stream().collect(Collectors.toMap(id -> "modules." + id + ".enabled", id -> false));

        assertThat(registry.enabledModules(new FixedConfig(allOff))).isEmpty();
    }

    @Test
    void missingSwitchDefaultsToEnabled() {
        ModuleRegistry registry = new ListModuleRegistry().register(new FakeModule("kits"));

        // No key for modules.kits.enabled: the module defaults to on (operators opt out).
        assertThat(idsOf(registry.enabledModules(new FixedConfig(Map.of())))).containsExactly("kits");
    }

    private static Set<String> idsOf(List<FeatureModule> modules) {
        return modules.stream().map(m -> m.id().value()).collect(Collectors.toSet());
    }

    private static Set<String> others(List<String> ids, String excluded) {
        return ids.stream().filter(id -> !id.equals(excluded)).collect(Collectors.toSet());
    }

    /** A minimal {@link FeatureModule} that contributes nothing, enough to exercise the contract. */
    private static final class FakeModule implements FeatureModule {
        private final ModuleId id;

        FakeModule(String id) {
            this.id = ModuleId.of(id);
        }

        @Override
        public ModuleId id() {
            return id;
        }

        @Override
        public String configRoot() {
            return id.configRoot();
        }

        @Override
        public List<CommandSpec> commands() {
            return List.of();
        }

        @Override
        public List<ListenerFactory> listeners() {
            return List.of();
        }

        @Override
        public List<MigrationSet> migrations() {
            return List.of();
        }

        @Override
        public boolean enabled(ConfigStore config) {
            return config.getBoolean(configRoot() + ".enabled", true);
        }

        @Override
        public void start(ModuleContext ctx) {
            // No-op: a stand-in module acquires nothing.
        }

        @Override
        public void stop() {
            // No-op: nothing to release.
        }
    }

    /** A map-backed {@link ConfigStore} for driving enable gates in tests. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
