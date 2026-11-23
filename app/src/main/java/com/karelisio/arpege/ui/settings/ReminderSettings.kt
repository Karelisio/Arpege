package com.karelisio.arpege.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.data.container
import com.karelisio.arpege.reminder.Reminders
import com.karelisio.arpege.theory.practice.ReminderPlan
import com.karelisio.arpege.ui.permissions.AppPermission
import com.karelisio.arpege.ui.permissions.rememberPermissionsController
import kotlinx.coroutines.launch

/** Rappel quotidien : activation + heure (TimePicker M3). */
@Composable
fun ReminderSettings(s: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val permissions = rememberPermissionsController()
    val notificationsOk = permissions.granted[AppPermission.NOTIFICATIONS] == true
    var picking by remember { mutableStateOf(false) }
    val time = ReminderPlan.label(s.reminderMinutes)

    fun save(enabled: Boolean, minutes: Int) {
        scope.launch {
            c.settings.setReminder(enabled, minutes)
            Reminders.apply(c.appContext, enabled, minutes)
        }
    }

    Card(shape = MaterialTheme.shapes.large) {
        ListItem(
            headlineContent = { Text("Rappel quotidien") },
            supportingContent = {
                Text(if (s.reminderEnabled) "Chaque jour à $time, sauf si tu as déjà pratiqué" else "Une notification pour garder ta série")
            },
            leadingContent = { Icon(Icons.Rounded.NotificationsActive, null) },
            trailingContent = {
                Switch(checked = s.reminderEnabled, onCheckedChange = { v ->
                    if (v && !notificationsOk) permissions.request(AppPermission.NOTIFICATIONS)
                    save(v, s.reminderMinutes)
                })
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            modifier = Modifier.clickable { picking = true },
            headlineContent = { Text("Heure du rappel") },
            supportingContent = { Text(time) },
            leadingContent = { Icon(Icons.Rounded.Schedule, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (s.reminderEnabled && !notificationsOk) {
            ListItem(
                modifier = Modifier.clickable { permissions.request(AppPermission.NOTIFICATIONS) },
                headlineContent = { Text("Notifications désactivées") },
                supportingContent = { Text("Touchez pour les autoriser, sinon le rappel ne s'affichera pas") },
                leadingContent = { Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.error) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    if (picking) {
        val state = rememberTimePickerState(s.reminderMinutes / 60, s.reminderMinutes % 60, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { picking = false },
            title = { Text("Heure du rappel") },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    save(true, state.hour * 60 + state.minute)
                    if (!notificationsOk) permissions.request(AppPermission.NOTIFICATIONS)
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuler") } },
        ) { TimePicker(state) }
    }
}
