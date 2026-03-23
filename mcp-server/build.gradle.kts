plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

application {
    mainClass.set("org.motopartes.mcp.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.bundles.kotlinx)
    implementation(libs.mcp.kotlin.sdk)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
}
