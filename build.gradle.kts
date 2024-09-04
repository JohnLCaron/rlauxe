import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.20"
    alias(libs.plugins.serialization)
}

group = "org.cryptobiotic.rlauxe"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
//    maven {
//        url ("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    }
}

dependencies {
    implementation(libs.bull.result)
    implementation(libs.bundles.xmlutil)
    implementation(libs.ktor.serialization.kotlinx.json.jvm )
    implementation(libs.kotlinx.cli)
    implementation(libs.bundles.logging)

    testImplementation(kotlin("test"))
}

tasks.test  {
    useJUnitPlatform()
    minHeapSize = "512m"
    maxHeapSize = "8g"
    jvmArgs = listOf("-Xss128m")

    // Make tests run in parallel
    // More info: https://www.jvt.me/posts/2021/03/11/gradle-speed-parallel/
    systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    systemProperties["junit.jupiter.execution.parallel.mode.classes.default"] = "concurrent"
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}