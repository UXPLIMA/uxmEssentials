import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    id("java-library")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
    withSourcesJar()
}

dependencies {
    "compileOnly"(libs.jspecify)
    "testCompileOnly"(libs.jspecify)
    // Error Prone's own annotations, so a type can declare itself immutable where the checker cannot see it.
    // compileOnly: they are @Retention(CLASS) markers the compiler reads and nothing needs at runtime.
    "compileOnly"(libs.errorprone.annotations)
    "testCompileOnly"(libs.errorprone.annotations)
    "errorprone"(libs.errorprone.core)
    "errorprone"(libs.nullaway)

    "testImplementation"(platform(libs.junit.bom))
    "testImplementation"(libs.bundles.testing)
    // Gradle 9 no longer bundles junit-platform-launcher in the test runtime; declare it explicitly.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-processing",
        "-Xlint:-serial",
        // JDK 23+ pedantic lint: flags /** */ comments used as section markers (not attached to a
        // declaration). Stylistic, not a correctness signal: off, like -serial/-processing above.
        "-Xlint:-dangling-doc-comments",
        "-Werror",
        "-parameters"
    ))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        // New default check in Error Prone 2.50 (the JDK 21+ unnamed-variable feature). It would rewrite
        // the project's deliberate descriptive unused-catch-variable names (e.g. `catch (… badName)`) to
        // `_`, contradicting the established readability convention, so it stays off.
        disable("UnnamedVariable")
        // Other purely-stylistic checks newly enabled-by-default in Error Prone 2.50; they fire on
        // existing, correct code (arrow-switch suggestions, etc.). Kept off to keep the 2.37→2.50 bump a
        // JDK-25 enabler rather than a codebase-wide restyle. Correctness checks remain on.
        disable("RefactorSwitch")
        // Fires on the codebase's deliberate identity comparisons (Bukkit Player/World handles, lock
        // stripes) where `==` is correct and `.equals` is meaningless; newly enabled in 2.50, off here.
        disable("ReferenceEquality")
        // Javadoc-only check newly enabled in 2.50; a handful of pre-existing stale {@link} targets trip it.
        // Off to keep the bump scoped to JDK 25 / Paper 26; the broken links are a separate doc-pass follow-up.
        disable("InvalidLink")
        // Style check newly enabled in 2.50. Flags public/protected members that are effectively private
        // (mostly test helpers). Stylistic, off to keep the bump scoped.
        disable("EffectivelyPrivate")
    }
}

extensions.configure<net.ltgt.gradle.nullaway.NullAwayExtension> {
    onlyNullMarked.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.nullaway {
        // CheckSeverity is reused from the errorprone plugin, the nullaway plugin
        // does not define its own enum.
        severity.set(net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        palantirJavaFormat(libs.versions.palantir.fmt.get())
        removeUnusedImports()
        formatAnnotations()
        importOrder("java", "javax", "org.bukkit", "io.papermc", "net.kyori", "")
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
    kotlinGradle { ktlint("1.5.0") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    // Pinned, because an unset max heap is a quarter of the host's RAM: the same suite got ~5 GB on a
    // workstation and ~1.7 GB on a 7 GB CI runner, where the parallel MockBukkit servers then ran out of
    // heap. With a fixed ceiling every machine runs the test JVM the same way, and one runner-sized value
    // is what the build is verified against.
    maxHeapSize = "2g"
}
