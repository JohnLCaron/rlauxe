// copied from kobweb
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    `kotlin-dsl` apply false
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