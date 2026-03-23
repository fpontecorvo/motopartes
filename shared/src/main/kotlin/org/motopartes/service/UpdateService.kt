package org.motopartes.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
data class VersionInfo(val version: String, val downloadUrl: String)

class UpdateService(private val currentVersion: String) {

    companion object {
        const val VERSION_URL = "https://raw.githubusercontent.com/fpontecorvo/motopartes/main/version.json"
        val APP_VERSION = org.motopartes.config.Version.NAME
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun checkForUpdate(): VersionInfo? {
        return try {
            val body = URI(VERSION_URL).toURL().readText()
            val remote = json.decodeFromString<VersionInfo>(body)
            if (isNewer(remote.version, currentVersion)) remote else null
        } catch (_: Exception) {
            null // Network error — silently ignore
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}
