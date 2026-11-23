package com.karelisio.arpege.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.arpege.MainActivity
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.practice.PracticeStats
import com.karelisio.arpege.theory.practice.SessionLite
import java.time.LocalDate

/** Widget : série de jours, minutes de la semaine vs objectif, accès rapides. */
class ArpegeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val since = System.currentTimeMillis() - 400L * 86_400_000L
        val sessions = c.db.practice().since(since).map { SessionLite(it.startEpochMs, it.durationSec, it.instrument, it.activity) }
        val stats = PracticeStats(sessions)
        val today = LocalDate.now()
        val goal = c.settings.current().weeklyGoalMinutes
        val week = stats.weekMinutes(today)
        val streak = stats.streak(today)
        provideContent { GlanceTheme { Content(context, streak, week, goal) } }
    }

    @Composable
    private fun Content(context: Context, streak: Int, week: Int, goal: Int) {
        val size = LocalSize.current
        val wide = size.width >= WIDE.width
        val progress = (week.toFloat() / goal).coerceIn(0f, 1f)
        Column(
            GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .background(GlanceTheme.colors.widgetBackground).padding(14.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("🔥 $streak j", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                    Text("$week / $goal min cette semaine", style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress, modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.secondaryContainer,
            )
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                QuickButton(context, if (wide) "Accordeur" else "Accord.", "arpege://tuner", GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                QuickButton(context, if (wide) "Métronome" else "Métro.", "arpege://metronome", GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun QuickButton(context: Context, label: String, uri: String, modifier: GlanceModifier) {
        Box(
            modifier.height(40.dp).cornerRadius(20.dp).background(GlanceTheme.colors.primaryContainer)
                .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java))),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onPrimaryContainer))
        }
    }

    companion object {
        private val SMALL = DpSize(140.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)

        suspend fun refresh(context: Context) = runCatching { ArpegeWidget().updateAll(context) }
    }
}

class ArpegeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ArpegeWidget()
}
