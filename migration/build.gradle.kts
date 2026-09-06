plugins {
    id("uxmessentials.java-conventions")
}

// The migration adapter is the seventh adapter module. It reads a foreign plugin's
// data tree (EssentialsX YAML for the v1 source), maps it through an Anti-Corruption
// Layer into the existing domain aggregates, and writes through the same application
// services live commands use. It depends on :core (the domain aggregates and the
// shared ports it drives) and :api (the published contracts); the Configurate YAML
// loader (which transitively pulls SnakeYAML) is a migration-only dependency and
// never reaches :core's classpath, exactly as the Bukkit fence keeps :core free of
// org.bukkit. Foreign-format parsing lives strictly here, never in :core.

dependencies {
    implementation(project(":core"))
    api(project(":api"))

    compileOnly(libs.configurate.yaml) // EssentialsX userdata/warps/kits YAML, adapter-only
    compileOnly(libs.slf4j.api)
    // The LiteBans JDBC source reads through java.sql only; the H2 driver is needed at runtime (the
    // import runs on the bukkit-adapter, which provides it via the Paper library loader) and in the
    // in-process fixture below, never to compile this module.
    compileOnly(libs.h2)

    testImplementation(libs.jqwik)
    testImplementation(libs.configurate.yaml)
    testImplementation(libs.configurate.hocon) // config-version ladder fixtures
    testImplementation(libs.h2) // in-process H2 fixture for the LiteBans reader round-trip
    testImplementation(libs.sqlite.jdbc) // in-process SQLite fixture for the Olzie PlayerWarps reader round-trip
}
