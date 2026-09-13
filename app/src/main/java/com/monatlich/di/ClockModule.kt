package com.monatlich.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Single source of "now" so ViewModels can be tested with a fixed clock. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
