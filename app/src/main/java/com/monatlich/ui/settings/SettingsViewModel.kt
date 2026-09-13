package com.monatlich.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.ExchangeRate
import com.monatlich.domain.model.Money
import com.monatlich.domain.repository.ExchangeRateRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.ui.common.format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Base currency + exchange-rate table. Reads are live flows from the repositories; the only local
 * state is which dialog / inline editor is open and the text typed into it.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val exchangeRates: ExchangeRateRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiFlags())

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.baseCurrency,
        exchangeRates.observeAll(),
        ui,
    ) { base, rates, flags ->
        SettingsUiState(
            baseCurrency = base,
            rates = rateRows(base, rates),
            isLoading = false,
            isBaseCurrencyDialogVisible = flags.isBaseCurrencyDialogVisible,
            rateEdit = flags.rateEdit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(baseCurrency = Currency.EUR, rates = emptyList(), isLoading = true),
    )

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.BaseCurrencyClicked -> ui.update { it.copy(isBaseCurrencyDialogVisible = true) }
            SettingsEvent.BaseCurrencyDialogDismissed -> ui.update { it.copy(isBaseCurrencyDialogVisible = false) }
            is SettingsEvent.BaseCurrencySelected -> selectBaseCurrency(event.currency)
            is SettingsEvent.RateEditStarted -> startRateEdit(event.currency)
            is SettingsEvent.RateInputChanged -> ui.update { flags ->
                flags.copy(rateEdit = flags.rateEdit?.copy(input = event.input, error = validate(event.input)))
            }
            SettingsEvent.RateEditSubmitted -> submitRateEdit()
            SettingsEvent.RateEditCancelled -> ui.update { it.copy(rateEdit = null) }
        }
    }

    private fun selectBaseCurrency(currency: Currency) {
        // Close the dialog first so the UI never waits on the write; editing a rate for the
        // currency that just became base makes no sense, so drop that editor too.
        ui.update { flags ->
            flags.copy(
                isBaseCurrencyDialogVisible = false,
                rateEdit = flags.rateEdit?.takeUnless { it.currency == currency },
            )
        }
        viewModelScope.launch { settings.setBaseCurrency(currency) }
    }

    private fun startRateEdit(currency: Currency) {
        val current = uiState.value.rates.firstOrNull { it.currency == currency }?.rate.orEmpty()
        ui.update { it.copy(rateEdit = RateEditUiState(currency = currency, input = current)) }
    }

    private fun submitRateEdit() {
        val edit = ui.value.rateEdit ?: return
        val parsed = parseRate(edit.input)
        if (parsed == null) {
            ui.update { it.copy(rateEdit = edit.copy(error = validate(edit.input) ?: ERROR_INVALID)) }
            return
        }
        ui.update { it.copy(rateEdit = null) }
        viewModelScope.launch { exchangeRates.set(edit.currency, parsed) }
    }

    private fun rateRows(base: Currency, rates: List<ExchangeRate>): List<RateRowUiState> {
        val byCurrency = rates.associateBy { it.currency }
        return Currency.entries
            .filter { it != base }
            .map { currency ->
                val rate = byCurrency[currency]?.rateToBase ?: BigDecimal.ONE
                RateRowUiState(
                    currency = currency,
                    rate = rate.stripTrailingZeros().toPlainString(),
                    sample = "${Money.of(SAMPLE_MAJOR, currency).format()} ≈ " +
                        Money.of(SAMPLE_MAJOR, currency).convertTo(base, rate).format(),
                )
            }
    }

    private data class UiFlags(
        val isBaseCurrencyDialogVisible: Boolean = false,
        val rateEdit: RateEditUiState? = null,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private val SAMPLE_MAJOR = BigDecimal("100")
        const val ERROR_INVALID = "Enter a number"
        const val ERROR_NOT_POSITIVE = "Rate must be greater than 0"

        /** Accepts `0.92`, `0,92`, ` 83 `; rejects blanks, non-numbers and anything `<= 0`. */
        fun parseRate(input: String): BigDecimal? {
            val value = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
            return value.takeIf { it.signum() > 0 }
        }

        /** Human-readable reason [parseRate] would reject [input], or `null` when it is valid. */
        fun validate(input: String): String? {
            if (input.isBlank()) return null // Empty while typing is not yet an error.
            val value = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return ERROR_INVALID
            return if (value.signum() > 0) null else ERROR_NOT_POSITIVE
        }
    }
}
