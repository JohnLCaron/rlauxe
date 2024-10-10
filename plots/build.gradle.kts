plugins {
    kotlin("jvm")
    alias(libs.plugins.serialization)
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
}

dependencies {
    api(project(":core"))

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlin-statistics-jvm:0.3.1")
    implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.7.0")

    implementation(libs.lets.plot)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}