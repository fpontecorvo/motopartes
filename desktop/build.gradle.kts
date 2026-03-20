plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxDatetime)
    implementation(libs.openpdf)
    implementation(libs.koog.agents)
    implementation(project(":api"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
}

compose.desktop {
    application {
        mainClass = "org.motopartes.desktop.MainKt"

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        buildTypes.release.proguard { isEnabled = false }

        nativeDistributions {
            modules("java.sql", "java.naming", "jdk.unsupported")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )

            packageName = "Motopartes"
            packageVersion = "1.0.0"
            description = "Sistema de gestion de venta minorista de motopartes"
            vendor = "Motopartes"

            macOS {
                bundleID = "org.motopartes.desktop"
            }

            windows {
                menuGroup = "Motopartes"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            linux {
                packageName = "motopartes"
            }
        }
    }
}
