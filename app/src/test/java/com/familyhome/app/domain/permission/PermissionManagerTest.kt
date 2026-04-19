package com.familyhome.app.domain.permission

import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PermissionManagerTest {

    private fun father(id: String = "f1") =
        User(id, "Ahmad", Role.FATHER, null, null, 0L)

    private fun wife(id: String = "w1") =
        User(id, "Siti", Role.WIFE, null, null, 0L)

    private fun kid(id: String = "k1") =
        User(id, "Budi", Role.KID, "f1", null, 0L)

    // ── Family Management ─────────────────────────────────────────────────────────

    @Nested
    inner class FamilyManagementTest {

        @Test
        fun `only FATHER can add family members`() {
            assertTrue(PermissionManager.canAddFamilyMember(father()))
            assertFalse(PermissionManager.canAddFamilyMember(wife()))
            assertFalse(PermissionManager.canAddFamilyMember(kid()))
        }

        @Test
        fun `only FATHER can remove family members`() {
            assertTrue(PermissionManager.canRemoveFamilyMember(father()))
            assertFalse(PermissionManager.canRemoveFamilyMember(wife()))
            assertFalse(PermissionManager.canRemoveFamilyMember(kid()))
        }
    }

    // ── Stock ─────────────────────────────────────────────────────────────────────

    @Nested
    inner class StockPermissionsTest {

        @Test
        fun `FATHER and WIFE can create stock items`() {
            assertTrue(PermissionManager.canCreateStockItem(father()))
            assertTrue(PermissionManager.canCreateStockItem(wife()))
            assertFalse(PermissionManager.canCreateStockItem(kid()))
        }

        @Test
        fun `FATHER and WIFE can delete stock items`() {
            assertTrue(PermissionManager.canDeleteStockItem(father()))
            assertTrue(PermissionManager.canDeleteStockItem(wife()))
            assertFalse(PermissionManager.canDeleteStockItem(kid()))
        }

        @Test
        fun `everyone can update stock quantity`() {
            assertTrue(PermissionManager.canUpdateStockQuantity(father()))
            assertTrue(PermissionManager.canUpdateStockQuantity(wife()))
            assertTrue(PermissionManager.canUpdateStockQuantity(kid()))
        }
    }

    // ── Chores ────────────────────────────────────────────────────────────────────

    @Nested
    inner class ChorePermissionsTest {

        @Test
        fun `FATHER and WIFE can assign chores`() {
            assertTrue(PermissionManager.canAssignChore(father()))
            assertTrue(PermissionManager.canAssignChore(wife()))
            assertFalse(PermissionManager.canAssignChore(kid()))
        }

        @Test
        fun `FATHER and WIFE can create recurring tasks`() {
            assertTrue(PermissionManager.canCreateRecurringTask(father()))
            assertTrue(PermissionManager.canCreateRecurringTask(wife()))
            assertFalse(PermissionManager.canCreateRecurringTask(kid()))
        }

        @Test
        fun `FATHER and WIFE can view chore history for any user`() {
            assertTrue(PermissionManager.canViewChoreHistory(father(), "anyone"))
            assertTrue(PermissionManager.canViewChoreHistory(wife(), "anyone"))
        }

        @Test
        fun `KID can only view their own chore history`() {
            val kid = kid("k1")
            assertTrue(PermissionManager.canViewChoreHistory(kid, "k1"))
            assertFalse(PermissionManager.canViewChoreHistory(kid, "k2"))
        }

        @Test
        fun `FATHER and WIFE can log chore for any user`() {
            assertTrue(PermissionManager.canLogChoreForUser(father(), "anyone"))
            assertTrue(PermissionManager.canLogChoreForUser(wife(), "anyone"))
        }

        @Test
        fun `KID can only log chore for themselves`() {
            val kid = kid("k1")
            assertTrue(PermissionManager.canLogChoreForUser(kid, "k1"))
            assertFalse(PermissionManager.canLogChoreForUser(kid, "k2"))
        }
    }

    // ── Expenses ──────────────────────────────────────────────────────────────────

    @Nested
    inner class ExpensePermissionsTest {

        @Test
        fun `FATHER can view all expenses`() {
            assertTrue(PermissionManager.canViewExpense(father(), "anyone", emptyList()))
        }

        @Test
        fun `WIFE can view own expenses`() {
            val wife  = wife("w1")
            val users = listOf(wife)
            assertTrue(PermissionManager.canViewExpense(wife, "w1", users))
        }

        @Test
        fun `WIFE can view kid expenses`() {
            val wife  = wife("w1")
            val kid   = kid("k1")
            val users = listOf(wife, kid)
            assertTrue(PermissionManager.canViewExpense(wife, "k1", users))
        }

        @Test
        fun `WIFE cannot view other adult expenses`() {
            val wife    = wife("w1")
            val father2 = father("f2")
            val users   = listOf(wife, father2)
            assertFalse(PermissionManager.canViewExpense(wife, "f2", users))
        }

        @Test
        fun `KID can only view own expenses`() {
            val kid = kid("k1")
            assertTrue(PermissionManager.canViewExpense(kid, "k1", emptyList()))
            assertFalse(PermissionManager.canViewExpense(kid, "k2", emptyList()))
        }

        @Test
        fun `FATHER can delete any expense`() {
            assertTrue(PermissionManager.canDeleteExpense(father(), "anyone"))
        }

        @Test
        fun `WIFE can only delete own expense`() {
            val wife = wife("w1")
            assertTrue(PermissionManager.canDeleteExpense(wife, "w1"))
            assertFalse(PermissionManager.canDeleteExpense(wife, "k1"))
        }

        @Test
        fun `KID cannot delete any expense`() {
            val kid = kid("k1")
            assertFalse(PermissionManager.canDeleteExpense(kid, "k1"))
        }
    }

    // ── Budgets ───────────────────────────────────────────────────────────────────

    @Nested
    inner class BudgetPermissionsTest {

        @Test
        fun `only FATHER can set budgets`() {
            assertTrue(PermissionManager.canSetBudget(father()))
            assertFalse(PermissionManager.canSetBudget(wife()))
            assertFalse(PermissionManager.canSetBudget(kid()))
        }

        @Test
        fun `FATHER and WIFE can view budgets`() {
            assertTrue(PermissionManager.canViewBudgets(father()))
            assertTrue(PermissionManager.canViewBudgets(wife()))
            assertFalse(PermissionManager.canViewBudgets(kid()))
        }
    }

    // ── Prayer ────────────────────────────────────────────────────────────────────

    @Nested
    inner class PrayerPermissionsTest {

        @Test
        fun `only FATHER can manage family prayer goals`() {
            assertTrue(PermissionManager.canManageFamilyGoal(father()))
            assertFalse(PermissionManager.canManageFamilyGoal(wife()))
            assertFalse(PermissionManager.canManageFamilyGoal(kid()))
        }

        @Test
        fun `everyone can add personal prayer goal`() {
            assertTrue(PermissionManager.canAddPersonalGoal(father()))
            assertTrue(PermissionManager.canAddPersonalGoal(wife()))
            assertTrue(PermissionManager.canAddPersonalGoal(kid()))
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────────

    @Nested
    inner class SyncPermissionsTest {

        @Test
        fun `FATHER and WIFE can host sync`() {
            assertTrue(PermissionManager.canHostSync(father()))
            assertTrue(PermissionManager.canHostSync(wife()))
            assertFalse(PermissionManager.canHostSync(kid()))
        }
    }
}
