plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional Redis transport for the cross-server bus (docs/09-deployment.md). It is its own Paper plugin jar
// (uxmEssentials-redis), not a library: its RedisBusTransportAdapter implements the host jar's BusTransport, and
// that instance crosses into the host's bus core, so both sides must share the SAME BusTransport class. A library
// (or a jar shading its own core copy) would yield a loader-constraint LinkageError. So core and paper-api are
// compileOnly, never shaded, and the companion sees the host's copies at runtime through the join-classpath
// dependency declared in paper-plugin.yml. Only Lettuce (+ Netty/Reactor + uxmlib-redis) is shaded here.

dependencies {
    // The BusTransport SPI + the RedisTransportFactory this companion implements live in :core. compileOnly so
    // no second copy is shaded: at runtime the companion resolves them to the host jar's classes via the joined
    // classpath. Same reason paper-api is compileOnly, the host (and Paper) provide it.
    compileOnly(project(":core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)
    // The byte[] Redis pub/sub channel lives in uxmlib-redis (lean. No storage deps); this adapter provides
    // the Lettuce runtime it compiles against and ships it relocated below.
    implementation(libs.uxmlib.redis)
    implementation(libs.lettuce)

    testImplementation(project(":core"))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.tc.junit)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("uxmEssentials-redis")
    // Lettuce drags in Netty + Reactor. Relocate them so this companion jar never clashes with Paper's own
    // (differently-versioned) bundled Netty when it lands on a backend classpath. Lettuce itself stays at
    // io.lettuce. Shadow rewrites Lettuce's internal references to the relocated Netty/Reactor packages
    // automatically. Use the same per-plugin namespace the main jar uses.
    relocate("io.netty", "com.uxplima.uxmessentials.libs.netty")
    relocate("reactor", "com.uxplima.uxmessentials.libs.reactor")
    relocate("org.reactivestreams", "com.uxplima.uxmessentials.libs.reactivestreams")
    // uxmlib-redis (and its uxmlib-common transitive) is shaded. Relocate it to the same coordinates the main
    // jar uses so this companion never clashes on the classes with another plugin that bundles uxmlib.
    relocate("com.uxplima.uxmlib", "com.uxplima.uxmessentials.libs.uxmlib")
    // Netty ships native-transport metadata + ServiceLoader files that must be merged, not dropped, or the
    // relocated classes fail to resolve at runtime.
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// ShadowJarNettyRelocationTest inspects the built jar, so the jar must exist before the test task runs
// (during `check` as well as a bare `test`).
tasks.test { dependsOn(tasks.shadowJar) }
