package com.karelisio.arpege.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.data.backup.BackupFormatException
import com.karelisio.arpege.data.backup.BackupRepository
import com.karelisio.arpege.data.backup.MergePlan
import com.karelisio.arpege.data.backup.PendingImport
import com.karelisio.arpege.data.container
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Export / import d'une sauvegarde JSON via le sélecteur de fichiers (SAF). */
@Composable
fun BackupSettings(snackbar: SnackbarHostState) {
    val c = LocalContext.current.container
    val repo = remember(c) { BackupRepository(c) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingImport?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val r = runCatching { repo.export(uri) }
            busy = false
            snackbar.showSnackbar(
                r.fold(
                    { b -> "Sauvegarde enregistrée : ${b.sessions.size} séances, ${b.tabs.size} tablatures" },
                    { "Échec de l'export" },
                ),
            )
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val r = runCatching { repo.prepare(uri) }
            busy = false
            r.onSuccess { pending = it }
                .onFailure { e -> snackbar.showSnackbar((e as? BackupFormatException)?.message ?: "Fichier illisible") }
        }
    }

    Card(shape = MaterialTheme.shapes.large) {
        ListItem(
            modifier = Modifier.clickable(enabled = !busy) { exporter.launch("arpege-sauvegarde-${LocalDate.now()}.json") },
            headlineContent = { Text("Exporter mes données") },
            supportingContent = { Text("Séances, favoris, stats, tablatures et réglages dans un fichier JSON") },
            leadingContent = { Icon(Icons.Rounded.Upload, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            modifier = Modifier.clickable(enabled = !busy) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            headlineContent = { Text("Importer une sauvegarde") },
            supportingContent = { Text("Fusion avec tes données, sans doublons") },
            leadingContent = { Icon(Icons.Rounded.Download, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.Download, null) },
            title = { Text("Importer cette sauvegarde ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.backup.exportedAt > 0) Text("Sauvegarde du ${formatDate(p.backup.exportedAt)}.")
                    Text(if (p.plan.isEmpty) "Rien de nouveau : toutes ces données sont déjà présentes." else "À ajouter : " + summary(p.plan) + ".")
                    if (p.backup.settings.isNotEmpty()) Text("Tes réglages seront remplacés par ceux de la sauvegarde.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = true
                        val r = runCatching { withContext(NonCancellable) { repo.apply(p) } }
                        busy = false
                        snackbar.showSnackbar(r.fold({ "Import terminé" + if (it.isEmpty) "" else " : " + summary(it) }, { "Échec de l'import" }))
                    }
                }) { Text("Importer") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } },
        )
    }
}

private fun summary(p: MergePlan): String = listOfNotNull(
    count(p.sessions.size, "séance", "séances"),
    count(p.favorites.size, "favori", "favoris"),
    count(p.stats.size, "statistique", "statistiques"),
    count(p.tabs.size, "tablature", "tablatures"),
).joinToString(", ")

private fun count(n: Int, one: String, many: String): String? = if (n == 0) null else "$n ${if (n > 1) many else one}"

private fun formatDate(ms: Long): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
