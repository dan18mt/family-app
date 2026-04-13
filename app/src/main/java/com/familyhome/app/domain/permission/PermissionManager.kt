package com.familyhome.app.domain.permission

import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User

/**
 * Central authority for all role-based access checks.
 *
 * Rules:
 *   FATHER — full access to everything
 *   WIFE   — can see/edit own + all kids' data; cannot manage family members
 *   KID    — read/update own data only; cannot create/delete most things
 */
object PermissionManager {

    // ── Family management ────────────────────────────────────────────────────

    fun canAddFamilyMember(actor: User): Boolean = actor.role == Role.FATHER
    fun canRemoveFamilyMember(actor: User): Boolean = actor.role == Role.FATHER

    // ── Stock ────────────────────────────────────────────────────────────────

    fun canCreateStockItem(actor: User): Boolean =
        actor.role == Role.FATHER || actor.role == Role.WIFE

    fun canDeleteStockItem(actor: User): Boolean =
        actor.role == Role.FATHER || actor.role == Role.WIFE

    /** Kids can update quantities; creation and deletion are restricted. */
    fun canUpdateStockQuantity(actor: User): Boolean = true

    // ── Chores ───────────────────────────────────────────────────────────────

    fun canAssignChore(actor: User): Boolean =
        actor.role == Role.FATHER || actor.role == Role.WIFE

    fun canCreateRecurringTask(actor: User): Boolean =
        actor.role == Role.FATHER || actor.role == Role.WIFE

    fun canDeleteRecurringTask(actor: User): Boolean =
        actor.role == Role.FATHER || actor.role == Role.WIFE

    /** Kids can only view their own chore history. */
    fun canViewChoreHistory(actor: User, targetUserId: String): Boolean =
        when (actor.role) {
            Role.FATHER -> true
            Role.WIFE   -> true
            Role.KID    -> actor.id == targetUserId
        }

    fun canLogChoreForUser(actor: User, targetUserId: String): Boolean =
        when (actor.role) {
            Role.FATHER -> true
            Role.WIFE   -> true
            Role.KID    -> actor.id == targetUserId
        }

    // ── Expenses ─────────────────────────────────────────────────────────────

    fun canViewExpense(actor: User, expense_paidBy: String, allUsers: List<User>): Boolean =
        when (actor.role) {
            Role.FATHER -> true
            Role.WIFE   -> {
                val payer = allUsers.find { it.id == expense_paidBy }
                expense_paidBy == actor.id || payer?.role == Role.KID
            }
            Role.KID -> expense_paidBy == actor.id
        }

    fun canDeleteExpense(actor: User, expense_paidBy: String): Boolean =
        when (actor.role) {
            Role.FATHER -> true
            Role.WIFE   -> expense_paidBy == actor.id
            Role.KID    -> false
        }

    // ── Budgets ──────────────────────────────────────────────────────────────

    fun canSetBudget(actor: User): Boolean = actor.role == Role.FATHER
    fun canViewBudgets(actor: User): Boolean =
        actor.role == Role.FATHER || actor.role == Role.WIFE

    // ── Prayer goals ─────────────────────────────────────────────────────────

    /** Only the family leader can create/edit/delete goals assigned to the whole family. */
    fun canManageFamilyGoal(actor: User): Boolean = actor.role == Role.FATHER

    /** Any member can add a personal goal (one assigned only to themselves). */
    fun canAddPersonalGoal(actor: User): Boolean = true

    // ── Sync ─────────────────────────────────────────────────────────────────

    /** Only the host (typically Father) starts the sync server. */
    fun canHostSync(actor: User): Boolean = actor.role == Role.FATHER
}
