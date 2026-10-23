import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("neopets.java-conventions")
}

val bundledModules = configurations.create("bundledModules") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":neopets-platform-paper"))
    api(project(":neopets-storage"))
    compileOnly(libs.paper.api)

    // Internal modules (and, transitively, core and api) are merged into the plugin jar.
    add(bundledModules.name, project(":neopets-storage"))
    add(bundledModules.name, project(":neopets-platform-paper"))
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

tasks.named<ProcessResources>("processResources") {
    fun libraryCoordinate(alias: String): String {
        val library = versionCatalog.findLibrary(alias).get().get()
        return "${library.module}:${library.versionConstraint.requiredVersion}"
    }

    val expansionProperties = mapOf(
        "version" to project.version,
        "hikaricp" to libraryCoordinate("hikaricp"),
        "mysqlConnectorJ" to libraryCoordinate("mysql-connector-j"),
        "sqliteJdbc" to libraryCoordinate("sqlite-jdbc"),
        "flywayCore" to libraryCoordinate("flyway"),
        "flywayMysql" to libraryCoordinate("flyway-mysql"),
    )

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
