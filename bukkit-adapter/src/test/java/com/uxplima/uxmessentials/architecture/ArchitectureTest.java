package com.uxplima.uxmessentials.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.conditions.ArchConditions.implement;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/**
 * Compiler-backstop architecture fences. They run as ordinary JUnit 5 tests on every {@code check}.
 *
 * <p>Each rule carries {@code allowEmptyShould(true)} so it passes vacuously while a layer or context
 * is still empty: ArchUnit 1.x otherwise fails a rule that matches zero classes. The flag is dropped
 * per rule once the matching layer fills. This phase fills only the kernel module framework and the
 * bootstrap, so the domain/application-purity rules and the JavaPlugin-containment rule already have
 * teeth.
 */
@AnalyzeClasses(packages = "com.uxplima.uxmessentials")
class ArchitectureTest {

    // Domain layer (:core) imports no Bukkit / Paper / Adventure.
    @ArchTest
    static final ArchRule domainHasNoBukkit = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori..")
            .allowEmptyShould(true);

    // Application layer (:core) imports no Bukkit / Paper / Adventure.
    @ArchTest
    static final ArchRule applicationHasNoBukkit = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori..")
            .allowEmptyShould(true);

    // The :core module is also free of SLF4J and infrastructure libraries: the ports stay pure.
    @ArchTest
    static final ArchRule domainAndApplicationHaveNoInfrastructure = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.slf4j..",
                    "com.zaxxer..",
                    "org.jooq..",
                    "com.github.benmanes..",
                    "org.spongepowered.configurate..")
            .allowEmptyShould(true);

    // Only bootstrap may depend on the concrete JavaPlugin class. A future contributor reaching for
    // JavaPlugin (or JavaPlugin.getInstance) outside bootstrap breaks constructor-injection here.
    @ArchTest
    static final ArchRule bootstrapIsTheOnlyPlaceWithJavaPlugin = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.plugin.java.JavaPlugin")
            .allowEmptyShould(true);

    // BukkitScheduler is forbidden everywhere, scheduling goes through the Folia-aware Scheduler
    // port so the plugin stays Folia-compatible.
    @ArchTest
    static final ArchRule noClassDependsOnBukkitScheduler = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler")
            .allowEmptyShould(true);

    // The economy domain/application is provider-agnostic: it models money, not Vault or Treasury. The
    // SDK types are confined to the outbound adapter packages (economy.adapter.treasury / .vault); a
    // contributor reaching for a Vault/Treasury type in economy.domain or economy.application breaks the
    // single-seam design here (docs/11-economy-integration.md §5).
    @ArchTest
    static final ArchRule economyDomainHasNoProviderSdk = noClasses()
            .that()
            .resideInAnyPackage("..economy.domain..", "..economy.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.milkbowl.vault..", "me.lokka30.treasury..")
            .allowEmptyShould(true);

    // The published API artifacts are a compatibility promise: once a signature ships, third-party plugins
    // compile against it and it is frozen. That promise is only keepable if the API cannot reach an internal
    // type, because a leaked domain record or engine class would be frozen too, by accident, and every later
    // refactor of it would break somebody's build. So the boundary is a fence rather than a convention: the
    // api packages may name JDK types, Bukkit types and each other, and nothing else of ours.
    @ArchTest
    static final ArchRule apiModulesDoNotDependOnInternals = noClasses()
            .that()
            .resideInAPackage("com.uxplima.uxmessentials.api..")
            // The API's own tests live in this package tree and drive the implementation behind the boundary on
            // purpose; only what actually ships in the artifacts is fenced.
            .and(areProductionClasses())
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage("com.uxplima.uxmessentials..")
                    .and(DescribedPredicate.not(
                            JavaClass.Predicates.resideInAPackage("com.uxplima.uxmessentials.api.."))))
            .because("the published API must expose only JDK types, Bukkit types and its own interfaces; "
                    + "reaching an internal type would freeze that type's shape forever")
            .allowEmptyShould(true);

    // A fact and a proposal are both values: everything they are is in their components, two of them with the same
    // components are the same thing, and neither has behaviour to hide. Records say all of that in the declaration,
    // so a class here is a sign somebody is about to give an event mutable state or identity it should not have.
    // The sealed per-context interfaces in between are exempt because they are the grouping, not the value.
    @ArchTest
    static final ArchRule domainFactsAndProposalsAreRecords = classes()
            .that()
            .areAssignableTo(DomainEvent.class)
            .or(JavaClass.Predicates.assignableTo(DomainProposal.class))
            .and(areProductionClasses())
            .and()
            .areNotInterfaces()
            .should()
            .beRecords()
            .because("a domain fact and a domain proposal are values, and a value with an identity is a bug "
                    + "waiting for somebody to mutate it")
            .allowEmptyShould(true);

    // The menu engine's spec model and evaluation are the pure core: plain JUnit can exercise them
    // without a server. Keeping them free of Bukkit / Paper / NMS is what makes that possible.
    @ArchTest
    static final ArchRule menuPureCoreHasNoBukkit = noClasses()
            .that()
            .resideInAnyPackage("..gui.menu.spec..", "..gui.menu.eval..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.minecraft..")
            .because("the menu engine's spec model and evaluation must stay pure for plain-JUnit testing");

    // Optional-plugin integrations are an outbound, Bukkit-side concern: the hooks SPI references org.bukkit and
    // each real impl references a plugin SDK. The menu engine's pure core (spec model + evaluation) must stay
    // plain-JUnit testable, so it may not reach into the hooks package. A spec/eval class importing Hooks would
    // pull the Bukkit-side integration layer into the pure core. (Vacuous today; a guard for the future, since the
    // org.bukkit fence above only catches direct org.bukkit dependencies, not a transitive one through Hooks.)
    @ArchTest
    static final ArchRule menuPureCoreDoesNotUseHooks = noClasses()
            .that()
            .resideInAnyPackage("..gui.menu.spec..", "..gui.menu.eval..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..shared.adapter.outbound.hooks..")
            .because("the menu engine's spec model and evaluation stay pure; optional-plugin hooks are an "
                    + "outbound Bukkit-side concern consumed at the adapter edge")
            .allowEmptyShould(true);

    // The engine's public surface is the Menus facade, the MenuBindings registries, and the two context
    // types a binding lambda is handed, MenuContext (condition/placeholder/list) and MenuActionContext
    // (action). A feature wires behaviour by reading those contexts, so they are public by contract even
    // though they live alongside the runtime. Everything else under render/ and runtime/, the holder, the
    // click listener, the refresh task: is the engine's private machinery and stays off-limits outside it.
    // Three packages are exempt for the same reason they are everywhere else: bootstrap is the composition
    // root that constructs the engine (it is not a feature), the engine's own tests under shared.menu..
    // legitimately exercise render/runtime directly, and shared.adapter.inbound.api is the developer-API
    // boundary. Its whole job is to translate the engine's runtime contexts into the published MenuView /
    // MenuClick interfaces, which it cannot do without naming the contexts it wraps. That translation is what
    // keeps every other consumer, inside the plugin or outside it, off the internals.
    @ArchTest
    static final ArchRule menuInternalsAreNotUsedOutsideTheEngine = noClasses()
            .that()
            .resideOutsideOfPackages(
                    "..gui.menu..",
                    "..bootstrap..",
                    "com.uxplima.uxmessentials.shared.menu..",
                    "com.uxplima.uxmessentials.shared.adapter.inbound.api..")
            .and(areProductionClasses())
            .should()
            .dependOnClassesThat(menuInternals())
            .because("only the Menus facade, the MenuBindings registries, and the MenuContext/MenuActionContext a "
                    + "binding reads are the public surface; the holder, listener, refresh task and renderer stay "
                    + "private to the engine");

    // Every spec-driven menu renders through the engine (Menus / MenuBindings / holder / listener); none of them
    // touch uxmLib's GUI library directly. The only production classes that legitimately depend on
    // com.uxplima.uxmlib.gui are the non-menu text-input and storage leaves, and they stay that way:
    //   - vaults VaultView and itemworld DisposalCommand are real item-STORAGE inventories (uxmLib StorageGui):
    //     players put and take items, contents persist: these are not menus.
    //   - the shared AnvilTextBackend, SignTextBackend, DialogTextBackend and their TextInputInstaller are the
    //     TEXT-INPUT seam (uxmLib com.uxplima.uxmlib.gui anvil/input/dialog). The runtime-neutral leaves the whole
    //     engine reuses for typed input.
    //   - bootstrap PluginModule is the Guis.install(...) site: the uxmLib GUI runtime must stay installed for the
    //     storage and anvil leaves above.
    // Any other production class reaching for com.uxplima.uxmlib.gui would be a spec menu that slipped off the
    // engine; this fence fails until it is migrated, rather than letting it bypass the engine silently.
    @ArchTest
    static final ArchRule onlyStorageAndAnvilLeavesUseUxmlibGui = noClasses()
            .that()
            .resideInAPackage("..uxmessentials..")
            .and(areProductionClasses())
            .and(areNotAllowedUxmlibGuiLeaves())
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.uxplima.uxmlib.gui..")
            .because("spec-driven menus must render through the engine; only the item-storage inventories "
                    + "(VaultView, DisposalCommand), the text-input seam (AnvilTextBackend, SignTextBackend, "
                    + "DialogTextBackend, TextInputInstaller) and the Guis.install site (PluginModule) may touch "
                    + "uxmLib's GUI library");

    // The completeness twin of the uxmLib fence above. That rule proves no spec menu reaches for uxmLib's GUI
    // library; this one proves no spec menu drops a level lower and hand-rolls a raw Bukkit inventory instead
    // the exact way an editor cluster once slipped past a uxmLib-imports-only completeness check. Two signatures
    // betray a hand-rolled GUI: implementing org.bukkit.inventory.InventoryHolder (every bespoke menu holder
    // carries it) and calling Bukkit.createInventory / HumanEntity.openInventory (building or showing the frame).
    //
    // Outside the engine package, a fixed allow-list of production classes may do either, and all of them are
    // genuine non-menu leaves, item containers or the security keypad, not spec menus:
    //   - itemworld Workstation opens the vanilla MenuType workstations (anvil, loom, furnace, ...) and the
    //     player's own ender chest: real game containers, no menu spec.
    //   - itemworld ShulkerBoxView / ShulkerBoxHolder open a shulker box the player right-clicks in hand as an
    //     editable 27-slot container and write the edits back into the box item on close, an item container, not
    //     a menu.
    //   - kits KitEditorView / KitEditorHolder and KitPreviewView / KitPreviewHolder are the kit item grids: a bare
    //     run of slots holding the kit's stacks, with no chrome at all, and the preview sizes itself to however many
    //     items the kit happens to hold. There is nothing for a spec file to theme, and a spec would fix the height
    //     the preview needs to vary, so both stay raw deliberately.
    //   - vanish VanishSilentContainerListener re-opens the very container the vanished player clicked, without
    //     touching its state, so there is no window of ours to describe.
    // (The 2FA keypad, both trade windows, the playerstate invsee/endersee mirrors, the invrollback snapshot preview
    // and the villagers trade manager all migrated onto the engine, so they are no longer here. Each is now spec
    // chrome around a declared content region.)
    // Every spec-driven MENU instead renders through the engine (the Menus facade); the engine's own MenuHolder,
    // Menus and EditorRefresh live inside ..gui.menu.. and are exempt by package. A new bespoke createInventory /
    // InventoryHolder GUI appearing anywhere else fails this fence until it is migrated onto the engine, rather
    // than letting it bypass the engine silently the way the editor cluster once did.
    @ArchTest
    static final ArchRule onlyTheEngineAndInventoryLeavesTouchRawBukkitInventories = noClasses()
            .that()
            .resideInAPackage("..uxmessentials..")
            .and(areProductionClasses())
            .and(areNotAllowedRawBukkitInventoryLeaves())
            .should(buildsOrOpensARawBukkitInventory())
            .because("spec-driven menus must render through the engine (the Menus facade); only the engine itself "
                    + "and the genuine inventory leaves, the itemworld Workstation and shulker-box view, the kits "
                    + "item grids, and the vanish silent-container mirror, may create or open a raw Bukkit "
                    + "inventory");

    /**
     * Builds the condition matching either raw-Bukkit-GUI signature: implementing
     * {@code org.bukkit.inventory.InventoryHolder} (which every bespoke menu holder carries to tag its frame), or
     * calling a Bukkit inventory-construction / open method, {@code Bukkit.createInventory(...)} or
     * {@code HumanEntity.openInventory(Inventory)}. The call check matches by the called method's owner residing in
     * {@code org.bukkit..} and its name, so the playerstate use-case method that happens to be named
     * {@code openInventory} but lives on a domain port is not mistaken for the Bukkit one.
     */
    private static ArchCondition<JavaClass> buildsOrOpensARawBukkitInventory() {
        return implement("org.bukkit.inventory.InventoryHolder")
                .or(callMethodWhere(callsBukkitInventoryFactoryOrOpen()))
                .as("create or open a raw Bukkit inventory");
    }

    /**
     * Matches a method call that builds or shows a Bukkit inventory frame: {@code Bukkit.createInventory} or
     * {@code HumanEntity.openInventory}. The match keys on the called method's declaring class residing in
     * {@code org.bukkit..} (so {@code Player.openInventory}, declared on {@code HumanEntity}, counts) together with
     * the method name, never on the name alone, so the playerstate {@code OpenContainer.openInventory} use case is
     * left untouched.
     */
    private static DescribedPredicate<JavaMethodCall> callsBukkitInventoryFactoryOrOpen() {
        return DescribedPredicate.describe("call Bukkit.createInventory or HumanEntity.openInventory", call -> {
            String owner = call.getTargetOwner().getPackageName();
            String method = call.getName();
            return owner.startsWith("org.bukkit")
                    && (method.equals("createInventory") || method.equals("openInventory"));
        });
    }

    /**
     * The non-menu inventory leaves allowed to create or open a raw Bukkit inventory outside the engine, named by
     * fully qualified name so this predicate itself adds no dependency on them. None is a spec menu: the itemworld
     * {@code Workstation} opens vanilla game containers, the itemworld {@code ShulkerBoxView} / {@code ShulkerBoxHolder}
     * open a held shulker box as an editable item container, the kits {@code KitEditorView} / {@code KitPreviewView}
     * and their holders are bare item grids with no chrome to theme, and the vanish
     * {@code VanishSilentContainerListener} mirrors a container silently for a vanished opener. Every spec-driven menu
     * renders through the engine instead, so this allow-list must stay exactly these leaves.
     *
     * <p>A leaf's nested members carry the same signature (a holder built as a private inner class, a view's nested
     * record), so the match is on the top-level enclosing class, not the exact nested name, mirroring the uxmLib
     * fence's allow-list above.
     */
    private static DescribedPredicate<JavaClass> areNotAllowedRawBukkitInventoryLeaves() {
        java.util.Set<String> allowed = java.util.Set.of(
                "com.uxplima.uxmessentials.itemworld.adapter.inbound.command.Workstation",
                "com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ShulkerBoxView",
                "com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ShulkerBoxHolder",
                "com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView",
                "com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorHolder",
                "com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView",
                "com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewHolder",
                "com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishSilentContainerListener");
        return DescribedPredicate.describe("are not the allowed raw-Bukkit inventory leaves", javaClass -> {
            String fullName = javaClass.getFullName();
            int nested = fullName.indexOf('$');
            String topLevel = nested < 0 ? fullName : fullName.substring(0, nested);
            return !allowed.contains(topLevel) && !isMenuEngine(topLevel);
        });
    }

    /**
     * The menu engine package is exempt by location: its {@code MenuHolder} implements {@code InventoryHolder} and
     * its {@code Menus} / {@code EditorRefresh} call {@code createInventory} / {@code openInventory}: that is the
     * one sanctioned place a Bukkit inventory is created, and every spec menu funnels through it.
     */
    private static boolean isMenuEngine(String topLevelName) {
        return topLevelName.startsWith("com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.");
    }

    /**
     * The non-menu leaves allowed to depend on uxmLib's GUI library, named by fully qualified name so this predicate
     * itself adds no dependency on them. Two are item-storage inventories ({@code VaultView}, {@code DisposalCommand},
     * built on uxmLib's {@code StorageGui}); the text-input seam contributes the anvil, sign and dialog backends
     * ({@code AnvilTextBackend}, {@code SignTextBackend}, {@code DialogTextBackend}) and their {@code TextInputInstaller},
     * built on {@code com.uxplima.uxmlib.gui} anvil/input/dialog; and one is the {@code Guis.install} site
     * ({@code PluginModule}). Every spec-driven menu renders through the engine instead, so this allow-list must stay
     * exactly these leaves.
     *
     * <p>A leaf's nested members ({@code VaultView.OpenWindow}, {@code TextInputInstaller.Installed},
     * {@code PluginModule.ContextLinks}) carry the dependency too, a held {@code StorageGui} or {@code AnvilInput}
     * surfaces as a nested record's field, so the match is on the top-level enclosing class, not the exact nested
     * name. That keeps the allow-list to these five top-level leaves while still covering their inner classes.
     */
    private static DescribedPredicate<JavaClass> areNotAllowedUxmlibGuiLeaves() {
        java.util.Set<String> allowed = java.util.Set.of(
                "com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView",
                "com.uxplima.uxmessentials.itemworld.adapter.inbound.command.DisposalCommand",
                "com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.AnvilTextBackend",
                "com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.SignTextBackend",
                "com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.DialogTextBackend",
                "com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller",
                "com.uxplima.uxmessentials.bootstrap.di.PluginModule");
        return DescribedPredicate.describe("are not the allowed uxmLib-GUI leaves", javaClass -> {
            String fullName = javaClass.getFullName();
            int nested = fullName.indexOf('$');
            String topLevel = nested < 0 ? fullName : fullName.substring(0, nested);
            return !allowed.contains(topLevel);
        });
    }

    /**
     * The engine's private machinery: everything under {@code render/} plus the runtime internals, but not the
     * two public context types a binding lambda reads. Naming the runtime internals one by one (by their fully
     * qualified names, so this predicate itself does not depend on those classes) is what lets feature wiring
     * register a binding through {@code MenuContext}/{@code MenuActionContext} while keeping the holder, listener
     * and refresh task encapsulated.
     */
    private static DescribedPredicate<JavaClass> menuInternals() {
        String runtime = "com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.";
        java.util.Set<String> runtimeInternals = java.util.Set.of(
                runtime + "MenuHolder",
                runtime + "MenuListener",
                runtime + "MenuRefresh",
                runtime + "Cancellable",
                runtime + "PagedListView",
                runtime + "PagedListRows");
        return JavaClass.Predicates.resideInAPackage("..gui.menu.render..")
                .or(DescribedPredicate.describe(
                        "are menu runtime internals", javaClass -> runtimeInternals.contains(javaClass.getFullName())))
                .as("menu engine render/runtime internals");
    }

    /**
     * Production classes only. The architecture tests and their nested helpers legitimately wire the engine to
     * exercise it, so this rule must not flag a test that constructs the renderer or listener for a fixture.
     */
    private static DescribedPredicate<JavaClass> areProductionClasses() {
        return DescribedPredicate.describe(
                "are production classes", javaClass -> !javaClass.getName().contains("Test"));
    }

    /**
     * A typed player name becomes an account in one place. Paper reads the server's own name cache only when the
     * server is in online mode ({@code CraftServer.getOfflinePlayer(String)} gates it on
     * {@code proxies.isProxyOnlineMode()}) and otherwise derives the uuid from the typed name verbatim, so a
     * second by-name resolver anywhere in the plugin is a name that an offline-mode server cannot resolve unless
     * its case matches exactly. Everything goes through the kernel {@code PlayerLookup} instead.
     */
    @ArchTest
    static final ArchRule nameResolutionGoesThroughTheSharedLookup = noClasses()
            .that()
            .resideInAPackage("..uxmessentials..")
            .and(areProductionClasses())
            .and(areNotAllowedByNameResolvers())
            .should(callMethodWhere(callsGetOfflinePlayerByName()))
            .because("a player name becomes an account in exactly one place, the kernel PlayerLookup (backed by "
                    + "the plugin's own name index), so an offline-mode server resolves a name the same way an "
                    + "online-mode one does");

    /**
     * Matches {@code Bukkit.getOfflinePlayer(String)} / {@code Server.getOfflinePlayer(String)}, never the
     * {@code UUID} overload, which carries the identity already and is fine anywhere.
     */
    private static DescribedPredicate<JavaMethodCall> callsGetOfflinePlayerByName() {
        return DescribedPredicate.describe(
                "call getOfflinePlayer(String)",
                call -> call.getTargetOwner().getPackageName().startsWith("org.bukkit")
                        && call.getName().equals("getOfflinePlayer")
                        && call.getTarget().getRawParameterTypes().size() == 1
                        && call.getTarget()
                                .getRawParameterTypes()
                                .get(0)
                                .getName()
                                .equals("java.lang.String"));
    }

    /**
     * The classes allowed to resolve a name against the server themselves, by fully qualified name so this
     * predicate adds no dependency on them. {@code BukkitPlayerLookup} is the kernel fallback the whole plugin
     * routes through. The claim, Vault and PlaceholderAPI adapters are handed a bare name by a third-party API
     * and have no account to start from. {@code SkullCommand} resolves a skull owner who need never have joined,
     * and {@code VotifierListener} keeps a vote for a player who has not joined yet. Every other by-name
     * resolution belongs on the kernel lookup.
     */
    private static DescribedPredicate<JavaClass> areNotAllowedByNameResolvers() {
        return DescribedPredicate.describe("are not the allowed by-name resolvers", javaClass -> {
            String name = javaClass.getName();
            return ALLOWED_BY_NAME_RESOLVERS.stream().noneMatch(name::startsWith);
        });
    }

    /**
     * Wiring runs before the worlds exist, so it may not ask for them.
     *
     * <p>The plugin declares {@code load: STARTUP} in {@code paper-plugin.yml}, because that is the only way
     * {@code getDefaultWorldGenerator} is ever reached for the default world: {@code CraftServer.getGenerator}
     * refuses a generator whose plugin is not enabled yet. The price is that {@code onEnable}, and therefore
     * every {@code wire} method below it, runs at a point where the server has no worlds at all.
     *
     * <p>What that breaks is quiet rather than loud. {@code getWorlds()} returns an empty list, so a warmup or a
     * migration keyed on it does nothing and says nothing; {@code createWorld} builds a world before the server
     * has made its own. Four wiring paths were doing exactly this before the STARTUP switch, and none of them
     * would have failed a test. Enumerating or creating worlds during wiring is therefore banned outright:
     * hand the work to {@code WorldPhase} instead and it runs at {@code ServerLoadEvent}, which is the first
     * moment the worlds are up on a boot and immediately on a runtime re-wire.
     *
     * <p>Reads of a single world through a player or a stored position are untouched, because a wiring class
     * also holds the callbacks it registers, and those run later with a real player in hand.
     */
    @ArchTest
    static final ArchRule wiringDoesNotEnumerateOrCreateWorlds = noClasses()
            .that()
            .resideInAPackage("..uxmessentials..")
            .and(areProductionClasses())
            .and(areWiringClasses())
            .should(callMethodWhere(enumeratesOrCreatesWorlds()))
            .because("the plugin enables before the worlds exist (load: STARTUP), so wiring that counts or makes "
                    + "worlds silently does nothing; hand it to WorldPhase and it runs once they are up");

    /** The classes that run during enable: bootstrap's own wiring and each context's {@code *Wiring}. */
    private static DescribedPredicate<JavaClass> areWiringClasses() {
        return DescribedPredicate.describe("are wiring classes", javaClass -> {
            String name = javaClass.getName();
            return name.startsWith("com.uxplima.uxmessentials.bootstrap.di.PluginModule")
                    || name.matches(".*\\.[A-Za-z]+Wiring(\\$.*)?$");
        });
    }

    /** Matches the whole-server world calls: listing every world, or making and unmaking one. */
    private static DescribedPredicate<JavaMethodCall> enumeratesOrCreatesWorlds() {
        return DescribedPredicate.describe(
                "enumerate or create worlds",
                call -> WHOLE_SERVER_WORLD_CALLS.contains(call.getName())
                        && (call.getTargetOwner().getName().equals("org.bukkit.Bukkit")
                                || call.getTargetOwner().getName().equals("org.bukkit.Server")));
    }

    private static final List<String> WHOLE_SERVER_WORLD_CALLS = List.of("getWorlds", "createWorld", "unloadWorld");

    private static final List<String> ALLOWED_BY_NAME_RESOLVERS = List.of(
            "com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitPlayerLookup",
            "com.uxplima.uxmessentials.shared.adapter.outbound.claim.",
            "com.uxplima.uxmessentials.shared.adapter.outbound.hooks.VaultPermissionService",
            "com.uxplima.uxmessentials.shared.adapter.outbound.hooks.VaultEconomyService",
            "com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport",
            "com.uxplima.uxmessentials.itemworld.adapter.inbound.command.SkullCommand",
            "com.uxplima.uxmessentials.vote.adapter.inbound.listener.VotifierListener");
}
