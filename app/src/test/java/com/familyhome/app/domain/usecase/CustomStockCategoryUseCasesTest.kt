package com.familyhome.app.domain.usecase

import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.CustomStockCategoryRepository
import com.familyhome.app.domain.usecase.stock.AddCustomStockCategoryUseCase
import com.familyhome.app.domain.usecase.stock.DeleteCustomStockCategoryUseCase
import com.familyhome.app.domain.usecase.stock.GetCustomStockCategoriesUseCase
import com.familyhome.app.domain.usecase.stock.UpdateCustomStockCategoryUseCase
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

class CustomStockCategoryUseCasesTest {

    private val repository = mockk<CustomStockCategoryRepository>(relaxed = true)

    private fun makeFather() = User("f1", "Ahmad", Role.FATHER, null, null, "hash", 0L)
    private fun makeWife()   = User("w1", "Siti",  Role.WIFE,   null, null, "hash", 0L)
    private fun makeKid()    = User("k1", "Budi",  Role.KID,    "f1", null, "hash", 0L)

    private fun makeCategory(id: String = "sc1") =
        CustomStockCategory(id, "Snacks", "Cookie")

    // ── GetCustomStockCategoriesUseCase ──────────────────────────────────────────

    @Nested
    inner class GetUseCaseTest {
        private val useCase = GetCustomStockCategoriesUseCase(repository)

        @Test
        fun `returns flow from repository`() {
            every { repository.getAllCategories() } returns flowOf(listOf(makeCategory()))
            useCase()
            verify { repository.getAllCategories() }
        }
    }

    // ── AddCustomStockCategoryUseCase ────────────────────────────────────────────

    @Nested
    inner class AddUseCaseTest {
        private val useCase = AddCustomStockCategoryUseCase(repository)

        @Test
        fun `FATHER can add stock category`() = runTest {
            val result = useCase(makeFather(), "Snacks", "Cookie")
            assertTrue(result.isSuccess)
            assertEquals("Snacks", result.getOrThrow().name)
            coVerify { repository.insertCategory(any()) }
        }

        @Test
        fun `WIFE can add stock category`() = runTest {
            val result = useCase(makeWife(), "Beverages", "LocalDrink")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot add stock category`() = runTest {
            val result = useCase(makeKid(), "Snacks", "Cookie")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("leaders") == true)
        }

        @Test
        fun `name is trimmed before saving`() = runTest {
            val result = useCase(makeFather(), "  Dairy  ", "Egg")
            assertEquals("Dairy", result.getOrThrow().name)
        }
    }

    // ── UpdateCustomStockCategoryUseCase ─────────────────────────────────────────

    @Nested
    inner class UpdateUseCaseTest {
        private val useCase = UpdateCustomStockCategoryUseCase(repository)

        @Test
        fun `FATHER can update stock category`() = runTest {
            val cat    = makeCategory()
            val result = useCase(makeFather(), cat)
            assertTrue(result.isSuccess)
            coVerify { repository.updateCategory(cat) }
        }

        @Test
        fun `WIFE can update stock category`() = runTest {
            val result = useCase(makeWife(), makeCategory())
            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot update stock category`() = runTest {
            val result = useCase(makeKid(), makeCategory())
            assertTrue(result.isFailure)
        }
    }

    // ── DeleteCustomStockCategoryUseCase ─────────────────────────────────────────

    @Nested
    inner class DeleteUseCaseTest {
        private val useCase = DeleteCustomStockCategoryUseCase(repository)

        @Test
        fun `FATHER can delete stock category`() = runTest {
            val result = useCase(makeFather(), "sc1")
            assertTrue(result.isSuccess)
            coVerify { repository.deleteCategory("sc1") }
        }

        @Test
        fun `WIFE can delete stock category`() = runTest {
            val result = useCase(makeWife(), "sc1")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot delete stock category`() = runTest {
            val result = useCase(makeKid(), "sc1")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("leaders") == true)
        }
    }
}
