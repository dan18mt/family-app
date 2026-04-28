package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.usecase.stock.*
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.presentation.screens.stock.StockUiState
import com.familyhome.app.presentation.screens.stock.StockViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getStockItemsUseCase: GetStockItemsUseCase
    private lateinit var updateStockQuantityUseCase: UpdateStockQuantityUseCase
    private lateinit var updateStockItemUseCase: UpdateStockItemUseCase
    private lateinit var deleteStockItemUseCase: DeleteStockItemUseCase
    private lateinit var getCustomStockCategoriesUseCase: GetCustomStockCategoriesUseCase
    private lateinit var addCustomStockCategoryUseCase: AddCustomStockCategoryUseCase
    private lateinit var updateCustomStockCategoryUseCase: UpdateCustomStockCategoryUseCase
    private lateinit var deleteCustomStockCategoryUseCase: DeleteCustomStockCategoryUseCase

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null, createdAt = 1L,
    )

    private fun stockItem(
        id: String = "s1",
        name: String = "Rice",
        category: StockCategory = StockCategory.FOOD,
        quantity: Float = 5f,
        minQuantity: Float = 2f,
    ) = StockItem(
        id = id, name = name, category = category, quantity = quantity,
        unit = "kg", minQuantity = minQuantity, updatedBy = "f1",
        updatedAt = System.currentTimeMillis(),
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getCurrentUserUseCase = mockk()
        getStockItemsUseCase = mockk()
        updateStockQuantityUseCase = mockk()
        updateStockItemUseCase = mockk()
        deleteStockItemUseCase = mockk()
        getCustomStockCategoriesUseCase = mockk()
        addCustomStockCategoryUseCase = mockk()
        updateCustomStockCategoryUseCase = mockk()
        deleteCustomStockCategoryUseCase = mockk()
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun setupDefaultMocks(user: User = fatherUser()) {
        coEvery { getCurrentUserUseCase() } returns user
        every { getStockItemsUseCase() } returns flowOf(emptyList())
        every { getStockItemsUseCase.byCategory(any()) } returns flowOf(emptyList())
        every { getCustomStockCategoriesUseCase() } returns flowOf(emptyList())
    }

    private fun createViewModel() = StockViewModel(
        getCurrentUserUseCase = getCurrentUserUseCase,
        getStockItemsUseCase = getStockItemsUseCase,
        updateStockQuantityUseCase = updateStockQuantityUseCase,
        updateStockItemUseCase = updateStockItemUseCase,
        deleteStockItemUseCase = deleteStockItemUseCase,
        getCustomStockCategoriesUseCase = getCustomStockCategoriesUseCase,
        addCustomStockCategoryUseCase = addCustomStockCategoryUseCase,
        updateCustomStockCategoryUseCase = updateCustomStockCategoryUseCase,
        deleteCustomStockCategoryUseCase = deleteCustomStockCategoryUseCase,
    )

    @Test
    fun `init loads current user`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("f1", vm.state.value.currentUser?.id)
    }

    @Test
    fun `init loads stock items`() = runTest {
        val items = listOf(stockItem(), stockItem(id = "s2", name = "Soap", category = StockCategory.CLEANING))
        setupDefaultMocks()
        every { getStockItemsUseCase() } returns flowOf(items)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `init loads custom categories`() = runTest {
        val cats = listOf(CustomStockCategory(id = "c1", name = "Spices", iconName = "local_fire_department"))
        setupDefaultMocks()
        every { getCustomStockCategoriesUseCase() } returns flowOf(cats)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.customCategories.size)
        assertEquals("Spices", vm.state.value.customCategories[0].name)
    }

    @Test
    fun `filterByCategory updates selected category and re-fetches items`() = runTest {
        val foodItems = listOf(stockItem())
        setupDefaultMocks()
        every { getStockItemsUseCase() } returns flowOf(foodItems + stockItem(id = "s2", name = "Soap", category = StockCategory.CLEANING))
        every { getStockItemsUseCase.byCategory(StockCategory.FOOD) } returns flowOf(foodItems)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.filterByCategory(StockCategory.FOOD)
        advanceUntilIdle()

        assertEquals(StockCategory.FOOD, vm.state.value.selectedCategory)
        assertEquals(1, vm.state.value.items.size)
        assertEquals("Rice", vm.state.value.items[0].name)
    }

    @Test
    fun `filterByCategory with null resets to all items`() = runTest {
        val allItems = listOf(stockItem(), stockItem(id = "s2", name = "Soap", category = StockCategory.CLEANING))
        setupDefaultMocks()
        every { getStockItemsUseCase() } returns flowOf(allItems)
        every { getStockItemsUseCase.byCategory(StockCategory.FOOD) } returns flowOf(listOf(stockItem()))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.filterByCategory(StockCategory.FOOD)
        advanceUntilIdle()

        vm.filterByCategory(null)
        advanceUntilIdle()

        assertNull(vm.state.value.selectedCategory)
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun `adjustQuantity calls use case with clamped value`() = runTest {
        setupDefaultMocks()
        val item = stockItem(quantity = 5f)
        coEvery { updateStockQuantityUseCase(any(), any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.adjustQuantity(item, 3f)
        advanceUntilIdle()

        coVerify { updateStockQuantityUseCase(fatherUser(), "s1", 8f) }
    }

    @Test
    fun `adjustQuantity clamps to zero for negative result`() = runTest {
        setupDefaultMocks()
        val item = stockItem(quantity = 2f)
        coEvery { updateStockQuantityUseCase(any(), any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.adjustQuantity(item, -5f)
        advanceUntilIdle()

        coVerify { updateStockQuantityUseCase(fatherUser(), "s1", 0f) }
    }

    @Test
    fun `adjustQuantity sets error on failure`() = runTest {
        setupDefaultMocks()
        coEvery { updateStockQuantityUseCase(any(), any(), any()) } returns
            Result.failure(SecurityException("Not allowed"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.adjustQuantity(stockItem(), 1f)
        advanceUntilIdle()

        assertEquals("Not allowed", vm.state.value.error)
    }

    @Test
    fun `adjustQuantity does nothing when no current user`() = runTest {
        coEvery { getCurrentUserUseCase() } returns null
        every { getStockItemsUseCase() } returns flowOf(emptyList())
        every { getCustomStockCategoriesUseCase() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.adjustQuantity(stockItem(), 1f)
        advanceUntilIdle()

        coVerify(exactly = 0) { updateStockQuantityUseCase(any(), any(), any()) }
    }

    @Test
    fun `updateItem calls use case`() = runTest {
        setupDefaultMocks()
        val item = stockItem()
        coEvery { updateStockItemUseCase(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateItem(item)
        advanceUntilIdle()

        coVerify { updateStockItemUseCase(fatherUser(), item) }
    }

    @Test
    fun `deleteItem calls use case`() = runTest {
        setupDefaultMocks()
        coEvery { deleteStockItemUseCase(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteItem(stockItem())
        advanceUntilIdle()

        coVerify { deleteStockItemUseCase(fatherUser(), "s1") }
    }

    @Test
    fun `deleteItem sets error on failure`() = runTest {
        setupDefaultMocks()
        coEvery { deleteStockItemUseCase(any(), any()) } returns
            Result.failure(SecurityException("Not allowed"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteItem(stockItem())
        advanceUntilIdle()

        assertEquals("Not allowed", vm.state.value.error)
    }

    @Test
    fun `addCategory calls use case`() = runTest {
        setupDefaultMocks()
        coEvery { addCustomStockCategoryUseCase(any(), any(), any()) } returns Result.success(
            CustomStockCategory(id = "c1", name = "Spices", iconName = "fire")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addCategory("Spices", "fire")
        advanceUntilIdle()

        coVerify { addCustomStockCategoryUseCase(fatherUser(), "Spices", "fire") }
    }

    @Test
    fun `deleteCategory calls use case`() = runTest {
        setupDefaultMocks()
        val cat = CustomStockCategory(id = "c1", name = "Spices", iconName = "fire")
        coEvery { deleteCustomStockCategoryUseCase(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteCategory(cat)
        advanceUntilIdle()

        coVerify { deleteCustomStockCategoryUseCase(fatherUser(), "c1") }
    }

    @Test
    fun `clearError resets error to null`() = runTest {
        setupDefaultMocks()
        coEvery { deleteStockItemUseCase(any(), any()) } returns
            Result.failure(RuntimeException("Error"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteItem(stockItem())
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.clearError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `initial state has correct defaults`() {
        val state = StockUiState()
        assertTrue(state.items.isEmpty())
        assertNull(state.currentUser)
        assertNull(state.selectedCategory)
        assertTrue(state.customCategories.isEmpty())
        assertTrue(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `stockItem isLowStock returns true when at or below minQuantity`() {
        assertTrue(stockItem(quantity = 2f, minQuantity = 2f).isLowStock)
        assertTrue(stockItem(quantity = 1f, minQuantity = 2f).isLowStock)
        assertFalse(stockItem(quantity = 3f, minQuantity = 2f).isLowStock)
    }
}
