package com.monatlich.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** System entry point for widget updates; delegates all rendering to [MonatlichWidget]. */
class MonatlichWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonatlichWidget()
}
