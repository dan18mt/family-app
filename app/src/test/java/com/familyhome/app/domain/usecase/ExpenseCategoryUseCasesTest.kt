package com.familyhome.app.domain.usecase

import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.CustomExpenseCategoryRepository
import com.familyhome.app.domain.usecase.expense.AddCustomExpenseCategoryUseCase
import com.familyhome.app.domain.usecase.expense.DeleteCustomExpenseCategoryUseCase
import com.familyhome.app.domain.usecase.expense.GetCustomExpenseCategoriesUseCase
import com.familyhome.app.domain.usecase.expense.UpdateCustomExpenseCategoryUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ExpenseCategoryUseCasesTest {

    private val repository = mockk<CustomExpenseCategoryRepository>(relaxed = true)

    private fun makeFather() = User("f1", "Ahmad", Role.FATHER, null, null, "hash", 0L)
    private fun makeWife()   = User("w1", "Siti",  Role.WIFE,   null, null, "hash", 0L)
    private fun makeKid()    = User("k1", "Budi",  Role.KID,    "f1", null, "hash", 0L)

    private fun makeCategory(id: String = "c1") =
        CustomExpenseCategory(id, "Transport", "DirectionsCar")

    // ── GetCustomExpenseCategoriesUseCase ────────────────────────────────────────

    @Nested
    inner class GetCustomExpenseCategoriesUseCaseTest {
        private val useCase = GetCustomExpenseCategoriesUseCase(repository)

        @Test
        fun `returns flow of categories from repository`() {
            val categories = listOf(makeCategory())
            every { repository.getAllCategories() } returns flowOf(categories)
            useCase()
            verify { repository.getAllCategories() }
        }
    }

    // ── AddCustomExpenseCategoryUseCase ──────────────────────────────────────────

    @Nested
    inner class AddCustomExpenseCategoryUseCaseTest {
        private val useCase = AddCustomExpenseCategoryUseCase(repository)

        @Test
        fun `FATHER can add category`() = runTest {
            val result = useCase(makeFather(), "Food", "Restaurant")
            assertTrue(result.isSuccess)
            assertEquals("Food", result.getOrThrow().name)
            coVerify { repository.insertCategory(any()) }
        }

        @Test
        fun `WIFE can add category`() = runTest {
            val result = useCase(makeWife(), "Clothing", "Checkroom")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot add category`() = runTest {
            val result = useCase(makeKid(), "Food", "Restaurant")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("leaders") == true)
        }

        @Test
        fun `name is trimmed`() = runTest {
            val result = useCase(makeFather(), "  Food  ", "Restaurant")
            assertEquals("Food", result.getOrThrow().name)
        }

        @Test
        fun `iconName is preserved`() = runTest {
            val result = useCase(makeFather(), "Food", "Restaurant")
            assertEquals("Restaurant", result.getOrThrow().iconName)
        }
    }

    // ── UpdateCustomExpenseCategoryUseCase ───────────────────────────────────────

    @Nested
    inner class UpdateCustomExpenseCategoryUseCaseTest {
        private val useCase = UpdateCustomExpenseCategoryUseCase(repository)

        @Test
        fun `FATHER can update category`() = runTest {
            val cat    = makeCategory()
            val result = useCase(makeFather(), cat)
            assertTrue(result.isSuccess)
            coVerify { repository.updateCategory(cat) }
        }

        @Test
        fun `WIFE can update category`() = runTest {
            val result = useCase(makeWife(), makeCategory())
            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot update category`() = runTest {
            val result = useCase(makeKid(), makeCategory())
            assertTrue(result.isFailure)
        }
    }

    // ── DeleteCustomExpenseCategoryUseCase ───────────────────────────────────────

    @Nested
    inner class DeleteCustomExpenseCategoryUseCaseTest {
        private val useCase = DeleteCustomExpenseCategoryUseCase(repository)

        @Test
        fun `FATHER can delete category`() = runTest {
            val result = useCase(makeFather(), "c1")
            assertTrue(result.isSuccess)
            coVerify { repository.deleteCategory("c1") }
        }

        @Test
        fun `WIFE can delete category`() = runTest {
            val result = useCase(makeWife(), "c1")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot delete category`() = runTest {
            val result = useCase(makeKid(), "c1")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("leaders") == true)
        }
    }
}
