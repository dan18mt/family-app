package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.BudgetEntity
import com.familyhome.app.data.local.entity.ChoreAssignmentEntity
import com.familyhome.app.data.local.entity.ChoreLogEntity
import com.familyhome.app.data.local.entity.CustomExpenseCategoryEntity
import com.familyhome.app.data.local.entity.ExpenseEntity
import com.familyhome.app.data.local.entity.RecurringTaskEntity
import com.familyhome.app.data.local.entity.StockItemEntity
import com.familyhome.app.data.local.entity.UserEntity
import com.familyhome.app.domain.model.AssignmentStatus
import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.BudgetDto
import com.familyhome.app.domain.model.BudgetPeriod
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreAssignmentDto
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.ChoreLogDto
import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.ExpenseDto
import com.familyhome.app.domain.model.Frequency
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.model.RecurringTaskDto
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.StockItemDto
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.model.UserDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MapperTest {

    // ── UserMapper ────────────────────────────────────────────────────────────────

    @Nested
    inner class UserMapperTest {

        private val entity = UserEntity(
            id = "u1", name = "Ahmad", role = "FATHER",
            parentId = null, avatarUri = "uri://avatar",
            createdAt = 1000L,
        )

        private val domain = User(
            id = "u1", name = "Ahmad", role = Role.FATHER,
            parentId = null, avatarUri = "uri://avatar",
            createdAt = 1000L,
        )

        @Test
        fun `entity toDomain maps all fields correctly`() {
            val result = entity.toDomain()
            assertEquals(domain, result)
        }

        @Test
        fun `domain toEntity maps all fields correctly`() {
            val result = domain.toEntity()
            assertEquals(entity, result)
        }

        @Test
        fun `entity toDomain toEntity is round-trip`() {
            assertEquals(entity, entity.toDomain().toEntity())
        }

        @Test
        fun `domain toDto maps role to string`() {
            val dto = domain.toDto()
            assertEquals("FATHER", dto.role)
        }

        @Test
        fun `UserDto toDomain parses role enum`() {
            val dto    = UserDto("u1", "Ahmad", "WIFE", null, null, 2000L)
            val result = dto.toDomain()
            assertEquals(Role.WIFE, result.role)
        }

        @Test
        fun `entity with nullable parentId maps correctly`() {
            val e = entity.copy(parentId = "p1")
            assertEquals("p1", e.toDomain().parentId)
        }
    }

    // ── StockMapper ───────────────────────────────────────────────────────────────

    @Nested
    inner class StockMapperTest {

        private val entity = StockItemEntity(
            id = "s1", name = "Rice", category = "FOOD",
            quantity = 5.0f, unit = "kg", minQuantity = 1.0f,
            updatedBy = "u1", updatedAt = 1000L, customCategoryId = null,
        )

        private val domain = StockItem(
            id = "s1", name = "Rice", category = StockCategory.FOOD,
            quantity = 5.0f, unit = "kg", minQuantity = 1.0f,
            updatedBy = "u1", updatedAt = 1000L, customCategoryId = null,
        )

        @Test
        fun `entity toDomain maps all fields`() {
            assertEquals(domain, entity.toDomain())
        }

        @Test
        fun `domain toEntity maps all fields`() {
            assertEquals(entity, domain.toEntity())
        }

        @Test
        fun `round-trip entity toDomain toEntity`() {
            assertEquals(entity, entity.toDomain().toEntity())
        }

        @Test
        fun `unknown category string falls back to OTHER`() {
            val e = entity.copy(category = "UNKNOWN_CATEGORY")
            assertEquals(StockCategory.OTHER, e.toDomain().category)
        }

        @Test
        fun `StockItemDto toDomain handles unknown category gracefully`() {
            val dto = StockItemDto("s1", "Rice", "INVALID", 3.0f, "kg", 1.0f, "u1", 0L)
            assertEquals(StockCategory.OTHER, dto.toDomain().category)
        }

        @Test
        fun `domain toDto preserves all fields`() {
            val dto = domain.toDto()
            assertEquals("FOOD", dto.category)
            assertEquals(5.0f, dto.quantity)
        }
    }

    // ── ExpenseMapper ─────────────────────────────────────────────────────────────

    @Nested
    inner class ExpenseMapperTest {

        private val entity = ExpenseEntity(
            id = "e1", amount = 150_000L, currency = "IDR",
            category = "GROCERIES", description = "Belanja sayur",
            paidBy = "u1", receiptUri = null, loggedAt = 2000L,
            expenseDate = 1900L, aiExtracted = false, customCategoryId = null,
        )

        private val domain = Expense(
            id = "e1", amount = 150_000L, currency = "IDR",
            category = ExpenseCategory.GROCERIES, description = "Belanja sayur",
            paidBy = "u1", receiptUri = null, loggedAt = 2000L,
            expenseDate = 1900L, aiExtracted = false, customCategoryId = null,
        )

        @Test
        fun `entity toDomain maps all fields`() {
            assertEquals(domain, entity.toDomain())
        }

        @Test
        fun `domain toEntity maps all fields`() {
            assertEquals(entity, domain.toEntity())
        }

        @Test
        fun `round-trip entity toDomain toEntity`() {
            assertEquals(entity, entity.toDomain().toEntity())
        }

        @Test
        fun `unknown category string falls back to OTHER`() {
            val e = entity.copy(category = "LUXURY")
            assertEquals(ExpenseCategory.OTHER, e.toDomain().category)
        }

        @Test
        fun `ExpenseDto toDomain handles unknown category`() {
            val dto = ExpenseDto("e1", 100L, "IDR", "BAD_CAT", "test", "u1", null, 0L, 0L, false)
            assertEquals(ExpenseCategory.OTHER, dto.toDomain().category)
        }

        @Test
        fun `aiExtracted is preserved`() {
            val e = entity.copy(aiExtracted = true)
            assertTrue(e.toDomain().aiExtracted)
        }

        @Test
        fun `toDto preserves category as string`() {
            assertEquals("GROCERIES", domain.toDto().category)
        }
    }

    // ── BudgetMapper ──────────────────────────────────────────────────────────────

    @Nested
    inner class BudgetMapperTest {

        private val entity = BudgetEntity(
            id = "b1", targetUserId = "u1", category = "GROCERIES",
            limitAmount = 1_000_000L, period = "MONTHLY", setBy = "f1",
        )

        private val domain = Budget(
            id = "b1", targetUserId = "u1", category = ExpenseCategory.GROCERIES,
            limitAmount = 1_000_000L, period = BudgetPeriod.MONTHLY, setBy = "f1",
        )

        @Test
        fun `entity toDomain maps category and period enums`() {
            val result = entity.toDomain()
            assertEquals(domain, result)
        }

        @Test
        fun `null category in entity maps to null in domain`() {
            val e = entity.copy(category = null)
            assertNull(e.toDomain().category)
        }

        @Test
        fun `null targetUserId in entity maps to null in domain`() {
            val e = entity.copy(targetUserId = null)
            assertNull(e.toDomain().targetUserId)
        }

        @Test
        fun `domain toEntity maps back correctly`() {
            assertEquals(entity, domain.toEntity())
        }

        @Test
        fun `round-trip entity toDomain toEntity`() {
            assertEquals(entity, entity.toDomain().toEntity())
        }

        @Test
        fun `BudgetDto toDomain parses period and category`() {
            val dto    = BudgetDto("b1", null, "TRANSPORT", 500_000L, "WEEKLY", "f1")
            val result = dto.toDomain()
            assertEquals(BudgetPeriod.WEEKLY, result.period)
            assertEquals(ExpenseCategory.TRANSPORT, result.category)
        }
    }

    // ── ChoreMapper ───────────────────────────────────────────────────────────────

    @Nested
    inner class ChoreMapperTest {

        private val logEntity = ChoreLogEntity("l1", "Sweep", "k1", 1000L, "done")
        private val logDomain = ChoreLog("l1", "Sweep", "k1", 1000L, "done")

        @Test
        fun `ChoreLogEntity toDomain`() = assertEquals(logDomain, logEntity.toDomain())

        @Test
        fun `ChoreLog toEntity`() = assertEquals(logEntity, logDomain.toEntity())

        @Test
        fun `ChoreLog toDto preserves all fields`() {
            val dto = logDomain.toDto()
            assertEquals("Sweep", dto.taskName)
            assertEquals("done", dto.note)
        }

        @Test
        fun `ChoreLogDto toDomain`() {
            val dto    = ChoreLogDto("l1", "Sweep", "k1", 1000L, null)
            val result = dto.toDomain()
            assertEquals("Sweep", result.taskName)
            assertNull(result.note)
        }

        private val taskEntity = RecurringTaskEntity(
            "t1", "Vacuum", "WEEKLY", "k1",
            lastDoneAt = null, nextDueAt = 9999L, scheduledAt = null, reminderMinutesBefore = null,
        )

        @Test
        fun `RecurringTaskEntity toDomain parses frequency enum`() {
            val result = taskEntity.toDomain()
            assertEquals(Frequency.WEEKLY, result.frequency)
        }

        @Test
        fun `RecurringTask toEntity preserves frequency`() {
            val domain = taskEntity.toDomain()
            assertEquals("WEEKLY", domain.toEntity().frequency)
        }

        @Test
        fun `RecurringTaskDto toDomain`() {
            val dto    = RecurringTaskDto("t1", "Vacuum", "DAILY", null, null, 0L)
            val result = dto.toDomain()
            assertEquals(Frequency.DAILY, result.frequency)
        }

        private val assignmentEntity = ChoreAssignmentEntity(
            "a1", "t1", "Sweep", "k1", "f1", "PENDING", null, 1000L, null,
        )

        @Test
        fun `ChoreAssignmentEntity toDomain maps status enum`() {
            val result = assignmentEntity.toDomain()
            assertEquals(AssignmentStatus.PENDING, result.status)
        }

        @Test
        fun `ChoreAssignment toEntity maps status to string`() {
            val domain = assignmentEntity.toDomain()
            assertEquals("PENDING", domain.toEntity().status)
        }

        @Test
        fun `ChoreAssignmentDto round-trip`() {
            val dto = ChoreAssignmentDto("a1", "t1", "Sweep", "k1", "f1", "ACCEPTED", null, 0L, 1000L)
            val result = dto.toAssignmentDomain()
            assertEquals(AssignmentStatus.ACCEPTED, result.status)
            assertEquals(1000L, result.respondedAt)
        }
    }

    // ── CustomExpenseCategoryMapper ───────────────────────────────────────────────

    @Nested
    inner class CustomExpenseCategoryMapperTest {

        private val entity = CustomExpenseCategoryEntity("c1", "Hobbies", "Sports")
        private val domain = CustomExpenseCategory("c1", "Hobbies", "Sports")

        @Test
        fun `entity toDomain maps all fields`() {
            assertEquals(domain, entity.toDomain())
        }

        @Test
        fun `domain toEntity maps all fields`() {
            assertEquals(entity, domain.toEntity())
        }

        @Test
        fun `round-trip entity toDomain toEntity`() {
            assertEquals(entity, entity.toDomain().toEntity())
        }
    }
}

// Extension for assertTrue without importing AssertJ
private fun assertTrue(condition: Boolean) =
    org.junit.jupiter.api.Assertions.assertTrue(condition)
