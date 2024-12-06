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
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.lets.plot)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotest)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    minHeapSize = "512m"
    maxHeapSize = "8g"
    jvmArgs = listOf("-Xss128m")

    // Make tests run in parallel
    // More info: https://www.jvt.me/posts/2021/03/11/gradle-speed-parallel/
    // systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
    // systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // systemProperties["junit.jupiter.execution.parallel.mode.classes.default"] = "concurrent"
}

kotlin {
    jvmToolchain(21)
}