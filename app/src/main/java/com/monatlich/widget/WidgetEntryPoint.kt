package com.monatlich.widget

import android.content.Context
import com.monatlich.domain.usecase.GetMonthSummary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * [MonatlichWidget] runs outside any Activity/Fragment, so it cannot receive a Hilt-injected
 * constructor; this is the standard way to reach into the Hilt graph from a plain [Context].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getMonthSummary(): GetMonthSummary
    fun clock(): Clock
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
