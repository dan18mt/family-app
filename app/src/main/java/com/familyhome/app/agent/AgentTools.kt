package com.familyhome.app.agent

import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.chore.LogChoreUseCase
import com.familyhome.app.domain.usecase.expense.GetExpenseSummaryUseCase
import com.familyhome.app.domain.usecase.expense.LogExpenseUseCase
import com.familyhome.app.domain.usecase.stock.GetStockItemsUseCase
import com.familyhome.app.domain.usecase.stock.UpdateStockQuantityUseCase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject

/**
 * Implements each tool that the AI agent can call.
 * Each function receives the raw [JsonObject] input from Claude and returns a
 * plain-English result string that is fed back as a tool_result.
 */
class AgentTools @Inject constructor(
    private val logChoreUseCase: LogChoreUseCase,
    private val getStockItemsUseCase: GetStockItemsUseCase,
    private val updateStockQuantityUseCase: UpdateStockQuantityUseCase,
    private val logExpenseUseCase: LogExpenseUseCase,
    private val getExpenseSummaryUseCase: GetExpenseSummaryUseCase,
) {
    suspend fun dispatch(toolName: String, input: JsonObject, actor: User): String =
        when (toolName) {
            "log_chore"          -> logChore(input, actor)
            "update_stock"       -> updateStock(input, actor)
            "log_expense"        -> logExpense(input, actor)
            "get_stock_summary"  -> getStockSummary()
            "get_chore_history"  -> "Chore history retrieval: check the Chores screen for details."
            "get_expense_summary" -> getExpenseSummary(actor)
            else                 -> "Unknown tool: $toolName"
        }

    private suspend fun logChore(input: JsonObject, actor: User): String {
        val taskName = input["task"]?.jsonPrimitive?.content ?: return "Missing task name."
        val note     = input["note"]?.jsonPrimitive?.content
        val result   = logChoreUseCase(actor, taskName, actor.id, note)
        return result.fold(
            onSuccess = { "Chore logged: \"$taskName\"." },
            onFailure = { "Failed to log chore: ${it.message}" },
        )
    }

    private suspend fun updateStock(input: JsonObject, actor: User): String {
        val itemName    = input["item_name"]?.jsonPrimitive?.content ?: return "Missing item name."
        val newQuantity = input["new_quantity"]?.jsonPrimitive?.float ?: return "Missing quantity."

        val items = getStockItemsUseCase().first()
        val item  = items.firstOrNull { it.name.equals(itemName, ignoreCase = true) }
            ?: return "Item \"$itemName\" not found in pantry."

        val result = updateStockQuantityUseCase(actor, item.id, newQuantity)
        return result.fold(
            onSuccess = { "Updated $itemName to $newQuantity ${item.unit}." },
            onFailure = { "Failed to update stock: ${it.message}" },
        )
    }

    private suspend fun logExpense(input: JsonObject, actor: User): String {
        val amount      = input["amount"]?.jsonPrimitive?.long ?: return "Missing amount."
        val description = input["description"]?.jsonPrimitive?.content ?: ""
        val categoryStr = input["category"]?.jsonPrimitive?.content ?: "OTHER"
        val category    = runCatching { ExpenseCategory.valueOf(categoryStr.uppercase()) }
            .getOrDefault(ExpenseCategory.OTHER)

        val result = logExpenseUseCase(
            actor        = actor,
            amount       = amount * 100L, // IDR to cents
            category     = category,
            description  = description,
            paidByUserId = actor.id,
            receiptUri   = null,
            aiExtracted  = true,
        )
        return result.fold(
            onSuccess = { "Expense logged: $description — Rp ${amount}." },
            onFailure = { "Failed to log expense: ${it.message}" },
        )
    }

    private suspend fun getStockSummary(): String {
        val items = getStockItemsUseCase().first()
        if (items.isEmpty()) return "The pantry is empty."
        val lowStock = items.filter { it.isLowStock }
        val sb = StringBuilder()
        sb.append("Pantry has ${items.size} items. ")
        if (lowStock.isNotEmpty()) {
            sb.append("Low stock: ${lowStock.joinToString { "${it.name} (${it.quantity} ${it.unit})" }}.")
        } else {
            sb.append("All items are sufficiently stocked.")
        }
        return sb.toString()
    }

    private suspend fun getExpenseSummary(actor: User): String {
        val total = getExpenseSummaryUseCase(actor.id)
        return "This month's spending: Rp ${total / 100}."
    }
}

/** Tool definitions sent to the Claude API as part of each request. */
val FAMILY_AGENT_TOOLS = listOf(
    AnthropicTool(
        name        = "log_chore",
        description = "Log that a chore or task has been completed.",
        inputSchema = InputSchema(
            properties = mapOf(
                "task" to PropertyDef("string", "Name of the chore or task, e.g. 'wash dishes'"),
                "note" to PropertyDef("string", "Optional note about the task"),
            ),
            required = listOf("task"),
        ),
    ),
    AnthropicTool(
        name        = "update_stock",
        description = "Update the quantity of a pantry/stock item.",
        inputSchema = InputSchema(
            properties = mapOf(
                "item_name"    to PropertyDef("string", "Name of the item in the pantry"),
                "new_quantity" to PropertyDef("number", "New quantity value"),
            ),
            required = listOf("item_name", "new_quantity"),
        ),
    ),
    AnthropicTool(
        name        = "log_expense",
        description = "Record a new expense.",
        inputSchema = InputSchema(
            properties = mapOf(
                "amount"      to PropertyDef("number", "Amount in IDR (not cents), e.g. 50000"),
                "description" to PropertyDef("string", "What the money was spent on"),
                "category"    to PropertyDef(
                    "string",
                    "Expense category",
                    enum = listOf("GROCERIES","TRANSPORT","SCHOOL","HEALTH","ENTERTAINMENT","HOUSEHOLD","OTHER"),
                ),
            ),
            required = listOf("amount", "description"),
        ),
    ),
    AnthropicTool(
        name        = "get_stock_summary",
        description = "Get a summary of current pantry stock and any low-stock alerts.",
        inputSchema = InputSchema(properties = emptyMap()),
    ),
    AnthropicTool(
        name        = "get_chore_history",
        description = "Get a summary of chores completed in the last N days.",
        inputSchema = InputSchema(
            properties = mapOf(
                "days" to PropertyDef("number", "Number of days to look back (default 7)"),
            ),
        ),
    ),
    AnthropicTool(
        name        = "get_expense_summary",
        description = "Get total spending for the current user this month.",
        inputSchema = InputSchema(properties = emptyMap()),
    ),
)
