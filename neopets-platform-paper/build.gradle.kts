plugins {
    id("neopets.java-conventions")
}

dependencies {
    api(project(":neopets-core"))
    compileOnly(libs.paper.api)
}
