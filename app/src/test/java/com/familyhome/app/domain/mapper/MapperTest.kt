package com.familyhome.app.domain.mapper

import com.familyhome.app.data.local.entity.BudgetEntity
import com.familyhome.app.data.local.entity.ChoreAssignmentEntity
import com.familyhome.app.data.local.entity.ChoreLogEntity
import com.familyhome.app.data.local.entity.CustomExpenseCategoryEntity
import com.familyhome.app.data.local.entity.ExpenseEntity
import com.familyhome.app.data.local.entity.RecurringTaskEntity
import com.familyhome.app.data.local.entity.StockItemEntity
import com.familyhome.app.data.local.entity.UserEntity
import com.familyhome.app.data.mapper.toDomain
import com.familyhome.app.data.mapper.toDto
import com.familyhome.app.data.mapper.toEntity
import com.familyhome.app.data.mapper.toAssignmentDto
import com.familyhome.app.data.mapper.toAssignmentDomain
import com.familyhome.app.domain.model.AssignmentStatus
import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.BudgetPeriod
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.Frequency
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapperTest {

    // ─── UserMapper ───────────────────────────────────────────────────────────

    @Test
    fun `UserEntity toDomain round-trip`() {
        val entity = UserEntity(
            id = "u-1", name = "Alice", role = "FATHER",
            parentId = null, avatarUri = "file://avatar.jpg",
            createdAt = 1_000L,
        )
        val domain = entity.toDomain()
        assertEquals("u-1", domain.id)
        assertEquals("Alice", domain.name)
        assertEquals(Role.FATHER, domain.role)
        assertNull(domain.parentId)
        assertEquals("file://avatar.jpg", domain.avatarUri)
        assertEquals(1_000L, domain.createdAt)

        // Round-trip back to entity
        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `User toDto round-trip`() {
        val entity = UserEntity("u-2", "Bob", "KID", "u-1", null, 2_000L)
        val domain = entity.toDomain()
        val dto = domain.toDto()
        assertEquals("u-2", dto.id)
        assertEquals("KID", dto.role)

        val backToDomain = dto.toDomain()
        assertEquals(domain, backToDomain)
    }

    // ─── ExpenseMapper ────────────────────────────────────────────────────────

    @Test
    fun `ExpenseEntity toDomain round-trip`() {
        val entity = ExpenseEntity(
            id = "e-1", amount = 50_000L, currency = "IDR",
            category = "GROCERIES", description = "Rice",
            paidBy = "u-1", receiptUri = null,
            loggedAt = 1_000L, expenseDate = 2_000L,
            aiExtracted = false, customCategoryId = null,
        )
        val domain = entity.toDomain()
        assertEquals(ExpenseCategory.GROCERIES, domain.category)
        assertEquals(50_000L, domain.amount)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `ExpenseEntity with unknown category defaults to OTHER`() {
        val entity = ExpenseEntity(
            id = "e-2", amount = 10_000L, currency = "IDR",
            category = "TOTALLY_UNKNOWN_CATEGORY", description = "x",
            paidBy = "u-1", receiptUri = null,
            loggedAt = 1L, expenseDate = 1L,
            aiExtracted = false,
        )
        val domain = entity.toDomain()
        assertEquals(ExpenseCategory.OTHER, domain.category)
    }

    @Test
    fun `Expense toDto preserves all fields`() {
        val entity = ExpenseEntity(
            id = "e-3", amount = 20_000L, currency = "IDR",
            category = "TRANSPORT", description = "Taxi",
            paidBy = "u-2", receiptUri = "file://receipt.jpg",
            loggedAt = 100L, expenseDate = 200L,
            aiExtracted = true, customCategoryId = "custom-1",
        )
        val dto = entity.toDomain().toDto()
        assertEquals("TRANSPORT", dto.category)
        assertEquals("file://receipt.jpg", dto.receiptUri)
        assertEquals(true, dto.aiExtracted)
        assertEquals("custom-1", dto.customCategoryId)
    }

    // ─── BudgetMapper ─────────────────────────────────────────────────────────

    @Test
    fun `BudgetEntity toDomain round-trip`() {
        val entity = BudgetEntity(
            id = "b-1", targetUserId = "u-1",
            category = "GROCERIES", limitAmount = 1_000_000L,
            period = "MONTHLY", setBy = "u-1",
        )
        val domain = entity.toDomain()
        assertEquals(ExpenseCategory.GROCERIES, domain.category)
        assertEquals(BudgetPeriod.MONTHLY, domain.period)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `Budget with null category round-trip`() {
        val entity = BudgetEntity("b-2", null, null, 500_000L, "WEEKLY", "u-1")
        val domain = entity.toDomain()
        assertNull(domain.category)
        assertNull(domain.targetUserId)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `Budget toDto preserves category and period`() {
        val budget = Budget("b-3", "u-1", ExpenseCategory.HEALTH, 300_000L, BudgetPeriod.WEEKLY, "u-1")
        val dto = budget.toDto()
        assertEquals("HEALTH", dto.category)
        assertEquals("WEEKLY", dto.period)

        val backToDomain = dto.toDomain()
        assertEquals(budget, backToDomain)
    }

    // ─── StockMapper ──────────────────────────────────────────────────────────

    @Test
    fun `StockItemEntity toDomain round-trip`() {
        val entity = StockItemEntity(
            id = "s-1", name = "Rice", category = "FOOD",
            quantity = 5f, unit = "kg", minQuantity = 2f,
            updatedBy = "u-1", updatedAt = 1_000L,
        )
        val domain = entity.toDomain()
        assertEquals(StockCategory.FOOD, domain.category)
        assertEquals(5f, domain.quantity)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `StockItemEntity with unknown category defaults to OTHER`() {
        val entity = StockItemEntity(
            id = "s-2", name = "X", category = "GARBAGE_CAT",
            quantity = 1f, unit = "pcs", minQuantity = 1f,
            updatedBy = "u-1", updatedAt = 1L,
        )
        assertEquals(StockCategory.OTHER, entity.toDomain().category)
    }

    @Test
    fun `StockItem isLowStock is true when quantity le minQuantity`() {
        val entity = StockItemEntity("s-3", "Milk", "FOOD", 1f, "L", 2f, "u-1", 1L)
        val domain = entity.toDomain()
        assertEquals(true, domain.isLowStock)
    }

    @Test
    fun `StockItem isLowStock is false when quantity gt minQuantity`() {
        val entity = StockItemEntity("s-4", "Milk", "FOOD", 5f, "L", 2f, "u-1", 1L)
        assertEquals(false, entity.toDomain().isLowStock)
    }

    // ─── ChoreMapper ──────────────────────────────────────────────────────────

    @Test
    fun `ChoreLogEntity toDomain round-trip`() {
        val entity = ChoreLogEntity("log-1", "Dishes", "u-1", 1_000L, "done fast")
        val domain = entity.toDomain()
        assertEquals("Dishes", domain.taskName)
        assertEquals("done fast", domain.note)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `ChoreLog toDto round-trip`() {
        val log = ChoreLog("log-2", "Sweep", "u-2", 2_000L, null)
        val dto = log.toDto()
        assertEquals("Sweep", dto.taskName)
        assertNull(dto.note)

        val backToDomain = dto.toDomain()
        assertEquals(log, backToDomain)
    }

    @Test
    fun `RecurringTaskEntity toDomain round-trip`() {
        val entity = RecurringTaskEntity(
            id = "task-1", taskName = "Clean", frequency = "WEEKLY",
            assignedTo = "u-1", lastDoneAt = null, nextDueAt = 999_000L,
            scheduledAt = 888_000L, reminderMinutesBefore = 15,
        )
        val domain = entity.toDomain()
        assertEquals(Frequency.WEEKLY, domain.frequency)
        assertEquals(15, domain.reminderMinutesBefore)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `ChoreAssignmentEntity toDomain round-trip`() {
        val entity = ChoreAssignmentEntity(
            id = "assign-1", taskId = "task-1", taskName = "Dishes",
            assignedTo = "kid-1", assignedBy = "father-1",
            status = "PENDING", declineReason = null,
            assignedAt = 1_000L, respondedAt = null,
        )
        val domain = entity.toDomain()
        assertEquals(AssignmentStatus.PENDING, domain.status)
        assertNull(domain.declineReason)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `ChoreAssignment toAssignmentDto round-trip`() {
        val assignment = ChoreAssignment(
            id = "assign-2", taskId = "task-2", taskName = "Sweep",
            assignedTo = "kid-1", assignedBy = "wife-1",
            status = AssignmentStatus.DECLINED, declineReason = "sick",
            assignedAt = 1_000L, respondedAt = 2_000L,
        )
        val dto = assignment.toAssignmentDto()
        assertEquals("DECLINED", dto.status)
        assertEquals("sick", dto.declineReason)

        val backToDomain = dto.toAssignmentDomain()
        assertEquals(assignment, backToDomain)
    }

    // ─── CustomExpenseCategoryMapper ──────────────────────────────────────────

    @Test
    fun `CustomExpenseCategoryEntity toDomain round-trip`() {
        val entity = CustomExpenseCategoryEntity("cat-1", "Hobbies", "SportsBar")
        val domain = entity.toDomain()
        assertEquals("Hobbies", domain.name)
        assertEquals("SportsBar", domain.iconName)

        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }
}
