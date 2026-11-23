package com.karelisio.arpege.data.backup

import com.karelisio.arpege.data.db.ExerciseStat
import com.karelisio.arpege.data.db.FavoriteChord
import com.karelisio.arpege.data.db.PracticeSession
import com.karelisio.arpege.data.db.TabFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Contenu d'une sauvegarde locale (fichier JSON choisi par l'utilisateur via SAF). */
data class Backup(
    val sessions: List<PracticeSession> = emptyList(),
    val favorites: List<FavoriteChord> = emptyList(),
    val stats: List<ExerciseStat> = emptyList(),
    val tabs: List<TabFile> = emptyList(),
    /** Préférences DataStore : nom → Boolean, Int, Long, Float, Double, String ou Set<String>. */
    val settings: Map<String, Any> = emptyMap(),
    val exportedAt: Long = 0,
    val appVersion: String = "",
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Sérialisation JSON (org.json, fourni par Android) ; format versionné. */
object BackupJson {
    const val APP = "arpege"
    const val FORMAT = 1

    fun encode(b: Backup): String = JSONObject().apply {
        put("app", APP)
        put("format", FORMAT)
        put("appVersion", b.appVersion)
        put("exportedAt", b.exportedAt)
        put("sessions", JSONArray(b.sessions.map { s ->
            JSONObject()
                .put("start", s.startEpochMs).put("durationSec", s.durationSec).put("instrument", s.instrument)
                .put("activity", s.activity).put("note", s.note).put("auto", s.auto)
        }))
        put("favorites", JSONArray(b.favorites.map { f ->
            JSONObject().put("name", f.name).put("instrument", f.instrument).put("addedAt", f.addedAt)
        }))
        put("stats", JSONArray(b.stats.map { s ->
            JSONObject()
                .put("exercise", s.exercise).put("item", s.item).put("correct", s.correct)
                .put("attempts", s.attempts).put("lastSeen", s.lastSeen)
        }))
        put("tabs", JSONArray(b.tabs.map { t ->
            JSONObject()
                .put("title", t.title).put("artist", t.artist).put("format", t.format).put("content", t.content)
                .put("sourceName", t.sourceName).put("importedAt", t.importedAt).put("transpose", t.transpose)
                .put("scrollSpeed", t.scrollSpeed.toDouble())
        }))
        put("settings", JSONArray(b.settings.mapNotNull { (k, v) -> encodePref(k, v) }))
    }.toString()

    private fun encodePref(key: String, v: Any): JSONObject? {
        val o = JSONObject().put("key", key)
        when (v) {
            is Boolean -> o.put("type", "boolean").put("value", v)
            is Int -> o.put("type", "int").put("value", v)
            is Long -> o.put("type", "long").put("value", v)
            is Float -> o.put("type", "float").put("value", v.toDouble())
            is Double -> o.put("type", "double").put("value", v)
            is String -> o.put("type", "string").put("value", v)
            is Set<*> -> o.put("type", "stringSet").put("value", JSONArray(v.filterIsInstance<String>()))
            else -> return null
        }
        return o
    }

    /** Lit une sauvegarde ; [BackupFormatException] si le fichier n'en est pas une. */
    fun decode(text: String): Backup {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("Fichier JSON illisible", e)
        }
        if (root.optString("app") != APP) throw BackupFormatException("Ce fichier n'est pas une sauvegarde Arpège")
        if (root.optInt("format", 0) > FORMAT) throw BackupFormatException("Sauvegarde créée par une version plus récente d'Arpège")
        return try {
            Backup(
                sessions = root.objects("sessions").map { o ->
                    PracticeSession(
                        startEpochMs = o.getLong("start"), durationSec = o.getInt("durationSec"),
                        instrument = o.getString("instrument"), activity = o.getString("activity"),
                        note = o.optString("note"), auto = o.optBoolean("auto", true),
                    )
                },
                favorites = root.objects("favorites").map { o ->
                    FavoriteChord(name = o.getString("name"), instrument = o.getString("instrument"), addedAt = o.optLong("addedAt"))
                },
                stats = root.objects("stats").map { o ->
                    ExerciseStat(o.getString("exercise"), o.getString("item"), o.optInt("correct"), o.optInt("attempts"), o.optLong("lastSeen"))
                },
                tabs = root.objects("tabs").map { o ->
                    TabFile(
                        title = o.getString("title"), artist = o.optString("artist"), format = o.getString("format"),
                        content = o.getString("content"), sourceName = o.optString("sourceName"),
                        importedAt = o.optLong("importedAt"), transpose = o.optInt("transpose"),
                        scrollSpeed = o.optDouble("scrollSpeed", 1.0).toFloat(),
                    )
                },
                settings = root.objects("settings").mapNotNull { decodePref(it) }.toMap(),
                exportedAt = root.optLong("exportedAt"),
                appVersion = root.optString("appVersion"),
            )
        } catch (e: JSONException) {
            throw BackupFormatException("Sauvegarde incomplète ou corrompue", e)
        }
    }

    private fun decodePref(o: JSONObject): Pair<String, Any>? {
        val key = o.optString("key").ifEmpty { return null }
        val value: Any = when (o.optString("type")) {
            "boolean" -> o.getBoolean("value")
            "int" -> o.getInt("value")
            "long" -> o.getLong("value")
            "float" -> o.getDouble("value").toFloat()
            "double" -> o.getDouble("value")
            "string" -> o.getString("value")
            "stringSet" -> o.getJSONArray("value").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            else -> return null
        }
        return key to value
    }

    private fun JSONObject.objects(name: String): List<JSONObject> {
        val a = optJSONArray(name) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }
}
