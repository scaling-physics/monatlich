package com.monatlich.ui.settings

import androidx.compose.runtime.Immutable
import com.monatlich.domain.model.Currency

/** Single immutable UI state for the Settings screen. */
@Immutable
data class SettingsUiState(
    val baseCurrency: Currency,
    /** Every currency other than [baseCurrency], in table order. */
    val rates: List<RateRowUiState>,
    val isLoading: Boolean = false,
    val isBaseCurrencyDialogVisible: Boolean = false,
    /** The rate currently being edited inline, or `null`. */
    val rateEdit: RateEditUiState? = null,
) {
    /** All currencies the user may pick as base, in declaration order. */
    val availableCurrencies: List<Currency> get() = Currency.entries
}

@Immutable
data class RateRowUiState(
    val currency: Currency,
    /** `1 currency == rate base`, as a plain decimal string (no exponent). */
    val rate: String,
    /** `100 currency` converted to base with [rate], formatted; a quick sanity check for the user. */
    val sample: String,
)

@Immutable
data class RateEditUiState(
    val currency: Currency,
    val input: String,
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null && input.isNotBlank()
}

sealed interface SettingsEvent {
    data object BaseCurrencyClicked : SettingsEvent
    data object BaseCurrencyDialogDismissed : SettingsEvent
    data class BaseCurrencySelected(val currency: Currency) : SettingsEvent
    data class RateEditStarted(val currency: Currency) : SettingsEvent
    data class RateInputChanged(val input: String) : SettingsEvent
    data object RateEditSubmitted : SettingsEvent
    data object RateEditCancelled : SettingsEvent
}
