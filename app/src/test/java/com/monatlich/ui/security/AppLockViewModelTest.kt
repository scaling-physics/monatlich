package com.monatlich.ui.security

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.monatlich.domain.usecase.FakeSecurityRepository
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppLockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private suspend fun ReceiveTurbine<AppLockUiState>.awaitUntil(
        predicate: (AppLockUiState) -> Boolean,
    ): AppLockUiState {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    @Test
    fun `entering the correct pin emits an unlock event and clears the entry`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.unlocked.test {
            viewModel.uiState.test {
                awaitItem()
                "1234".forEach { viewModel.onEvent(AppLockEvent.DigitEntered(it)) }
                val result = awaitUntil { it.pin.isEmpty() && !it.error }
                assertEquals("", result.pin)
                cancelAndIgnoreRemainingEvents()
            }
            awaitItem() // the unlock event itself
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entering the wrong pin shows an error, clears the entry, and does not unlock`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.unlocked.test {
            viewModel.uiState.test {
                awaitItem()
                "0000".forEach { viewModel.onEvent(AppLockEvent.DigitEntered(it)) }
                val result = awaitUntil { it.error }
                assertEquals("", result.pin)
                cancelAndIgnoreRemainingEvents()
            }
            expectNoEvents()
        }
    }

    @Test
    fun `a digit typed after a wrong pin starts a fresh entry`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.uiState.test {
            awaitItem()
            "1111".forEach { viewModel.onEvent(AppLockEvent.DigitEntered(it)) }
            awaitUntil { it.error }
            viewModel.onEvent(AppLockEvent.DigitEntered('9'))
            assertEquals("9", awaitUntil { it.pin.isNotEmpty() }.pin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `backspace removes the last digit and clears the error`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(AppLockEvent.DigitEntered('5'))
            assertEquals("5", awaitUntil { it.pin == "5" }.pin)
            viewModel.onEvent(AppLockEvent.Backspace)
            assertEquals("", awaitUntil { it.pin.isEmpty() }.pin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `biometric enabled is reflected in state`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true, biometricEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.uiState.test {
            assertTrue(awaitUntil { it.biometricEnabled }.biometricEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `biometric success emits an unlock event directly`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true, biometricEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.unlocked.test {
            viewModel.onEvent(AppLockEvent.BiometricSucceeded)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unlock events are not replayed to a collector that starts after the fact`() = runTest {
        // Regression test: AppLockViewModel is Activity-scoped and outlives any one lock screen
        // showing, so a past unlock must never bleed into a fresh collector the next time the app
        // re-locks and shows the screen again.
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true)
        val viewModel = AppLockViewModel(security)

        viewModel.uiState.test {
            awaitItem()
            "1234".forEach { viewModel.onEvent(AppLockEvent.DigitEntered(it)) }
            awaitUntil { it.pin.isEmpty() && !it.error }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.unlocked.test {
            expectNoEvents()
        }
    }
}
