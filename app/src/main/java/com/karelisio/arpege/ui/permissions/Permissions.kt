package com.karelisio.arpege.ui.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class AppPermission(val title: String, val rationale: String, val icon: ImageVector) {
    MICROPHONE(
        "Micro",
        "Pour l'accordeur et les exercices d'entraînement. Le son n'est jamais enregistré ni envoyé : tout est analysé sur ton téléphone.",
        Icons.Rounded.Mic,
    ),
    NOTIFICATIONS(
        "Notifications",
        "Pour contrôler le métronome depuis la notification (pause, ±BPM) quand l'écran est éteint.",
        Icons.Rounded.NotificationsActive,
    ),
    BATTERY(
        "Batterie sans restriction",
        "Pour que le métronome continue en arrière-plan. MagicOS, MIUI et d'autres surcouches arrêtent sinon les applis au bout de quelques minutes.",
        Icons.Rounded.BatteryChargingFull,
    ),
    INSTALL(
        "Installation des mises à jour",
        "Arpège n'est pas sur le Play Store : cette autorisation permet d'installer les nouvelles versions directement depuis l'appli.",
        Icons.Rounded.InstallMobile,
    ),
}

fun AppPermission.isGranted(context: Context): Boolean = when (this) {
    AppPermission.MICROPHONE ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    AppPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    AppPermission.BATTERY ->
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    AppPermission.INSTALL -> context.packageManager.canRequestPackageInstalls()
}

/** État des autorisations, revérifié à chaque retour au premier plan (retour des réglages système). */
class PermissionsController(
    private val context: Context,
    private val requestRuntime: (String) -> Unit,
) {
    var granted by mutableStateOf(AppPermission.entries.associateWith { it.isGranted(context) })
        private set

    fun refresh() { granted = AppPermission.entries.associateWith { it.isGranted(context) } }

    fun request(p: AppPermission) {
        val pkg = Uri.parse("package:${context.packageName}")
        when (p) {
            AppPermission.MICROPHONE -> requestRuntime(Manifest.permission.RECORD_AUDIO)
            AppPermission.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestRuntime(Manifest.permission.POST_NOTIFICATIONS)
            AppPermission.BATTERY -> launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            AppPermission.INSTALL -> launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg))
        }
    }

    fun openAppSettings() = launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    /** Gestionnaire de lancement des surcouches (Honor/Huawei, Xiaomi…), sinon réglages de l'appli. */
    fun openVendorAutostart() {
        val candidates = listOf(
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        for (c in candidates) {
            val i = Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
        openAppSettings()
    }

    private fun launch(i: Intent) {
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { openAppSettings() }
    }
}

@Composable
fun rememberPermissionsController(): PermissionsController {
    val context = LocalContext.current
    var controller: PermissionsController? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { controller?.refresh() }
    val c = remember { PermissionsController(context) { launcher.launch(it) } }
    controller = c
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { c.refresh() }
    return c
}
