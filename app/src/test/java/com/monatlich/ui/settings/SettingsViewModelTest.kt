package com.monatlich.ui.settings

import app.cash.turbine.test
import com.monatlich.domain.model.Currency
import com.monatlich.domain.usecase.FakeExchangeRateRepository
import com.monatlich.domain.usecase.FakeSettingsRepository
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository(Currency.EUR)
    private val rates = FakeExchangeRateRepository(
        mapOf(Currency.USD to BigDecimal("0.92"), Currency.EUR to BigDecimal.ONE, Currency.INR to BigDecimal("0.0108")),
    )

    private fun viewModel() = SettingsViewModel(settings, rates)

    @Test
    fun `state lists the base currency and every other currency's rate`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(Currency.EUR, state.baseCurrency)
            assertEquals(listOf(Currency.USD, Currency.INR), state.rates.map { it.currency })
            assertEquals("0.92", state.rates[0].rate)
            assertEquals("0.0108", state.rates[1].rate)
            assertTrue(state.rates[0].sample.startsWith("$100.00"))
            assertEquals(listOf(Currency.USD, Currency.EUR, Currency.INR), state.availableCurrencies)
        }
    }

    @Test
    fun `base currency change persists and drops the new base from the rate list`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(SettingsEvent.BaseCurrencyClicked)
            assertTrue(awaitItem().isBaseCurrencyDialogVisible)

            vm.onEvent(SettingsEvent.BaseCurrencySelected(Currency.INR))
            val updated = awaitItemMatching { it.baseCurrency == Currency.INR && !it.isBaseCurrencyDialogVisible }
            assertEquals(Currency.INR, settings.state.value)
            assertEquals(listOf(Currency.USD, Currency.EUR), updated.rates.map { it.currency })
        }
    }

    @Test
    fun `valid rate edit is written to the repository`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(SettingsEvent.RateEditStarted(Currency.USD))
            val editing = awaitItem().rateEdit
            assertNotNull(editing)
            assertEquals(Currency.USD, editing!!.currency)
            assertEquals("0.92", editing.input)

            vm.onEvent(SettingsEvent.RateInputChanged("0,95"))
            assertNull(awaitItem().rateEdit?.error)

            vm.onEvent(SettingsEvent.RateEditSubmitted)
            val saved = awaitItemMatching { it.rateEdit == null && it.rates[0].rate == "0.95" }
            assertEquals(BigDecimal("0.95"), rates.get(Currency.USD)?.rateToBase)
            assertEquals("0.95", saved.rates.first { it.currency == Currency.USD }.rate)
        }
    }

    @Test
    fun `invalid rate is rejected with an error and nothing is written`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(SettingsEvent.RateEditStarted(Currency.USD))
            awaitItem()

            vm.onEvent(SettingsEvent.RateInputChanged("abc"))
            assertEquals(SettingsViewModel.ERROR_INVALID, awaitItem().rateEdit?.error)

            vm.onEvent(SettingsEvent.RateInputChanged("0"))
            val zero = awaitItem().rateEdit
            assertEquals(SettingsViewModel.ERROR_NOT_POSITIVE, zero?.error)
            assertFalse(zero!!.isValid)

            vm.onEvent(SettingsEvent.RateInputChanged("-1"))
            assertEquals(SettingsViewModel.ERROR_NOT_POSITIVE, awaitItem().rateEdit?.error)

            vm.onEvent(SettingsEvent.RateEditSubmitted)
            expectNoEvents()
            assertEquals(BigDecimal("0.92"), rates.get(Currency.USD)?.rateToBase)

            vm.onEvent(SettingsEvent.RateEditCancelled)
            assertNull(awaitItem().rateEdit)
            assertEquals(BigDecimal("0.92"), rates.get(Currency.USD)?.rateToBase)
        }
    }

    @Test
    fun `submitting an empty input flags it instead of saving`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(SettingsEvent.RateEditStarted(Currency.INR))
            awaitItem()
            vm.onEvent(SettingsEvent.RateInputChanged(""))
            assertNull(awaitItem().rateEdit?.error)

            vm.onEvent(SettingsEvent.RateEditSubmitted)
            assertEquals(SettingsViewModel.ERROR_INVALID, awaitItem().rateEdit?.error)
            assertEquals(BigDecimal("0.0108"), rates.get(Currency.INR)?.rateToBase)
        }
    }

    @Test
    fun `parseRate accepts locale commas and rejects non-positive values`() {
        assertEquals(BigDecimal("0.92"), SettingsViewModel.parseRate("0,92"))
        assertEquals(BigDecimal("83"), SettingsViewModel.parseRate(" 83 "))
        assertNull(SettingsViewModel.parseRate("0"))
        assertNull(SettingsViewModel.parseRate("-0.5"))
        assertNull(SettingsViewModel.parseRate(""))
        assertNull(SettingsViewModel.parseRate("1e"))
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsUiState>.awaitItemMatching(
        predicate: (SettingsUiState) -> Boolean,
    ): SettingsUiState {
        repeat(10) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching state within 10 emissions")
    }
}
