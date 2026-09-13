package com.monatlich.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.monatlich.ui.common.PlaceholderScreen

const val TRANSACTIONS_SCREEN_TAG = "transactions_screen"

/** Placeholder until M5 delivers the per-month transaction list with edit / delete. */
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Transactions",
        message = "Coming in M5",
        modifier = modifier,
        testTag = TRANSACTIONS_SCREEN_TAG,
    )
}
