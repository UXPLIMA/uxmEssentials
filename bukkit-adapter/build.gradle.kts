import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.minotaur)
    alias(libs.plugins.hangar.publish)
    alias(libs.plugins.paperweight) // offline /invsee reads player-data NBT through Mojang-mapped NMS
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence-adapter"))
    implementation(project(":migration"))
    api(project(":api"))
    api(project(":bukkit-api"))

    // The Mojang-mapped dev bundle supplies the Paper API *and* the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the offline-inventory adapter needs; it replaces the plain paper-api
    // compile dependency for the main source set. Paper's runtime plugin remapper maps the shipped
    // Mojang-mapped jar back to the server's mappings at load (see shadowJar manifest below).
    paperweight.paperDevBundle(libs.versions.paper.get())
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    compileOnly(libs.luckperms.api) // optional soft-depend (Permissions port, ADR 0005)

    // Economy provider soft-depends — compileOnly: the outbound adapters bind to
    // these at runtime only if the plugin is present (ServicesManager, ADR 0004).
    compileOnly(libs.treasury.api)
    compileOnly(libs.vault.api)

    // Claim-plugin hook soft-depends — compileOnly: the inbound adapters bind to
    // these only when the plugin is present (checked via Bukkit ServicesManager / plugin lookup).
    compileOnly(libs.hook.lands)
    compileOnly(libs.hook.griefprevention)
    compileOnly(libs.hook.simpleclaimsystem)
    compileOnly(libs.hook.rclaim)

    // PlaceholderAPI soft-depend — compileOnly: the expansion and the message bridge
    // touch these symbols only past the plugin-present guard, so the plugin runs fully
    // without PlaceholderAPI installed.
    compileOnly(libs.placeholderapi)

    // MiniPlaceholders soft-depend — compileOnly: its MiniMessage tag resolvers are read only past the
    // plugin-present guard in MiniPlaceholdersSupport, so the plugin runs fully without it installed.
    compileOnly(libs.miniplaceholders)

    // Map-plugin marker soft-depends — compileOnly: the Dynmap/squaremap marker publishers touch these
    // symbols only past the plugin-present guard, so the plugin runs fully with neither map plugin installed.
    // Dynmap splits its surface: dynmap-api carries the DynmapAPI plugin handle, DynmapCoreAPI the markers package.
    compileOnly(libs.squaremap.api)
    compileOnly(libs.dynmap.api)
    compileOnly(libs.dynmap.core.api)

    // Floodgate/Cumulus Bedrock soft-depend — compileOnly: the Bedrock detector binds only when Floodgate is
    // present, so the plugin runs fully on a Java-only server (Cumulus's form classes arrive transitively).
    compileOnly(libs.floodgate.api)

    compileOnly(libs.bundles.configs)
    // Configurate YAML — the DeluxeMenus menu converter reads competitor menu YAML; compileOnly because the
    // Paper library loader already provisions configurate-yaml at runtime (see UxmEssentialsLoader), the same
    // way the :migration module reads EssentialsX YAML.
    compileOnly(libs.configurate.yaml)
    // Caffeine is supplied at runtime by the plugin loader (see UxmEssentialsLoader); compileOnly here so the tablist
    // skin-fetch cache can bind to it without re-shading a library the loader already provides.
    compileOnly(libs.caffeine)
    // gson is likewise provisioned at runtime by the plugin loader; compileOnly so the npc Mojang skin fetch can parse
    // the two profile responses without re-shading a library already on the server classpath.
    compileOnly(libs.gson)
    implementation(libs.bstats.bukkit)

    // The Redis bus transport lives in the standalone uxmEssentials-redis companion *plugin*, not in the main
    // jar. The main never compiles against it: BusWiring names only the RedisTransportFactory SPI in :core and
    // resolves the companion's implementation through Bukkit's ServicesManager at runtime. The operator drops
    // the companion jar in plugins/ to enable the redis transport; BusWiring degrades to local-only when it is
    // absent. So there is no compile dependency on :redis-adapter here — that would re-introduce the duplicate
    // BusTransport class the ServicesManager design exists to avoid.

    // uxmLib GUI toolkit (dogfood) — consumed from mavenLocal; pulls uxmlib-item + uxmlib-common
    // transitively. Configurate is loaded at runtime via Paper library loader.
    implementation("com.uxplima.uxmlib:uxmlib-gui:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib Bedrock toolkit (dogfood) - the Floodgate/Geyser detector and the Cumulus form sender the menu
    // engine renders through for a Bedrock viewer. Floodgate stays compileOnly on this side too: the library
    // names the SDK only past its own plugin-present guard.
    implementation("com.uxplima.uxmlib:uxmlib-bedrock:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib item toolkit (dogfood) — the PdcFlag helper backing the per-player boolean-flag PDC stores.
    // Arrives transitively via uxmlib-gui, but declared directly so the PdcFlag use is explicit.
    implementation("com.uxplima.uxmlib:uxmlib-item:0.46.0") { isTransitive = false }
    // uxmLib HUD toolkit (dogfood) — Titles for the teleport arrival banner. Pulls uxmlib-common only.
    implementation("com.uxplima.uxmlib:uxmlib-hud:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib integration toolkit (dogfood) — native-Display holograms for the holograms context.
    implementation("com.uxplima.uxmlib:uxmlib-integration:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib packet-nametag toolkit (dogfood) — the per-viewer packet renderer the nametags context draws on. It is a
    // Mojang-mapped NMS module (rides along Mojang-mapped in the shaded jar, like the offline-inventory adapter), and
    // it sends through uxmlib-pipeline's channel sender.
    implementation("com.uxplima.uxmlib:uxmlib-nametags:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib packet-tablist toolkit (dogfood) — the per-viewer player-info packet builder the tablist context uses to
    // paint a custom skin on a tab row (the one tab thing native Paper cannot do). Same Mojang-mapped NMS module shape
    // as uxmlib-nametags; sends through uxmlib-pipeline's channel sender.
    implementation("com.uxplima.uxmlib:uxmlib-packet:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    implementation("com.uxplima.uxmlib:uxmlib-pipeline:0.46.0") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib update toolkit (dogfood) — the opt-in release update-checker. Pulls uxmlib-common only.
    implementation("com.uxplima.uxmlib:uxmlib-update:0.46.0") {
        exclude(group = "org.spongepowered")
    }

    testImplementation(libs.mockbukkit)
    testImplementation(libs.archunit.junit)
    testImplementation(libs.paper.api)
    testImplementation(libs.vault.api) // the migration live-feed tests stub a Vault Economy provider
    // Lands and GriefPrevention on the test classpath so the ownership tests can stub a real Area/Claim and
    // prove owner is distinct from trusted. Transitive-free: the SDK types only reference org.bukkit.*, which
    // paper-api already provides — pulling each plugin's own spigot-api would collide with it.
    testImplementation(libs.hook.lands) { isTransitive = false }
    testImplementation(libs.hook.griefprevention) { isTransitive = false }
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.bundles.configs)
    testImplementation(libs.bundles.db)
    testImplementation(libs.bundles.db.mysql)
    testImplementation(libs.bundles.db.pg)
    testImplementation(libs.h2) // in-process H2 fixture for the AxPlayerWarps importer round-trip
    testImplementation(libs.caffeine)
    testImplementation(libs.gson)
    testImplementation(libs.configurate.yaml)
}

// The Mojang-mapped dev bundle (declared above via paperDevBundle) is needed only to compile the
// offline-inventory NMS adapter. Keep it off the test classpath: MockBukkit drives the plugin against the plain
// Paper API, and the full server's PaperRegistryAccess static initializer throws if its classes leak onto the
// unit-test runtime. compileOnly alone is what the adapter needs — net.minecraft is provided by the live server,
// and Paper's runtime remapper maps the shipped Mojang-mapped jar at load (shadowJar manifest above).
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}

// Locale catalogs live in a dedicated source set so they have their own
// resources output and can drive the locale-parity gate (docs/04-build.md §8.1).
sourceSets {
    create("messages") {
        resources.srcDir("src/messages/resources") // messages_en.conf, messages_de.conf, ...
    }
    // JMH benchmarks live in their own source set so they never ship in the jar
    // and never run during normal `check` (docs/04-build.md §8.2).
    create("jmh") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

dependencies {
    "jmhImplementation"(libs.bundles.jmh)
    "jmhAnnotationProcessor"(libs.jmh.ap)
    // The player-warp browse benchmark drives the real jOOQ read-model against an embedded SQLite file, so the JMH
    // source set compiles and runs against core, the persistence adapter, and the default DB bundle.
    "jmhImplementation"(project(":core"))
    "jmhImplementation"(project(":persistence-adapter"))
    "jmhImplementation"(libs.bundles.db)
    // The developer-API benchmark measures the bridge and the veto gate, so it needs the published event classes
    // and Paper itself. Paper is a real dependency here rather than compileOnly: the benchmark runs outside a
    // server and still has to load HandlerList.
    "jmhImplementation"(project(":bukkit-api"))
    "jmhImplementation"(libs.paper.api)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    // paper-plugin.yml carries a single ${version} token. Substitute it with a line-based filter rather than expand():
    // expand() compiles the whole file into one Groovy template string, whose 65535-char literal ceiling the growing
    // permission block eventually blows, failing the copy. A per-line replace has no such limit and needs no template.
    filesMatching("paper-plugin.yml") { filter { line -> line.replace("\${version}", version) } }
    // Fold the message catalogs into the runtime jar. The messages source set has its own
    // resources output that is also on the runtime classpath, so the same catalog file can arrive
    // from both inputs; keep the folded copy.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(sourceSets["messages"].resources)
}

// Parity gate: every locale must declare exactly en's MessageKey set.
val localeParityCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Fail if any messages_<lang>.conf is missing or has extra keys vs messages_en.conf."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.i18n.LocaleParityCheck")
    // P9 ships LocaleParityCheck and activates the gate (the onlyIf self-skip is gone). The checker
    // needs the message catalogs and the test classes compiled, so depend on the test compile and the
    // folded resources.
    dependsOn(tasks.named("compileTestJava"), tasks.processResources)
}
tasks.named("check") { dependsOn(localeParityCheck) }

// Documentation export: the module pages on docs.uxplima.com take their command, permission, setting and
// placeholder tables from this file rather than from someone retyping them. Deliberately not wired into
// `check`: it writes a file and is run by hand when the docs are refreshed.
val docsExport by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Write build/docs/docs-data.json for tools/docs/generate.py."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.docs.DocsExport")
    dependsOn(tasks.named("compileTestJava"), tasks.processResources)
}

