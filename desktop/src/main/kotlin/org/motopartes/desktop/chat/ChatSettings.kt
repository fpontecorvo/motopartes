package org.motopartes.desktop.chat

import org.motopartes.config.AppPaths
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object ChatSettings {

    private val file = AppPaths.dataDir().resolve("chat.properties")
    private val props = Properties()

    init {
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
    }

    private fun save() {
        file.parent.toFile().mkdirs()
        file.outputStream().use { props.store(it, null) }
    }

    var apiKey: String
        get() = props.getProperty("api_key", "")
        set(value) { props.setProperty("api_key", value); save() }

    var provider: String
        get() = props.getProperty("provider", "google")
        set(value) { props.setProperty("provider", value); save() }

    var model: String
        get() = props.getProperty("model", "gemini-2.5-flash")
        set(value) { props.setProperty("model", value); save() }
}