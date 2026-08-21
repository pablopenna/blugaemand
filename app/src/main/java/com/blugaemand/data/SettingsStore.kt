package com.blugaemand.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blugaemand.motion.MotionSettings
import com.blugaemand.motion.MotionTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsPreferences: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * Settings that are about the app and the phone rather than about a layout — so far, motion aiming.
 *
 * A file of its own beside [LayoutStore] rather than more keys in that one: DataStore allows a
 * single instance per file, so two delegates over one name is a crash, and these have nothing to do
 * with the layout library besides both being remembered. Stored as loose keys rather than JSON,
 * since unlike a layout none of this is ever shared or hand-edited.
 */
class SettingsStore(context: Context) {

    private val preferences = context.applicationContext.settingsPreferences

    val motion: Flow<MotionSettings> = preferences.readable().map { prefs ->
        MotionSettings(
            enabled = prefs[MOTION_ENABLED] ?: false,
            // An unknown name reads as the default rather than throwing: this is a preference, and
            // a phone that cannot open its own menu because of one is the worse failure.
            target = prefs[MOTION_TARGET]
                ?.let { name -> MotionTarget.entries.firstOrNull { it.name == name } }
                ?: MotionTarget.RIGHT_STICK,
            sensitivity = prefs[MOTION_SENSITIVITY] ?: 1f,
            invertY = prefs[MOTION_INVERT_Y] ?: false,
        )
    }

    suspend fun saveMotion(settings: MotionSettings) {
        preferences.edit {
            it[MOTION_ENABLED] = settings.enabled
            it[MOTION_TARGET] = settings.target.name
            it[MOTION_SENSITIVITY] = settings.sensitivity
            it[MOTION_INVERT_Y] = settings.invertY
        }
    }

    /** Same bargain as [LayoutStore]: an unreadable file means "nothing stored", not a crash. */
    private fun DataStore<Preferences>.readable(): Flow<Preferences> = data.catch { cause ->
        if (cause !is IOException) throw cause
        Log.w(TAG, "Could not read the settings store", cause)
        emit(emptyPreferences())
    }

    private companion object {
        const val TAG = "Blugaemand"
        val MOTION_ENABLED = booleanPreferencesKey("motion_enabled")
        val MOTION_TARGET = stringPreferencesKey("motion_target")
        val MOTION_SENSITIVITY = floatPreferencesKey("motion_sensitivity")
        val MOTION_INVERT_Y = booleanPreferencesKey("motion_invert_y")
    }
}
