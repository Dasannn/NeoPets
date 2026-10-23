import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("neopets.java-conventions")
}

repositories {
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

val bundledModules = configurations.create("bundledModules") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":neopets-platform-paper"))
    api(project(":neopets-storage"))
    compileOnly(libs.paper.api)
    compileOnly(libs.vault.api)
    testImplementation(libs.paper.api)
    testImplementation(libs.vault.api)

    // Internal modules (and, transitively, core and api) are merged into the plugin jar.
    add(bundledModules.name, project(":neopets-storage"))
    add(bundledModules.name, project(":neopets-platform-paper"))
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun libraryCoordinate(alias: String): String {
    val library = versionCatalog.findLibrary(alias).get().get()
    return "${library.module}:${library.versionConstraint.requiredVersion}"
}

val runtimeLibraryCoordinates = mapOf(
    "hikaricp" to libraryCoordinate("hikaricp"),
    "mysql-connector-j" to libraryCoordinate("mysql-connector-j"),
    "sqlite-jdbc" to libraryCoordinate("sqlite-jdbc"),
    "flyway-core" to libraryCoordinate("flyway"),
    "flyway-mysql" to libraryCoordinate("flyway-mysql"),
)

val expansionProperties = mapOf(
    "version" to project.version,
    "hikaricp" to runtimeLibraryCoordinates.getValue("hikaricp"),
    "mysqlConnectorJ" to runtimeLibraryCoordinates.getValue("mysql-connector-j"),
    "sqliteJdbc" to runtimeLibraryCoordinates.getValue("sqlite-jdbc"),
    "flywayCore" to runtimeLibraryCoordinates.getValue("flyway-core"),
    "flywayMysql" to runtimeLibraryCoordinates.getValue("flyway-mysql"),
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(expansionProperties)
    filesMatching(listOf("paper-plugin.yml", "runtime-libraries.properties")) {
        expand(expansionProperties)
    }
}

tasks.jar {
    dependsOn(bundledModules)
    archiveBaseName = "NeoPets"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Only project modules are bundled; external runtime libraries come from NeoPetsLoader.
    val moduleJars = bundledModules.incoming.artifactView {
        componentFilter { it is ProjectComponentIdentifier }
    }.files
    from({ moduleJars.map(::zipTree) })
}

tasks.named<Test>("test") {
    val pluginJar = tasks.jar.flatMap { it.archiveFile }
    dependsOn(tasks.jar)
    inputs.file(pluginJar)
    inputs.files(bundledModules)
    systemProperty("neopets.test.version", project.version.toString())
    runtimeLibraryCoordinates.forEach { (name, coordinate) ->
        systemProperty("neopets.test.library.$name", coordinate)
    }
    doFirst {
        systemProperty("neopets.test.jar", pluginJar.get().asFile.absolutePath)
        systemProperty(
            "neopets.test.bundledJars",
            bundledModules.files.joinToString(File.pathSeparator) { it.absolutePath },
        )
    }
}
