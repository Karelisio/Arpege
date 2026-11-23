package com.karelisio.arpege.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.ToolScaffold
import com.karelisio.arpege.ui.components.cascadeIn

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val controller = rememberPermissionsController()
    ToolScaffold(title = "Autorisations", onBack = onBack) { p ->
        LazyColumn(
            Modifier.padding(p),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(AppPermission.entries) { i, perm ->
                androidx.compose.foundation.layout.Box(Modifier.cascadeIn(i)) { PermissionRow(perm, controller) }
            }
            item {
                Text(
                    "Une autorisation refusée définitivement se réactive dans les réglages système.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = controller::openAppSettings) { Text("Ouvrir les réglages de l'appli") }
            }
        }
    }
}
