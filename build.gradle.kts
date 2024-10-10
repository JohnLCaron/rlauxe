import org.gradle.kotlin.dsl.from
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23"
    alias(libs.plugins.serialization)
}

group = "org.cryptobiotic.rlauxe"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
//     maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")

//    maven {
//        url ("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    }
}