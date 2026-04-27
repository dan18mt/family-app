package com.familyhome.app.presentation.screens

import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.presentation.screens.expenses.ChartPeriod
import com.familyhome.app.presentation.screens.expenses.ExpensesUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [ExpensesUiState] computed properties.
 *
 * No mocking required — the state is a data class whose computed properties
 * ([totalThisMonth], [ExpensesUiState.displayedExpenses]) are exercised directly.
 *
 * Timeline used in tests (all midnight UTC, simplified to millis offsets):
 *   periodStart  = 1_000_000L   (represents the payroll / week start)
 *   beforePeriod = 500_000L     (older expense — before period start)
 *   inPeriod     = 2_000_000L   (recent expense — inside current period)
 *   alsoInPeriod = 3_000_000L   (another recent expense)
 */
class ExpensesUiStateTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val periodStart  = 1_000_000L
    private val beforePeriod =   500_000L
    private val inPeriod     = 2_000_000L
    private val alsoInPeriod = 3_000_000L

    private fun expense(
        id: String,
        amount: Long,
        paidBy: String = "u1",
        expenseDate: Long = inPeriod,
        category: ExpenseCategory = ExpenseCategory.GROCERIES,
        customCategoryId: String? = null,
    ) = Expense(
        id = id, amount = amount, currency = "IDR",
        category = category, description = "test",
        paidBy = paidBy, receiptUri = null,
        loggedAt = expenseDate, expenseDate = expenseDate,
        aiExtracted = false, customCategoryId = customCategoryId,
    )

    private fun baseState(vararg expenses: Expense) = ExpensesUiState(
        expenses = expenses.toList(),
        currentPeriodStartMillis = periodStart,
        isLoading = false,
    )

    // ── totalThisMonth ────────────────────────────────────────────────────────

    @Nested
    inner class TotalThisMonth {

        @Test
        fun `sums only expenses at or after period start when no date range is set`() {
            val state = baseState(
                expense("old", 10_000L, expenseDate = beforePeriod),
                expense("new", 20_000L, expenseDate = inPeriod),
                expense("new2", 5_000L, expenseDate = alsoInPeriod),
            )

            assertEquals(25_000L, state.totalThisMonth)
        }

        @Test
        fun `returns 0 when all expenses are before period start`() {
            val state = baseState(
                expense("old1", 10_000L, expenseDate = beforePeriod),
                expense("old2", 15_000L, expenseDate = beforePeriod - 1),
            )

            assertEquals(0L, state.totalThisMonth)
        }

        @Test
        fun `payroll start day is reflected via currentPeriodStartMillis`() {
            // Simulate payroll day = 25 → period starts later in month
            val laterPeriodStart = 2_500_000L
            val state = ExpensesUiState(
                expenses = listOf(
                    expense("e1", 10_000L, expenseDate = 2_000_000L), // before new period start
                    expense("e2", 30_000L, expenseDate = 3_000_000L), // after new period start
                ),
                currentPeriodStartMillis = laterPeriodStart,
                isLoading = false,
            )

            // Only e2 falls within the later period start
            assertEquals(30_000L, state.totalThisMonth)
        }

        @Test
        fun `uses custom date range instead of period start when range is set`() {
            val rangeFrom = 1_800_000L
            val rangeTo   = 2_500_000L
            val state = baseState(
                expense("before", 10_000L, expenseDate = beforePeriod),
                expense("in",     20_000L, expenseDate = 2_000_000L),  // in range
                expense("after",  15_000L, expenseDate = 3_000_000L),  // past rangeTo
            ).copy(dateRangeFrom = rangeFrom, dateRangeTo = rangeTo)

            // Only "in" matches rangeFrom..rangeTo
            assertEquals(20_000L, state.totalThisMonth)
        }

        @Test
        fun `custom range with only dateRangeFrom set includes expenses from that date onwards`() {
            val rangeFrom = 2_500_000L
            val state = baseState(
                expense("e1", 5_000L,  expenseDate = 2_000_000L), // before rangeFrom
                expense("e2", 15_000L, expenseDate = 3_000_000L), // after rangeFrom
            ).copy(dateRangeFrom = rangeFrom)

            assertEquals(15_000L, state.totalThisMonth)
        }

        @Test
        fun `custom range with only dateRangeTo set includes expenses up to that date`() {
            val rangeTo = 2_500_000L
            val state = baseState(
                expense("e1", 5_000L,  expenseDate = 2_000_000L), // before rangeTo
                expense("e2", 15_000L, expenseDate = 3_000_000L), // after rangeTo
            ).copy(dateRangeTo = rangeTo)

            assertEquals(5_000L, state.totalThisMonth)
        }

        @Test
        fun `member filter restricts total to selected member only`() {
            val state = baseState(
                expense("f1", 50_000L, paidBy = "father"),
                expense("k1", 10_000L, paidBy = "kid"),
            ).copy(selectedMemberId = "kid")

            assertEquals(10_000L, state.totalThisMonth)
        }

        @Test
        fun `null selectedMemberId includes all members`() {
            val state = baseState(
                expense("f1", 50_000L, paidBy = "father"),
                expense("k1", 10_000L, paidBy = "kid"),
            ).copy(selectedMemberId = null)

            assertEquals(60_000L, state.totalThisMonth)
        }

        @Test
        fun `member filter combined with date range works correctly`() {
            val rangeFrom = 1_500_000L
            val rangeTo   = 2_500_000L
            val state = baseState(
                expense("f-old",  5_000L, paidBy = "father", expenseDate = beforePeriod),
                expense("f-in",  20_000L, paidBy = "father", expenseDate = 2_000_000L),
                expense("k-in",  10_000L, paidBy = "kid",    expenseDate = 2_000_000L),
            ).copy(selectedMemberId = "father", dateRangeFrom = rangeFrom, dateRangeTo = rangeTo)

            // Only "f-in" passes both member and date filters
            assertEquals(20_000L, state.totalThisMonth)
        }

        @Test
        fun `totalThisMonth does not filter by category`() {
            val state = baseState(
                expense("e1", 30_000L, category = ExpenseCategory.GROCERIES),
                expense("e2", 20_000L, category = ExpenseCategory.TRANSPORT),
            ).copy(selectedCategoryKey = ExpenseCategory.GROCERIES.name)

            // Category filter must NOT affect the total — summary card shows all categories
            assertEquals(50_000L, state.totalThisMonth)
        }
    }

    // ── displayedExpenses ─────────────────────────────────────────────────────

    @Nested
    inner class DisplayedExpenses {

        @Test
        fun `shows only expenses at or after period start when no date range`() {
            val state = baseState(
                expense("old", 10_000L, expenseDate = beforePeriod),
                expense("new", 20_000L, expenseDate = inPeriod),
            )

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("new", displayed[0].id)
        }

        @Test
        fun `member filter shows only that member's expenses`() {
            val state = baseState(
                expense("f1", 50_000L, paidBy = "father"),
                expense("k1", 10_000L, paidBy = "kid"),
            ).copy(selectedMemberId = "kid")

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("k1", displayed[0].id)
        }

        @Test
        fun `null selectedMemberId shows all members' expenses`() {
            val state = baseState(
                expense("f1", 50_000L, paidBy = "father"),
                expense("k1", 10_000L, paidBy = "kid"),
            ).copy(selectedMemberId = null)

            assertEquals(2, state.displayedExpenses.size)
        }

        @Test
        fun `category filter shows only matching expenses`() {
            val state = baseState(
                expense("g1", 30_000L, category = ExpenseCategory.GROCERIES),
                expense("t1", 20_000L, category = ExpenseCategory.TRANSPORT),
            ).copy(selectedCategoryKey = ExpenseCategory.GROCERIES.name)

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("g1", displayed[0].id)
        }

        @Test
        fun `custom category key filter works for custom category expenses`() {
            val customId = "custom-cat-abc"
            val state = baseState(
                expense("c1", 15_000L, customCategoryId = customId),
                expense("b1", 25_000L, customCategoryId = null),
            ).copy(selectedCategoryKey = customId)

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("c1", displayed[0].id)
        }

        @Test
        fun `custom date range overrides period start`() {
            val rangeFrom = 2_200_000L
            val rangeTo   = 2_800_000L
            val state = baseState(
                expense("before-range", 10_000L, expenseDate = inPeriod),     // in period but before range
                expense("in-range",     20_000L, expenseDate = 2_500_000L),   // in range
                expense("after-range",  15_000L, expenseDate = alsoInPeriod), // after range
            ).copy(dateRangeFrom = rangeFrom, dateRangeTo = rangeTo)

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("in-range", displayed[0].id)
        }

        @Test
        fun `all three filters combined member + date range + category`() {
            val rangeFrom = 1_500_000L
            val rangeTo   = 2_800_000L
            val state = baseState(
                expense("ok",      10_000L, paidBy = "kid", expenseDate = 2_000_000L, category = ExpenseCategory.SCHOOL),
                expense("wrong-member", 5_000L, paidBy = "father", expenseDate = 2_000_000L, category = ExpenseCategory.SCHOOL),
                expense("wrong-cat",   8_000L, paidBy = "kid",    expenseDate = 2_000_000L, category = ExpenseCategory.GROCERIES),
                expense("wrong-date",  3_000L, paidBy = "kid",    expenseDate = alsoInPeriod, category = ExpenseCategory.SCHOOL),
            ).copy(
                selectedMemberId = "kid",
                dateRangeFrom    = rangeFrom,
                dateRangeTo      = rangeTo,
                selectedCategoryKey = ExpenseCategory.SCHOOL.name,
            )

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("ok", displayed[0].id)
        }

        @Test
        fun `weekly period start correctly excludes older expenses`() {
            // WEEKLY period starts at periodStart — expenses before that should be excluded
            val state = baseState(
                expense("this-week", 20_000L, expenseDate = inPeriod),
                expense("last-week",  5_000L, expenseDate = beforePeriod),
            )

            val displayed = state.displayedExpenses
            assertEquals(1, displayed.size)
            assertEquals("this-week", displayed[0].id)
        }
    }

    // ── isCustomDateRange ─────────────────────────────────────────────────────

    @Nested
    inner class IsCustomDateRange {

        @Test
        fun `false when both date range fields are null`() {
            assertEquals(false, baseState().isCustomDateRange)
        }

        @Test
        fun `true when dateRangeFrom is set`() {
            assertEquals(true, baseState().copy(dateRangeFrom = inPeriod).isCustomDateRange)
        }

        @Test
        fun `true when dateRangeTo is set`() {
            assertEquals(true, baseState().copy(dateRangeTo = inPeriod).isCustomDateRange)
        }

        @Test
        fun `true when both date range fields are set`() {
            assertEquals(true, baseState().copy(dateRangeFrom = periodStart, dateRangeTo = inPeriod).isCustomDateRange)
        }
    }
}
