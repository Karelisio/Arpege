package com.karelisio.arpege.ui.tabs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.data.container
import com.karelisio.arpege.data.db.TabFile
import com.karelisio.arpege.theory.song.GuitarProReader
import com.karelisio.arpege.theory.song.SongFormat
import java.util.Base64
import com.karelisio.arpege.theory.song.parseSong
import com.karelisio.arpege.ui.components.ShapedIcon
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.cascadeIn
import com.karelisio.arpege.ui.nav.sharedBoundsKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

@Composable
fun TabsLibraryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val tabs by c.db.tabs().all().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            var imported = 0
            var rejected = 0
            for (uri in uris) {
                val file = withContext(Dispatchers.IO) { runCatching { readTab(context, uri) }.getOrNull() }
                if (file == null) rejected++ else { c.db.tabs().insert(file); imported++ }
            }
            val msg = buildString {
                if (imported > 0) append("$imported fichier(s) importé(s)")
                if (rejected > 0) { if (isNotEmpty()) append(" · "); append("$rejected non pris en charge (.txt, .cho, .chopro, .pro, .gp3–.gp5)") }
            }
            if (msg.isNotEmpty()) snackbar.showSnackbar(msg)
        }
    }

    ToolScaffold(title = "Tablatures", onBack = onBack, sharedKey = "tool-tabs", snackbar = snackbar) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
            if (tabs.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShapedIcon(Icons.Rounded.MusicNote, shape = MaterialShapes.Clover8Leaf, size = 96.dp)
                    Text("Ta bibliothèque est vide", style = MaterialTheme.typography.titleLarge)
                    Text("Importe des tablatures texte (.txt), ChordPro (.cho, .chopro, .pro) ou Guitar Pro (.gp3 à .gp5) depuis tes fichiers. Aucune autorisation de stockage n'est nécessaire.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                    TabRow(t, Modifier.cascadeIn(i), onOpen = { onOpen(t.id) }, onDelete = {
                        scope.launch {
                            c.db.tabs().delete(t)
                            if (snackbar.showSnackbar("« ${t.title} » supprimé", actionLabel = "Annuler") == SnackbarResult.ActionPerformed) c.db.tabs().insert(t)
                        }
                    })
                }
            }
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("text/*", "application/octet-stream", "application/x-chordpro", "*/*")) },
                icon = { Icon(Icons.Rounded.FileOpen, null) }, text = { Text("Importer") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }
}

@Composable
private fun TabRow(t: TabFile, modifier: Modifier, onOpen: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state, modifier = modifier, enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = { Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error) } },
    ) {
        Card(onClick = onOpen, shape = MaterialTheme.shapes.large, modifier = Modifier.sharedBoundsKey("tab-${t.id}")) {
            ListItem(
                headlineContent = { Text(t.title) },
                supportingContent = { Text(listOf(t.artist, t.sourceName).filter { it.isNotBlank() }.joinToString(" · ")) },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Article, null) },
                trailingContent = { SuggestionChip(onClick = onOpen, label = { Text(runCatching { SongFormat.valueOf(t.format).label }.getOrDefault(t.format)) }) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/** Lit un fichier via SAF ; renvoie null si le format n'est pas pris en charge. */
private fun readTab(context: Context, uri: Uri): TabFile? {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
        if (cur.moveToFirst()) cur.getString(0) else null
    } ?: uri.lastPathSegment ?: "fichier.txt"
    val format = SongFormat.fromFileName(name) ?: return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > 2_000_000) return null
    if (format == SongFormat.GUITAR_PRO) {
        val gp = runCatching { GuitarProReader.read(bytes) }.getOrNull() ?: return null
        return TabFile(
            title = gp.title.ifBlank { name.substringBeforeLast('.') }, artist = gp.artist, format = format.name,
            content = Base64.getEncoder().encodeToString(bytes), sourceName = name,
        )
    }
    val text = decode(bytes)
    val song = parseSong(text, format, name.substringBeforeLast('.'))
    return TabFile(title = song.title, artist = song.artist, format = format.name, content = text, sourceName = name)
}

/** UTF-8 strict, sinon Windows-1252 (fréquent pour les vieux fichiers de tablatures). */
private fun decode(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix(Char(0xFEFF).toString())
} catch (_: CharacterCodingException) {
    String(bytes, Charset.forName("windows-1252"))
}
