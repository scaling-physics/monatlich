package com.monatlich.ui.security

import app.cash.turbine.test
import com.monatlich.domain.usecase.FakeSecurityRepository
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecuritySettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeBiometricAvailability(private val available: Boolean) : BiometricAvailability {
        override fun isAvailable(): Boolean = available
    }

    private fun viewModel(security: FakeSecurityRepository, biometricAvailable: Boolean = true) =
        SecuritySettingsViewModel(security, FakeBiometricAvailability(biometricAvailable))

    @Test
    fun `turning on lock without a pin opens pin setup instead`() = runTest {
        val security = FakeSecurityRepository()
        val vm = viewModel(security)

        vm.uiState.test {
            assertFalse(awaitItem().hasPin)
            vm.onEvent(SecurityEvent.LockToggled(true))
            val state = awaitItem()
            assertEquals(PinSetupStage.Enter, state.pinSetup?.stage)
            assertFalse(state.lockEnabled) // not enabled yet - still mid setup
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing pin setup with matching pins sets the pin and enables lock`() = runTest {
        val security = FakeSecurityRepository()
        val vm = viewModel(security)

        vm.uiState.test {
            awaitItem()
            vm.onEvent(SecurityEvent.SetPinClicked)
            assertEquals(PinSetupStage.Enter, awaitItem().pinSetup?.stage)

            "1234".forEach { vm.onEvent(SecurityEvent.PinDigitEntered(it)) }
            val confirmStage = awaitUntil { it.pinSetup?.stage == PinSetupStage.Confirm }
            assertEquals(PinSetupStage.Confirm, confirmStage.pinSetup?.stage)

            "1234".forEach { vm.onEvent(SecurityEvent.PinDigitEntered(it)) }
            val done = awaitUntil { it.pinSetup == null }
            assertTrue(done.hasPin)
            assertTrue(done.lockEnabled)
            assertTrue(security.verifyPin("1234"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mismatched confirmation resets to a fresh entry with a mismatch flag`() = runTest {
        val security = FakeSecurityRepository()
        val vm = viewModel(security)

        vm.uiState.test {
            awaitItem()
            vm.onEvent(SecurityEvent.SetPinClicked)
            awaitItem()
            "1234".forEach { vm.onEvent(SecurityEvent.PinDigitEntered(it)) }
            awaitItem()
            "9999".forEach { vm.onEvent(SecurityEvent.PinDigitEntered(it)) }
            val mismatch = awaitUntil { it.pinSetup?.mismatch == true }
            assertEquals(PinSetupStage.Enter, mismatch.pinSetup?.stage)
            assertEquals("", mismatch.pinSetup?.pin)
            assertFalse(mismatch.hasPin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing the pin turns off lock and biometric too`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234", lockEnabled = true, biometricEnabled = true)
        val vm = viewModel(security)

        vm.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.hasPin)
            assertTrue(initial.lockEnabled)

            vm.onEvent(SecurityEvent.RemovePinClicked)
            assertTrue(awaitItem().showRemoveConfirm)

            vm.onEvent(SecurityEvent.RemovePinConfirmed)
            val result = awaitUntil { !it.hasPin }
            assertFalse(result.showRemoveConfirm)
            assertFalse(result.lockEnabled)
            assertFalse(result.biometricEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing pin setup clears the draft without saving anything`() = runTest {
        val security = FakeSecurityRepository()
        val vm = viewModel(security)

        vm.uiState.test {
            awaitItem()
            vm.onEvent(SecurityEvent.SetPinClicked)
            awaitItem()
            vm.onEvent(SecurityEvent.PinSetupDismissed)
            val state = awaitItem()
            assertNull(state.pinSetup)
            assertFalse(state.hasPin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `biometric availability is reported from the injected checker`() = runTest {
        val security = FakeSecurityRepository(storedPin = "1234")
        val vm = viewModel(security, biometricAvailable = false)

        vm.uiState.test {
            assertFalse(awaitItem().biometricAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SecurityUiState>.awaitUntil(
        predicate: (SecurityUiState) -> Boolean,
    ): SecurityUiState {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }
}
