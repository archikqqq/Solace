package com.v2nox.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException

private val Context.appearancePreferences by preferencesDataStore(name = "appearance")

internal data class AppearanceSettings(
    val followSystem: Boolean = true,
    val darkTheme: Boolean = false,
    val dynamicColor: Boolean = true,
)

/** UI-only preferences, deliberately separate from server/VPN configuration. */
internal class AppearanceRepository(context: Context) {
    private val dataStore = context.applicationContext.appearancePreferences
    val settings = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        AppearanceSettings(
            followSystem = preferences[FOLLOW_SYSTEM] ?: true,
            darkTheme = preferences[DARK_THEME] ?: false,
            dynamicColor = preferences[DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun setFollowSystem(value: Boolean) { dataStore.edit { it[FOLLOW_SYSTEM] = value } }
    suspend fun setDarkTheme(value: Boolean) { dataStore.edit { it[DARK_THEME] = value } }
    suspend fun setDynamicColor(value: Boolean) { dataStore.edit { it[DYNAMIC_COLOR] = value } }

    private companion object {
        val FOLLOW_SYSTEM = booleanPreferencesKey("follow_system")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
