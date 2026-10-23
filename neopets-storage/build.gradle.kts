plugins {
    id("neopets.java-conventions")
}

dependencies {
    api(project(":neopets-core"))
    testImplementation(libs.archunit)
}
