package com.monatlich.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Pushes fresh data to the home-screen widget after a change that affects its summary. */
interface WidgetRefresher {
    suspend fun refresh()
}

class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {
    override suspend fun refresh() {
        // Best-effort: a widget that isn't pinned, or a Glance failure, must never break a save/delete.
        runCatching { MonatlichWidget().updateAll(context) }
    }
}
