package com.monatlich.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.monatlich.MainActivity
import com.monatlich.domain.model.MonthSummary
import com.monatlich.ui.common.formatMinor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

private val WidgetBackground = Color(0xFF6B1F2A)
private val WidgetOnBackground = Color(0xFFFFF3EE)
private val WidgetMuted = Color(0xFFE3B8C0)
private val WidgetOverBudget = Color(0xFFFFB4A9)
private val WidgetOnTrack = Color(0xFFA8D5A2)

private fun solid(color: Color): ColorProvider = ColorProvider(color)

/**
 * Glance widget: this month's spend at a glance plus a one-tap "+" that opens the app straight
 * into the add-transaction sheet. Data is fetched once per [provideGlance] call (widgets refresh
 * periodically, or on demand via [WidgetRefresher] after a save/delete) rather than held live.
 */
class MonatlichWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val month = YearMonth.now(entryPoint.clock())
        val summary = runCatching { entryPoint.getMonthSummary()(month).first() }.getOrNull()
        val content = WidgetContentData.from(month, summary)

        provideContent {
            WidgetContent(context = context, data = content)
        }
    }
}

/** Everything the widget draws, pre-formatted outside Compose (locale-sensitive, not observable there). */
private data class WidgetContentData(
    val monthLabel: String,
    val spentLabel: String,
    val remainingLabel: String?,
    val isOverBudget: Boolean,
) {
    companion object {
        fun from(month: YearMonth, summary: MonthSummary?): WidgetContentData {
            val monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()))
            if (summary == null) {
                return WidgetContentData(monthLabel, "—", null, isOverBudget = false)
            }
            val spentLabel = formatMinor(summary.totalSpentInBase.amountMinor, summary.baseCurrency.code)
            val remainingLabel = if (summary.totalBudgetInBase.amountMinor > 0L) {
                val remaining = summary.totalRemainingInBase
                if (remaining.isNegative) {
                    "${formatMinor(-remaining.amountMinor, remaining.currency.code)} over"
                } else {
                    "${formatMinor(remaining.amountMinor, remaining.currency.code)} left"
                }
            } else {
                null
            }
            return WidgetContentData(
                monthLabel = monthLabel,
                spentLabel = spentLabel,
                remainingLabel = remainingLabel,
                isOverBudget = summary.totalRemainingInBase.isNegative,
            )
        }
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetContentData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(solid(WidgetBackground))
            .padding(16.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.monthLabel,
                style = TextStyle(color = solid(WidgetMuted), fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            AddButton(context)
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = data.spentLabel,
            style = TextStyle(color = solid(WidgetOnBackground), fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "spent",
            style = TextStyle(color = solid(WidgetMuted), fontSize = 12.sp),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        data.remainingLabel?.let { label ->
            Text(
                text = label,
                style = TextStyle(
                    color = solid(if (data.isOverBudget) WidgetOverBudget else WidgetOnTrack),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun AddButton(context: Context) {
    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_QUICK_ADD, true)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .size(32.dp)
            .background(solid(WidgetOnBackground))
            .clickable(actionStartActivity(intent = quickAddIntent, parameters = actionParametersOf())),
    ) {
        Text(
            text = "+",
            style = TextStyle(color = solid(WidgetBackground), fontSize = 20.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}
