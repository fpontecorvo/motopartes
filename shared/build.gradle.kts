plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.kotlinx)
    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("seed") {
    mainClass.set("org.motopartes.seed.SeedDataKt")
    classpath = sourceSets["main"].runtimeClasspath
}
