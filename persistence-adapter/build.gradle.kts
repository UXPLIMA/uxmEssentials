import org.jooq.meta.jaxb.Logging

plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.jooq.codegen)
}

// jOOQ generates its classes by parsing the Flyway migrations through the
// DDLDatabase: no live database is contacted at build time. It reads every
// V*.sql under db/migration in version order, so the generated sources describe
// the same tables the migrations create at runtime and the typed DSL can never
// drift from the actual schema. SQLite is the default backend; the DDLDatabase
// parses against the portable subset MySQL/MariaDB and PostgreSQL also accept.

dependencies {
    implementation(project(":core"))
    api(project(":api"))

    compileOnly(libs.bundles.db) // Hikari + SQLite + Flyway + jOOQ (default backend)
    compileOnly(libs.bundles.db.mysql) // MySQL/MariaDB driver. Activated via modules.conf
    compileOnly(libs.bundles.db.pg) // PostgreSQL driver. Activated via modules.conf
    compileOnly(libs.caffeine)
    compileOnly(libs.gson)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.bundles.db)
    testImplementation(libs.bundles.db.mysql)
    testImplementation(libs.bundles.db.pg)
    testImplementation(libs.caffeine)
    testImplementation(libs.gson)

    // The code generator parses the DDL through jOOQ's meta-extensions DDLDatabase.
    // It is needed only on the codegen classpath, never at runtime.
    jooqCodegen(libs.jooq.meta.ext)

    testImplementation(libs.tc.junit)
    testImplementation(libs.tc.postgres) // network-backend integration tests
    testImplementation(libs.tc.mysql) // network-backend integration tests
    // SQLite needs no Testcontainer: the embedded file db runs in-process.
}

jooq {
    configuration {
        logging = Logging.WARN
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    property {
                        key = "scripts"
                        value = "src/main/resources/db/migration/*.sql"
                    }
                    // Parse the scripts the way Flyway lays them out: numbered files
                    // applied in version order, semicolon-delimited statements. The glob
                    // picks up every V*.sql, so a new context's migration regenerates its
                    // tables without touching this build.
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    // Force lowercase generated identifiers. The migrations declare tables/columns
                    // unquoted in lowercase, but the jOOQ DDL parser folds unquoted names to UPPER
                    // which `as_is` then preserves. SQLite (default) and case-insensitive MySQL images
                    // tolerate the mismatch, but a real case-sensitive MySQL/Linux (lower_case_table_names=0)
                    // has lowercase tables and rejects the uppercased names ("table doesn't exist").
                    // Lowercasing the generated names matches the migrations on every backend.
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
            }
            generate {
                isRecords = true
                isFluentSetters = true
                isJavaTimeTypes = true
            }
            target {
                packageName = "com.uxplima.uxmessentials.persistence.jooq"
                directory =
                    layout.buildDirectory
                        .dir("generated-src/jooq/main")
                        .get()
                        .asFile.path
            }
        }
    }
}

// Make the generated sources part of the main compilation and order codegen first.
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/jooq/main"))
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named("jooqCodegen"))
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(tasks.named("jooqCodegen"))
    // The generated jOOQ directory is both a declared source dir and a build-output path, so the same
    // generated file can reach the sources jar twice; keep the first and drop the duplicate.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// The generated jOOQ classes are not hand-written: keep them out of Spotless so
// the formatter does not fight the generator. The convention plugin already
// disables Error Prone warnings in generated code; this excludes the generated
// directory from the formatter too.
spotless {
    java {
        targetExclude("build/generated-src/**")
    }
}
