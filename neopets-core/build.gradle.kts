plugins {
    id("neopets.java-conventions")
}

dependencies {
    api(project(":neopets-api"))
    testImplementation(libs.archunit)
}
