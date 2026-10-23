plugins {
    id("neopets.java-conventions")
}

dependencies {
    api(project(":neopets-core"))

    compileOnly(libs.hikaricp)
    compileOnly(libs.mysql.connector.j)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.flyway)
    compileOnly(libs.flyway.mysql)

    testImplementation(libs.archunit)
    testImplementation(libs.hikaricp)
    testImplementation(libs.mysql.connector.j)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.flyway)
    testImplementation(libs.flyway.mysql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
