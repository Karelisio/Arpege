package com.karelisio.arpege.data.backup

import android.net.Uri
import androidx.room.withTransaction
import com.karelisio.arpege.BuildConfig
import com.karelisio.arpege.data.AppContainer
import com.karelisio.arpege.reminder.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Import prêt à confirmer : la sauvegarde lue et ce qu'elle ajoutera. */
class PendingImport(val backup: Backup, val plan: MergePlan)

/** Export / import local via des URI SAF (aucune autorisation de stockage). */
class BackupRepository(private val c: AppContainer) {
    private val resolver get() = c.appContext.contentResolver

    private suspend fun current(withSettings: Boolean): Backup {
        val db = c.db
        return Backup(
            sessions = db.practice().all().first(),
            favorites = db.favorites().all().first(),
            stats = db.stats().everything(),
            tabs = db.tabs().all().first(),
            settings = if (withSettings) c.settings.exportPreferences() else emptyMap(),
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    /** Écrit toutes les données dans [uri] ; renvoie le contenu exporté. */
    suspend fun export(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val backup = current(withSettings = true)
        val out = resolver.openOutputStream(uri) ?: error("Fichier inaccessible")
        out.use { it.write(BackupJson.encode(backup).toByteArray()) }
        backup
    }

    /** Lit [uri] et calcule la fusion, sans rien modifier. */
    suspend fun prepare(uri: Uri): PendingImport = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw BackupFormatException("Fichier inaccessible")
        val backup = BackupJson.decode(text)
        PendingImport(backup, BackupMerge.plan(current(withSettings = false), backup))
    }

    /** Applique un import confirmé (base en une transaction, puis réglages). */
    suspend fun apply(p: PendingImport) = withContext(Dispatchers.IO) {
        val db = c.db
        // Recalcul sur l'état actuel, au cas où les données auraient changé depuis l'aperçu.
        val plan = BackupMerge.plan(current(withSettings = false), p.backup)
        db.withTransaction {
            plan.sessions.forEach { db.practice().insert(it) }
            plan.favorites.forEach { db.favorites().add(it) }
            plan.stats.forEach { db.stats().upsert(it) }
            plan.tabs.forEach { db.tabs().insert(it) }
        }
        if (p.backup.settings.isNotEmpty()) c.settings.importPreferences(p.backup.settings)
        Reminders.sync(c.appContext)
        c.onPracticeChanged()
        plan
    }
}