// JMH's annotation processor emits harness classes under …/jmh_generated/… that Thread.yield() in a spin loop
// and are not tagged @Generated, so Error Prone's ThreadPriorityCheck fires on them and the shared -Werror fatals
// it. Exclude only that generated path from Error Prone; the benchmark's own source stays fully checked.
tasks.named<JavaCompile>("compileJmhJava") {
    options.errorprone.excludedPaths.set(".*/jmh_generated/.*")
}

val jmh by tasks.registering(JavaExec::class) {
    group = "benchmark"
    description = "Run JMH micro-benchmarks (baltop ordering, rtp safe-search, teleport resolution)."
    classpath = sourceSets["jmh"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    // Persist results for the perf-regression CI job to diff against the baseline.
    args("-rf", "json", "-rff", "build/reports/jmh/result.json")
}

tasks.shadowJar {
    archiveBaseName.set("uxmEssentials")
    archiveClassifier.set("")
    // The plugin is compiled and shipped Mojang-mapped; this tells Paper's runtime plugin remapper to
    // map it to the running server's mappings at load. Without it the NMS calls in the offline-inventory
    // adapter would miss at runtime. shadowJar builds its own manifest, so the attribute is set here too.
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
    // Shade with relocation — see docs/04-build.md §16. Use a single per-plugin
    // namespace (`com.uxplima.uxmessentials.libs.<lib>`) so two plugins shading
    // the same library at different versions do not clash on the classpath. DO
    // NOT relocate Adventure / Kyori — Paper bundles them; relocating breaks
    // runtime symbol lookup.
    relocate("org.bstats", "com.uxplima.uxmessentials.libs.bstats")
    // uxmLib (dogfood) — relocate into our per-plugin namespace so two plugins shading it cannot clash.
    relocate("com.uxplima.uxmlib", "com.uxplima.uxmessentials.libs.uxmlib")
    // The Redis client is no longer bundled here: the bus's Redis transport ships in the separate
    // uxmEssentials-redis companion jar (Lettuce, relocated there), so the main jar carries no Redis client at
    // all and there is nothing to relocate.
    mergeServiceFiles()
    minimize {
        // bStats uses reflection / service loading the minimizer can't see.
        exclude(dependency("org.bstats:.*:.*"))
        // uxmLib uses reflection (Brigadier/registry/MiniMessage) + a GuiListener the minimizer can't
        // trace from the few entry points the adapter touches; keep its modules whole.
        exclude(dependency("com.uxplima.uxmlib:.*:.*"))
        // The persistence adapter is the API surface the feature contexts build on — the generated
        // jOOQ tables/records and the repository/transaction/cache bases must survive even before a
        // consuming context references them, so keep the whole module out of dead-code elimination.
        exclude(project(":persistence-adapter"))
        // The API modules are the published surface: their classes exist for consumers this jar's call graph
        // cannot see, so the minimizer would strip every one production code never calls and a third-party
        // plugin would meet NoClassDefFoundError at load. Keep both modules whole.
        exclude(project(":api"))
        exclude(project(":bukkit-api"))
    }
}

tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.runServer {
    minecraftVersion(
        // Paper's version string is "<mc>.build.<n>-<channel>" (e.g. 26.1.2.build.71-stable); run-paper
        // wants just the Minecraft version, so strip the ".build.*" build suffix.
        libs.versions.paper
            .get()
            .substringBefore(".build"),
    )
    jvmArgs(
        "-Xmx4G",
        "-Djdk.tracePinnedThreads=full",
    )
    // Live hot-swap of changed classes without a server restart needs the JetBrains Runtime
    // (DCEVM). On a stock JDK these options are unrecognised and abort the JVM at launch, so they
    // are opt-in: run with -Photswap under a JBR toolchain to enable them.
    if (providers.gradleProperty("hotswap").isPresent) {
        jvmArgs(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+AllowEnhancedClassRedefinition",
        )
    }
}
