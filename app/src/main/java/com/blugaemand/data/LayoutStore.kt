package com.blugaemand.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutLibrary
import com.blugaemand.input.decodeLayouts
import com.blugaemand.input.encodeLayouts
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.IOException

private val Context.layoutPreferences: DataStore<Preferences> by preferencesDataStore("layouts")

/**
 * Where the user's layouts and their choice of one live between launches.
 *
 * Preferences rather than a typed `DataStore<LayoutLibrary>`: the payload is already a single JSON
 * string from [encodeLayouts], so a `Serializer` would be ceremony wrapped around one that exists.
 *
 * This is the only file outside `hid` and `ui` that touches Android, which is deliberate — the
 * decisions worth testing live in [LayoutLibrary] and the JSON layer, both plain Kotlin.
 */
class LayoutStore(context: Context) {

    private val preferences = context.applicationContext.layoutPreferences

    /**
     * The user's layouts, or an empty library if nothing has been saved yet. The built-ins are
     * always available through [LayoutLibrary.all], so first run needs nothing written to it.
     */
    val library: Flow<LayoutLibrary> = preferences.readable().map { prefs ->
        val text = prefs[USER_LAYOUTS] ?: return@map LayoutLibrary()
        try {
            LayoutLibrary(decodeLayouts(text))
        } catch (e: SerializationException) {
            // Report an empty library, but leave the stored text exactly where it is. A layout the
            // app has stopped being able to parse is still the only copy of work someone did, and
            // overwriting it here would destroy it on the next save with no way back. A future
            // build, or a fixed one, can still read it.
            Log.w(TAG, "Stored layouts could not be read; leaving them untouched", e)
            LayoutLibrary()
        }
    }

    /**
     * Which layout the pad should be showing. Defaults to [DEFAULT_LAYOUT], which is what seeding a
     * default amounts to when the built-ins are compiled in — nothing is copied on first run.
     */
    val selectedId: Flow<String> = preferences.readable().map { prefs ->
        prefs[SELECTED_LAYOUT_ID] ?: DEFAULT_LAYOUT.id
    }

    suspend fun save(library: LayoutLibrary) {
        preferences.edit { it[USER_LAYOUTS] = encodeLayouts(library.user) }
    }

    suspend fun select(layout: GamepadLayout) {
        preferences.edit { it[SELECTED_LAYOUT_ID] = layout.id }
    }

    /**
     * Stores the library and the choice of layout **in one transaction**.
     *
     * Not a convenience over calling [save] and [select] in turn: each `edit` is its own transaction
     * and emits on its own, so in between the two the library has changed and the selection has not.
     * Anything reading both at once sees a moment where they disagree — and the guard that closes
     * the editor when the selected layout stops being editable read exactly that moment, closing the
     * editor the instant a layout was created from a built-in.
     */
    suspend fun saveAndSelect(library: LayoutLibrary, layout: GamepadLayout) {
        preferences.edit {
            it[USER_LAYOUTS] = encodeLayouts(library.user)
            it[SELECTED_LAYOUT_ID] = layout.id
        }
    }

    /**
     * The stored preferences, with a read failure treated as "nothing stored yet" rather than as a
     * crash. DataStore surfaces a corrupt or unreadable file as an [IOException] on the flow, and
     * taking the app down over it would make the pad unusable rather than merely forgetful.
     */
    private fun DataStore<Preferences>.readable(): Flow<Preferences> = data.catch { cause ->
        if (cause !is IOException) throw cause
        Log.w(TAG, "Could not read the layout store", cause)
        emit(emptyPreferences())
    }

    private companion object {
        const val TAG = "Blugaemand"
        val USER_LAYOUTS = stringPreferencesKey("user_layouts")
        val SELECTED_LAYOUT_ID = stringPreferencesKey("selected_layout_id")
    }
}
