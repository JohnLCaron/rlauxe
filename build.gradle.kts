// copied from kobweb
plugins {
    kotlin("jvm") version "2.3.10" apply false
    // alias(libs.plugins.kotlin.jvm) apply false
    // `kotlin-dsl` apply false
    alias(libs.plugins.kotlinx.serialization) apply false
}

group = "org.cryptobiotic.rlauxe"
version = libs.versions.rlauxe.get()

subprojects {
    repositories {
        mavenCentral()
        google()
    }
}