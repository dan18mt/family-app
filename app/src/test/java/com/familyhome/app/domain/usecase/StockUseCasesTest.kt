package com.familyhome.app.domain.usecase

import com.familyhome.app.data.notification.LowStockNotifier
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.StockRepository
import com.familyhome.app.domain.usecase.stock.AddStockItemUseCase
import com.familyhome.app.domain.usecase.stock.DeleteStockItemUseCase
import com.familyhome.app.domain.usecase.stock.GetLowStockItemsUseCase
import com.familyhome.app.domain.usecase.stock.GetStockItemsUseCase
import com.familyhome.app.domain.usecase.stock.UpdateStockItemUseCase
import com.familyhome.app.domain.usecase.stock.UpdateStockQuantityUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StockUseCasesTest {

    private val stockRepository = mockk<StockRepository>()
    private val lowStockNotifier = mockk<LowStockNotifier>()

    private fun fatherUser() = User(
        id = "father-1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null, pin = "hash", createdAt = 1_000L,
    )

    private fun wifeUser() = User(
        id = "wife-1", name = "Wife", role = Role.WIFE,
        parentId = "father-1", avatarUri = null, pin = "hash", createdAt = 2_000L,
    )

    private fun kidUser() = User(
        id = "kid-1", name = "Kid", role = Role.KID,
        parentId = "father-1", avatarUri = null, pin = "hash", createdAt = 3_000L,
    )

    private fun stockItem(id: String = "item-1", quantity: Float = 5f, minQuantity: Float = 2f) = StockItem(
        id = id, name = "Rice", category = StockCategory.FOOD,
        quantity = quantity, unit = "kg", minQuantity = minQuantity,
        updatedBy = "father-1", updatedAt = 1_000L,
    )

    @Nested
    inner class GetStockItemsUseCaseTest {
        private lateinit var useCase: GetStockItemsUseCase

        @BeforeEach
        fun setup() { useCase = GetStockItemsUseCase(stockRepository) }

        @Test
        fun `returns all items`() = runTest {
            val items = listOf(stockItem(), stockItem("item-2"))
            every { stockRepository.getAllItems() } returns flowOf(items)

            val emitted = mutableListOf<List<StockItem>>()
            useCase().collect { emitted.add(it) }

            assertEquals(items, emitted[0])
        }

        @Test
        fun `returns items by category`() = runTest {
            val items = listOf(stockItem())
            every { stockRepository.getItemsByCategory(StockCategory.FOOD) } returns flowOf(items)

            val emitted = mutableListOf<List<StockItem>>()
            useCase.byCategory(StockCategory.FOOD).collect { emitted.add(it) }

            assertEquals(items, emitted[0])
        }
    }

    @Nested
    inner class GetLowStockItemsUseCaseTest {
        private lateinit var useCase: GetLowStockItemsUseCase

        @BeforeEach
        fun setup() { useCase = GetLowStockItemsUseCase(stockRepository) }

        @Test
        fun `returns only low stock items`() = runTest {
            val lowItem = stockItem(quantity = 1f, minQuantity = 2f)
            every { stockRepository.getLowStockItems() } returns flowOf(listOf(lowItem))

            val emitted = mutableListOf<List<StockItem>>()
            useCase().collect { emitted.add(it) }

            assertEquals(1, emitted[0].size)
            assertTrue(emitted[0][0].isLowStock)
        }
    }

    @Nested
    inner class AddStockItemUseCaseTest {
        private lateinit var useCase: AddStockItemUseCase

        @BeforeEach
        fun setup() { useCase = AddStockItemUseCase(stockRepository) }

        @Test
        fun `father can add stock item`() = runTest {
            coJustRun { stockRepository.insertItem(any()) }

            val result = useCase(fatherUser(), "Milk", StockCategory.FOOD, 2f, "L", 1f)

            assertTrue(result.isSuccess)
            assertEquals("Milk", result.getOrThrow().name)
            assertEquals(StockCategory.FOOD, result.getOrThrow().category)
        }

        @Test
        fun `wife can add stock item`() = runTest {
            coJustRun { stockRepository.insertItem(any()) }

            val result = useCase(wifeUser(), "Soap", StockCategory.CLEANING, 3f, "bar", 1f)

            assertTrue(result.isSuccess)
        }

        @Test
        fun `kid cannot add stock item`() = runTest {
            val result = useCase(kidUser(), "Candy", StockCategory.FOOD, 5f, "pack", 1f)
            assertTrue(result.isFailure)
        }

        @Test
        fun `updatedBy is set to actor id`() = runTest {
            coJustRun { stockRepository.insertItem(any()) }

            val result = useCase(fatherUser(), "Eggs", StockCategory.FOOD, 12f, "pcs", 6f)

            assertEquals("father-1", result.getOrThrow().updatedBy)
        }
    }

    @Nested
    inner class UpdateStockQuantityUseCaseTest {
        private lateinit var useCase: UpdateStockQuantityUseCase

        @BeforeEach
        fun setup() { useCase = UpdateStockQuantityUseCase(stockRepository, lowStockNotifier) }

        @Test
        fun `anyone can update stock quantity`() = runTest {
            val item = stockItem()
            coEvery { stockRepository.getItemById("item-1") } returns item
            coJustRun { stockRepository.updateItem(any()) }
            coJustRun { lowStockNotifier.notifyIfLow(any()) }

            val result = useCase(kidUser(), "item-1", 10f)

            assertTrue(result.isSuccess)
            coVerify { stockRepository.updateItem(any()) }
        }

        @Test
        fun `notifies low stock after update`() = runTest {
            val item = stockItem(quantity = 5f, minQuantity = 2f)
            coEvery { stockRepository.getItemById("item-1") } returns item
            coJustRun { stockRepository.updateItem(any()) }
            coJustRun { lowStockNotifier.notifyIfLow(any()) }

            useCase(fatherUser(), "item-1", 1f)

            coVerify { lowStockNotifier.notifyIfLow(any()) }
        }

        @Test
        fun `returns failure if item not found`() = runTest {
            coEvery { stockRepository.getItemById("ghost") } returns null

            val result = useCase(fatherUser(), "ghost", 5f)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSuchElementException)
        }
    }

    @Nested
    inner class DeleteStockItemUseCaseTest {
        private lateinit var useCase: DeleteStockItemUseCase

        @BeforeEach
        fun setup() { useCase = DeleteStockItemUseCase(stockRepository) }

        @Test
        fun `father can delete item`() = runTest {
            coJustRun { stockRepository.deleteItem("item-1") }

            val result = useCase(fatherUser(), "item-1")

            assertTrue(result.isSuccess)
            coVerify { stockRepository.deleteItem("item-1") }
        }

        @Test
        fun `wife can delete item`() = runTest {
            coJustRun { stockRepository.deleteItem("item-1") }

            val result = useCase(wifeUser(), "item-1")

            assertTrue(result.isSuccess)
        }

        @Test
        fun `kid cannot delete item`() = runTest {
            val result = useCase(kidUser(), "item-1")
            assertTrue(result.isFailure)
        }
    }

    @Nested
    inner class UpdateStockItemUseCaseTest {
        private lateinit var useCase: UpdateStockItemUseCase

        @BeforeEach
        fun setup() { useCase = UpdateStockItemUseCase(stockRepository) }

        @Test
        fun `father can update item details`() = runTest {
            val item = stockItem()
            coJustRun { stockRepository.updateItem(any()) }

            val result = useCase(fatherUser(), item)

            assertTrue(result.isSuccess)
            coVerify { stockRepository.updateItem(any()) }
        }

        @Test
        fun `kid cannot update item details`() = runTest {
            val result = useCase(kidUser(), stockItem())
            assertTrue(result.isFailure)
        }
    }
}
