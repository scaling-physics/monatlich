package com.monatlich.ui.budget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.ui.theme.MonatlichTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Renders the stateless [SetBudgetSheet] without Hilt and checks the key nodes and events. */
class SetBudgetSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = SetBudgetUiState(
        categoryId = 1,
        month = YearMonth.of(2026, 9),
        isLoading = false,
        categoryName = "Groceries",
        categoryColor = 0xFF2E7D32,
        existingBudget = Money(45_000, Currency.EUR),
        amountText = "450",
        amountMinor = 45_000,
        currency = Currency.EUR,
    )

    @Test
    fun rendersCategoryAmountAndActions() {
        val events = mutableListOf<SetBudgetEvent>()
        composeRule.setContent {
            MonatlichTheme {
                SetBudgetSheet(state = loaded, onEvent = { events += it }, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(SET_BUDGET_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeRule.onNodeWithTag(SET_BUDGET_AMOUNT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SET_BUDGET_SAVE_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(SET_BUDGET_REMOVE_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(SET_BUDGET_AMOUNT_TAG).performTextInput(".5")
        composeRule.onNodeWithTag(SET_BUDGET_SAVE_TAG).performClick()
        composeRule.onNodeWithTag(SET_BUDGET_REMOVE_TAG).performClick()
        composeRule.onNodeWithText("₹ INR").performClick()

        assertEquals(SetBudgetEvent.AmountEdited("450.5"), events.first())
        assertEquals(
            listOf(SetBudgetEvent.Save, SetBudgetEvent.Remove, SetBudgetEvent.CurrencySelected(Currency.INR)),
            events.drop(1),
        )
    }

    @Test
    fun saveDisabledWhileLoadingOrEmpty() {
        composeRule.setContent {
            MonatlichTheme {
                SetBudgetSheet(
                    state = SetBudgetUiState(categoryId = 1, month = YearMonth.of(2026, 9)),
                    onEvent = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag(SET_BUDGET_SAVE_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("No budget set for this month").assertIsDisplayed()
    }
}
