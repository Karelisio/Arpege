package com.karelisio.arpege.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Notation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** Préférences internes, jamais exportées ni importées. */
private val NOT_EXPORTED = setOf("last_update_check", "onboarding_done")

data class AppSettings(
    val notation: Notation = Notation.LATIN,
    val instrument: Instrument = Instrument.GUITAR,
    val a4: Float = 440f,
    val weeklyGoalMinutes: Int = 150,
    val vibrateDownbeat: Boolean = false,
    val onboardingDone: Boolean = false,
    val metronomeBpm: Int = 100,
    val tunerStrobe: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 19 * 60,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val notation = stringPreferencesKey("notation")
        val instrument = stringPreferencesKey("instrument")
        val a4 = floatPreferencesKey("a4")
        val goal = intPreferencesKey("weekly_goal")
        val vibrate = booleanPreferencesKey("vibrate_downbeat")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val bpm = intPreferencesKey("metronome_bpm")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val tunerStrobe = booleanPreferencesKey("tuner_strobe")
        val reminder = booleanPreferencesKey("reminder_enabled")
        val reminderMinutes = intPreferencesKey("reminder_minutes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        notation = this[K.notation]?.let { runCatching { Notation.valueOf(it) }.getOrNull() } ?: Notation.LATIN,
        instrument = this[K.instrument]?.let { runCatching { Instrument.valueOf(it) }.getOrNull() } ?: Instrument.GUITAR,
        a4 = this[K.a4] ?: 440f,
        weeklyGoalMinutes = this[K.goal] ?: 150,
        vibrateDownbeat = this[K.vibrate] ?: false,
        onboardingDone = this[K.onboarding] ?: false,
        metronomeBpm = this[K.bpm] ?: 100,
        tunerStrobe = this[K.tunerStrobe] ?: false,
        reminderEnabled = this[K.reminder] ?: false,
        reminderMinutes = this[K.reminderMinutes] ?: (19 * 60),
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun setNotation(v: Notation) = context.dataStore.edit { it[K.notation] = v.name }
    suspend fun setInstrument(v: Instrument) = context.dataStore.edit { it[K.instrument] = v.name }
    suspend fun setA4(v: Float) = context.dataStore.edit { it[K.a4] = v.coerceIn(432f, 446f) }
    suspend fun setWeeklyGoal(v: Int) = context.dataStore.edit { it[K.goal] = v.coerceIn(10, 3000) }
    suspend fun setVibrate(v: Boolean) = context.dataStore.edit { it[K.vibrate] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setMetronomeBpm(v: Int) = context.dataStore.edit { it[K.bpm] = v }

    suspend fun setTunerStrobe(v: Boolean) = context.dataStore.edit { it[K.tunerStrobe] = v }
    suspend fun setReminder(enabled: Boolean, minuteOfDay: Int) = context.dataStore.edit {
        it[K.reminder] = enabled
        it[K.reminderMinutes] = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    }

    /** Réglages exportables (sauvegarde) : toutes les préférences sauf l'état interne. */
    suspend fun exportPreferences(): Map<String, Any> =
        context.dataStore.data.first().asMap().mapKeys { it.key.name }.filterKeys { it !in NOT_EXPORTED }

    /** Restaure des préférences exportées (types reconnus, et identiques à l'existant s'il y en a). */
    suspend fun importPreferences(values: Map<String, Any>) = context.dataStore.edit { prefs ->
        val existing = prefs.asMap().mapKeys { it.key.name }
        values.filterKeys { it !in NOT_EXPORTED }.forEach { (name, v) ->
            val old = existing[name]
            if (old != null && old::class != v::class && !(old is Set<*> && v is Set<*>)) return@forEach
            when (v) {
                is Boolean -> prefs[booleanPreferencesKey(name)] = v
                is Int -> prefs[intPreferencesKey(name)] = v
                is Long -> prefs[longPreferencesKey(name)] = v
                is Float -> prefs[floatPreferencesKey(name)] = v
                is Double -> prefs[doublePreferencesKey(name)] = v
                is String -> prefs[stringPreferencesKey(name)] = v
                is Set<*> -> prefs[stringSetPreferencesKey(name)] = v.filterIsInstance<String>().toSet()
            }
        }
    }

    suspend fun lastUpdateCheck(): Long = context.dataStore.data.first()[K.lastUpdateCheck] ?: 0L
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = v }
}
