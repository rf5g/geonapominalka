package com.example.geonapominalka.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Централизованное хранилище настроек: тема, звук/вибрация уведомлений,
 * частота опроса местоположения в фоне (ручная и адаптивный режим).
 */
class SettingsRepository(private val context: Context) {

    object Keys {
        val THEME = stringPreferencesKey("theme") // "light" | "dark" | "system"
        val SOUND_URI = stringPreferencesKey("sound_uri")
        val VIBRATION = booleanPreferencesKey("vibration")
        val INTERVAL_SECONDS = intPreferencesKey("interval_seconds") // ручной интервал: 30/60/180/300/600/1200
        val ADAPTIVE_MODE = booleanPreferencesKey("adaptive_mode") // адаптивный режим опроса — по умолчанию включён
        val MAP_TYPE = intPreferencesKey("map_type") // индекс тайл-источника: 0 - OSM, 1 - спутник
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val soundUri: Flow<String?> = context.dataStore.data.map { it[Keys.SOUND_URI] }
    val vibration: Flow<Boolean> = context.dataStore.data.map { it[Keys.VIBRATION] ?: true }
    val intervalSeconds: Flow<Int> = context.dataStore.data.map { it[Keys.INTERVAL_SECONDS] ?: 60 }
    val adaptiveMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.ADAPTIVE_MODE] ?: true }
    val mapType: Flow<Int> = context.dataStore.data.map { it[Keys.MAP_TYPE] ?: 0 /* OSM Mapnik */ }

    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.THEME] = value }
    suspend fun setSoundUri(value: String?) = context.dataStore.edit {
        if (value == null) it.remove(Keys.SOUND_URI) else it[Keys.SOUND_URI] = value
    }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = value }
    suspend fun setIntervalSeconds(value: Int) = context.dataStore.edit { it[Keys.INTERVAL_SECONDS] = value }
    suspend fun setAdaptiveMode(value: Boolean) = context.dataStore.edit { it[Keys.ADAPTIVE_MODE] = value }
    suspend fun setMapType(value: Int) = context.dataStore.edit { it[Keys.MAP_TYPE] = value }

    suspend fun currentIntervalSeconds(): Int = intervalSeconds.first()
    suspend fun currentAdaptiveMode(): Boolean = adaptiveMode.first()
}
