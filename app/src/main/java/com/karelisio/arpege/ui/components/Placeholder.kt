package com.karelisio.arpege.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

@Composable
fun ComingSoon(title: String, onBack: () -> Unit, sharedKey: String? = null) {
    ToolScaffold(title = title, onBack = onBack, sharedKey = sharedKey) { p: PaddingValues ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingIndicator()
            Text("Bientôt disponible", style = MaterialTheme.typography.titleMedium)
        }
    }
}
