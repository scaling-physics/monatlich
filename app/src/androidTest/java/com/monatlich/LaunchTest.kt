package com.monatlich

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.monatlich.ui.navigation.NAV_BAR_TAG
import com.monatlich.ui.overview.MONTH_LABEL_TAG
import com.monatlich.ui.overview.OVERVIEW_SCREEN_TAG
import com.monatlich.ui.transactions.TRANSACTIONS_SCREEN_TAG
import com.monatlich.ui.transactions.TRANSACTION_EDITOR_SHEET_TAG
import org.junit.Rule
import org.junit.Test

class LaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsOverview() {
        composeRule.onNodeWithTag(OVERVIEW_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MONTH_LABEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(NAV_BAR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Categories").assertIsDisplayed()
    }

    @Test
    fun tappingTransactionsTabNavigates() {
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TRANSACTIONS_SCREEN_TAG).assertIsDisplayed()
    }

    @Test
    fun fabOpensAddExpenseSheet() {
        composeRule.onNodeWithContentDescription("Add expense").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TRANSACTION_EDITOR_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Add transaction").assertIsDisplayed()
    }
}
