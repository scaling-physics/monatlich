package com.monatlich.ui.overview

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class OverviewViewModelTest {

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)

    private fun viewModel() = OverviewViewModel(clock = fixedClock)

    @Test
    fun `initial state is the current month`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(YearMonth.of(2026, 9), state.month)
            assertTrue(state.isCurrentMonth)
            assertFalse(state.isAddSheetVisible)
            assertEquals(5, state.categories.size)
            assertEquals(state.categories.sumOf { it.spentMinor }, state.totalSpentMinor)
            assertEquals(state.categories.sumOf { it.budgetMinor }, state.totalBudgetMinor)
        }
    }

    @Test
    fun `previous and next month move one month and back`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(YearMonth.of(2026, 9), awaitItem().month)

            vm.onEvent(OverviewEvent.PreviousMonth)
            val previous = awaitItem()
            assertEquals(YearMonth.of(2026, 8), previous.month)
            assertFalse(previous.isCurrentMonth)

            vm.onEvent(OverviewEvent.NextMonth)
            val back = awaitItem()
            assertEquals(YearMonth.of(2026, 9), back.month)
            assertTrue(back.isCurrentMonth)

            vm.onEvent(OverviewEvent.NextMonth)
            assertEquals(YearMonth.of(2026, 10), awaitItem().month)
        }
    }

    @Test
    fun `previous month crosses the year boundary`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            repeat(9) { vm.onEvent(OverviewEvent.PreviousMonth) }
            repeat(8) { awaitItem() }
            assertEquals(YearMonth.of(2025, 12), awaitItem().month)
        }
    }

    @Test
    fun `jump to current month returns home from far away`() = runTest {
        val vm = viewModel()
        repeat(14) { vm.onEvent(OverviewEvent.NextMonth) }
        assertEquals(YearMonth.of(2027, 11), vm.uiState.value.month)

        vm.onEvent(OverviewEvent.JumpToCurrentMonth)
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        assertTrue(vm.uiState.value.isCurrentMonth)
    }

    @Test
    fun `placeholder data differs between months so bars re-animate`() {
        val vm = viewModel()
        val september = vm.uiState.value
        vm.onEvent(OverviewEvent.NextMonth)
        val october = vm.uiState.value
        assertNotEquals(september.totalSpentMinor, october.totalSpentMinor)
        assertEquals(september.categories.map { it.name }, october.categories.map { it.name })
    }

    @Test
    fun `add sheet toggles and survives month change`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertFalse(awaitItem().isAddSheetVisible)

            vm.onEvent(OverviewEvent.AddExpenseClicked)
            assertTrue(awaitItem().isAddSheetVisible)

            vm.onEvent(OverviewEvent.NextMonth)
            assertTrue(awaitItem().isAddSheetVisible)

            vm.onEvent(OverviewEvent.AddSheetDismissed)
            assertFalse(awaitItem().isAddSheetVisible)
        }
    }
}
