package com.zacco.rytm.data.profile

import android.content.Context
import com.zacco.rytm.data.db.RytmDatabase
import com.zacco.rytm.data.sync.HealthConnectSync
import com.zacco.rytm.domain.profile.PersonalProfile
import com.zacco.rytm.domain.profile.ProfilePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Profil osoby zapisany lokalnie na telefonie (każdy użytkownik ma swój telefon i swoje dane).
 * Null: profilu jeszcze nie ma, więc aplikacja pokazuje kreator.
 */
class ProfileStore(context: Context, private val db: RytmDatabase) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(read())

    /** Gotowe po [ensureLoaded]; wcześniej null może też znaczyć „jeszcze nie sprawdzono”. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val profile: StateFlow<PersonalProfile?> = state.asStateFlow()

    fun current(): PersonalProfile? = state.value

    fun save(p: PersonalProfile) {
        prefs.edit().putString(KEY, json.encodeToString(PersonalProfile.serializer(), p)).apply()
        state.value = p
    }

    /**
     * Aktualizacja z wersji bez profilu: jeśli aplikacja już synchronizowała dane, to jest dotychczasowy użytkownik,
     * więc wgrywamy jego ustawienia zamiast pokazywać kreator.
     */
    suspend fun ensureLoaded() {
        // Sprawdzamy tylko raz: u nowej osoby synchronizacja może ruszyć przed końcem kreatora.
        if (!prefs.getBoolean(KEY_CHECKED, false)) {
            if (state.value == null && db.syncState().get(HealthConnectSync.KEY_LAST_SYNC) != null) {
                save(ProfilePresets.existingInstall)
            }
            prefs.edit().putBoolean(KEY_CHECKED, true).apply()
        }
        _ready.value = true
    }

    private fun read(): PersonalProfile? = prefs.getString(KEY, null)?.let {
        runCatching { json.decodeFromString(PersonalProfile.serializer(), it) }.getOrNull()
    }

    private companion object {
        const val PREFS = "profile"
        const val KEY = "personal"
        const val KEY_CHECKED = "existing-install-checked"
    }
}
