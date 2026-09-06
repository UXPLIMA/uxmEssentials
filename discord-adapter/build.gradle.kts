plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional Discord bridge (docs/09-deployment.md Path C). It is its own Paper
// plugin jar (uxmEssentials-discord) that consumes the host plugin's ports/events
// through Bukkit's ServicesManager: there is no compile-time link to :bukkit-adapter.
// JDA is the only backend. It is not shaded into the jar: the thin plugin jar declares
// JDA compileOnly and UxmDiscordLoader downloads it from Maven Central at boot via the
// paper-plugin.yml loader directive. opus-java is dropped at the loader level because the
// bridge only posts text and never touches voice.

dependencies {
    implementation(project(":core"))
    api(project(":api"))
    compileOnly(libs.paper.api)

    compileOnly(libs.jda)
    compileOnly(libs.bundles.configs)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.paper.api)
    testImplementation(libs.jda)
    testImplementation(libs.bundles.configs)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set("uxmEssentials-discord")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }
