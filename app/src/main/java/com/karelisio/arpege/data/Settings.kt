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
import com.karelisio.arpege.theory.dsp.MetronomeFavorite
import com.karelisio.arpege.theory.dsp.SpeedTrainer
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
    val metronomeFavorites: List<MetronomeFavorite> = emptyList(),
    val trainerOn: Boolean = false,
    val trainer: SpeedTrainer = SpeedTrainer(),
    val timerOn: Boolean = false,
    val timerMinutes: Int = 20,
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
        val favorites = stringPreferencesKey("metronome_favorites")
        val trainerOn = booleanPreferencesKey("metronome_trainer_on")
        val trainer = stringPreferencesKey("metronome_trainer")
        val timerOn = booleanPreferencesKey("metronome_timer_on")
        val timerMinutes = intPreferencesKey("metronome_timer_minutes")
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
        metronomeFavorites = MetronomeFavorite.decode(this[K.favorites]),
        trainerOn = this[K.trainerOn] ?: false,
        trainer = SpeedTrainer.decode(this[K.trainer]) ?: SpeedTrainer(),
        timerOn = this[K.timerOn] ?: false,
        timerMinutes = (this[K.timerMinutes] ?: 20).coerceIn(5, 60),
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun setNotation(v: Notation) = context.dataStore.edit { it[K.notation] = v.name }
    suspend fun setInstrument(v: Instrument) = context.dataStore.edit { it[K.instrument] = v.name }
    suspend fun setA4(v: Float) = context.dataStore.edit { it[K.a4] = v.coerceIn(432f, 446f) }
    suspend fun setWeeklyGoal(v: Int) = context.dataStore.edit { it[K.goal] = v.coerceIn(10, 3000) }
    suspend fun setVibrate(v: Boolean) = context.dataStore.edit { it[K.vibrate] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setMetronomeBpm(v: Int) = context.dataStore.edit { it[K.bpm] = v }
    /** Modifie les favoris de façon atomique (lecture et écriture dans la même transaction). */
    suspend fun updateMetronomeFavorites(f: (List<MetronomeFavorite>) -> List<MetronomeFavorite>) = context.dataStore.edit {
        it[K.favorites] = MetronomeFavorite.encode(f(MetronomeFavorite.decode(it[K.favorites])).take(MetronomeFavorite.MAX_COUNT))
    }
    suspend fun setTrainer(on: Boolean, v: SpeedTrainer) = context.dataStore.edit { it[K.trainerOn] = on; it[K.trainer] = v.encode() }
    suspend fun setTimer(on: Boolean, minutes: Int) = context.dataStore.edit { it[K.timerOn] = on; it[K.timerMinutes] = minutes.coerceIn(5, 60) }

    suspend fun lastUpdateCheck(): Long = context.dataStore.data.first()[K.lastUpdateCheck] ?: 0L
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = v }
}
