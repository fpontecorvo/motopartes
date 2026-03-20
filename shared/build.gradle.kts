plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
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
