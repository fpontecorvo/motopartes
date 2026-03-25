package org.motopartes.mobile.api

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "motopartes_settings")

object SettingsKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val API_KEY = stringPreferencesKey("api_key")
}

suspend fun Context.getServerUrl(): String =
    dataStore.data.map { it[SettingsKeys.SERVER_URL] ?: "" }.first()

suspend fun Context.getApiKey(): String =
    dataStore.data.map { it[SettingsKeys.API_KEY] ?: "" }.first()

suspend fun Context.saveConnection(url: String, key: String) {
    dataStore.edit {
        it[SettingsKeys.SERVER_URL] = url.trimEnd('/')
        it[SettingsKeys.API_KEY] = key
    }
}
