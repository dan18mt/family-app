package com.familyhome.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhome.app.data.local.database.FamilyDatabase
import com.familyhome.app.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyDaoTest {

    private lateinit var db: FamilyDatabase
    private lateinit var userDao: UserDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var stockDao: StockItemDao
    private lateinit var choreLogDao: ChoreLogDao
    private lateinit var budgetDao: BudgetDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FamilyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        expenseDao = db.expenseDao()
        stockDao = db.stockItemDao()
        choreLogDao = db.choreLogDao()
        budgetDao = db.budgetDao()
    }

    @After
    fun teardown() { db.close() }

    // ─── UserDao ─────────────────────────────────────────────────────────────

    @Test
    fun userDao_insertAndGetAll() = runTest {
        val user = userEntity("u1", "FATHER")
        userDao.insertUser(user)
        val all = userDao.getAllUsers().first()
        assertEquals(1, all.size)
        assertEquals("u1", all[0].id)
    }

    @Test
    fun userDao_upsertOverwrites() = runTest {
        userDao.insertUser(userEntity("u1", "FATHER"))
        userDao.insertUser(userEntity("u1", "WIFE")) // same id, different role
        val user = userDao.getUserById("u1")
        assertEquals("WIFE", user?.role)
    }

    @Test
    fun userDao_deleteById() = runTest {
        userDao.insertUser(userEntity("u1", "FATHER"))
        userDao.deleteUser("u1")
        val all = userDao.getAllUsers().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun userDao_upsertAll() = runTest {
        val users = listOf(userEntity("u1", "FATHER"), userEntity("u2", "WIFE"))
        userDao.upsertAll(users)
        val all = userDao.getAllUsers().first()
        assertEquals(2, all.size)
    }

    // ─── ExpenseDao ───────────────────────────────────────────────────────────

    @Test
    fun expenseDao_insertAndGetAll() = runTest {
        expenseDao.insertExpense(expenseEntity("e1", "GROCERIES", 50_000L, paidBy = "u1"))
        val all = expenseDao.getAllExpenses().first()
        assertEquals(1, all.size)
        assertEquals(50_000L, all[0].amount)
    }

    @Test
    fun expenseDao_getByUser() = runTest {
        expenseDao.insertExpense(expenseEntity("e1", "GROCERIES", 50_000L, paidBy = "u1"))
        expenseDao.insertExpense(expenseEntity("e2", "TRANSPORT", 20_000L, paidBy = "u2"))
        val u1expenses = expenseDao.getExpensesByUser("u1").first()
        assertEquals(1, u1expenses.size)
        assertEquals("e1", u1expenses[0].id)
    }

    @Test
    fun expenseDao_getByCategory() = runTest {
        expenseDao.insertExpense(expenseEntity("e1", "GROCERIES", 50_000L, paidBy = "u1"))
        expenseDao.insertExpense(expenseEntity("e2", "TRANSPORT", 20_000L, paidBy = "u1"))
        val groceries = expenseDao.getExpensesByCategory("GROCERIES").first()
        assertEquals(1, groceries.size)
    }

    @Test
    fun expenseDao_getInRange() = runTest {
        val now = System.currentTimeMillis()
        expenseDao.insertExpense(expenseEntity("e1", "GROCERIES", 50_000L, paidBy = "u1", date = now))
        expenseDao.insertExpense(expenseEntity("e2", "GROCERIES", 30_000L, paidBy = "u1", date = now - 86_400_000L * 40))
        val inRange = expenseDao.getExpensesInRange(now - 86_400_000L, now + 86_400_000L).first()
        assertEquals(1, inRange.size)
        assertEquals("e1", inRange[0].id)
    }

    @Test
    fun expenseDao_delete() = runTest {
        expenseDao.insertExpense(expenseEntity("e1", "GROCERIES", 50_000L, paidBy = "u1"))
        expenseDao.deleteExpense("e1")
        assertTrue(expenseDao.getAllExpenses().first().isEmpty())
    }

    // ─── StockItemDao ─────────────────────────────────────────────────────────

    @Test
    fun stockDao_insertAndGetAll() = runTest {
        stockDao.insertItem(stockEntity("s1", "FOOD", qty = 5f, min = 2f))
        val all = stockDao.getAllItems().first()
        assertEquals(1, all.size)
    }

    @Test
    fun stockDao_getLowStock() = runTest {
        stockDao.insertItem(stockEntity("s1", "FOOD", qty = 1f, min = 3f)) // low
        stockDao.insertItem(stockEntity("s2", "FOOD", qty = 5f, min = 2f)) // ok
        val low = stockDao.getLowStockItems().first()
        assertEquals(1, low.size)
        assertEquals("s1", low[0].id)
    }

    @Test
    fun stockDao_getByCategory() = runTest {
        stockDao.insertItem(stockEntity("s1", "FOOD", qty = 5f, min = 1f))
        stockDao.insertItem(stockEntity("s2", "CLEANING", qty = 3f, min = 1f))
        val food = stockDao.getItemsByCategory("FOOD").first()
        assertEquals(1, food.size)
        assertEquals("s1", food[0].id)
    }

    @Test
    fun stockDao_updateItem() = runTest {
        stockDao.insertItem(stockEntity("s1", "FOOD", qty = 5f, min = 2f))
        val updated = stockEntity("s1", "FOOD", qty = 2f, min = 2f)
        stockDao.updateItem(updated)
        val item = stockDao.getItemById("s1")
        assertEquals(2f, item?.quantity)
    }

    @Test
    fun stockDao_deleteItem() = runTest {
        stockDao.insertItem(stockEntity("s1", "FOOD", qty = 5f, min = 2f))
        stockDao.deleteItem("s1")
        assertTrue(stockDao.getAllItems().first().isEmpty())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun userEntity(id: String, role: String) = UserEntity(
        id = id, name = "Test $id", role = role,
        parentId = null, avatarUri = null,
        createdAt = System.currentTimeMillis(),
    )

    private fun expenseEntity(
        id: String, category: String, amount: Long,
        paidBy: String, date: Long = System.currentTimeMillis(),
    ) = ExpenseEntity(
        id = id, amount = amount, currency = "IDR",
        category = category, description = "Test expense $id",
        paidBy = paidBy, receiptUri = null,
        loggedAt = System.currentTimeMillis(), expenseDate = date,
        aiExtracted = false,
    )

    private fun stockEntity(id: String, category: String, qty: Float, min: Float) = StockItemEntity(
        id = id, name = "Item $id", category = category,
        quantity = qty, unit = "pcs", minQuantity = min,
        updatedBy = "u1", updatedAt = System.currentTimeMillis(),
    )
}
