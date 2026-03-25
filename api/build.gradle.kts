plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

application {
    mainClass.set("org.motopartes.api.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.kotlinx)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}
