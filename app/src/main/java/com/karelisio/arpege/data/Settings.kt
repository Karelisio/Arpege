package com.karelisio.arpege.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.arpege.theory.Instrument
import com.karelisio.arpege.theory.Notation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val notation: Notation = Notation.LATIN,
    val instrument: Instrument = Instrument.GUITAR,
    val a4: Float = 440f,
    val weeklyGoalMinutes: Int = 150,
    val vibrateDownbeat: Boolean = false,
    val onboardingDone: Boolean = false,
    val metronomeBpm: Int = 100,
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
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun setNotation(v: Notation) = context.dataStore.edit { it[K.notation] = v.name }
    suspend fun setInstrument(v: Instrument) = context.dataStore.edit { it[K.instrument] = v.name }
    suspend fun setA4(v: Float) = context.dataStore.edit { it[K.a4] = v.coerceIn(432f, 446f) }
    suspend fun setWeeklyGoal(v: Int) = context.dataStore.edit { it[K.goal] = v.coerceIn(10, 3000) }
    suspend fun setVibrate(v: Boolean) = context.dataStore.edit { it[K.vibrate] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setMetronomeBpm(v: Int) = context.dataStore.edit { it[K.bpm] = v }

    suspend fun lastUpdateCheck(): Long = context.dataStore.data.first()[K.lastUpdateCheck] ?: 0L
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = v }
}
