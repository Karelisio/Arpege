package com.karelisio.arpege.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.ShapedIcon

/** Bottom sheet de mise à jour : changelog, progression animée, installation. */
@Composable
fun UpdateSheet(state: UpdateState, onDownload: (ReleaseInfo) -> Unit, onDismiss: () -> Unit) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        else -> return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShapedIcon(Icons.Rounded.SystemUpdate, shape = MaterialShapes.SoftBurst)
                Column {
                    Text("Arpège ${release.version}", style = MaterialTheme.typography.headlineSmall)
                    Text("Nouvelle version disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                release.changelog.ifBlank { "Améliorations et corrections." },
                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
            )
            AnimatedContent(targetState = state::class, label = "updateAction") { kind ->
                when (kind) {
                    UpdateState.Available::class -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss) { Text("Plus tard") }
                        Button(onClick = { onDownload(release) }) { Text("Mettre à jour") }
                    }
                    UpdateState.Downloading::class -> {
                        val target = (state as? UpdateState.Downloading)?.progress ?: 1f
                        val p by animateFloatAsState(target, label = "dl")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Text("Téléchargement… ${(p * 100).toInt()} %")
                        }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContainedLoadingIndicator()
                        Text("Installation…")
                    }
                }
            }
        }
    }
}
