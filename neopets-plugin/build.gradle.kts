plugins {
    id("neopets.java-conventions")
}

dependencies {
    api(project(":neopets-platform-paper"))
    api(project(":neopets-storage"))
    compileOnly(libs.paper.api)
}
