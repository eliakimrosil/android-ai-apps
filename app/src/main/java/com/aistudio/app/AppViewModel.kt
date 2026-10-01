package com.aistudio.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(
        "return_guard_prefs",
        Context.MODE_PRIVATE
    )
    private val dataFile = File(application.filesDir, "return_guard_items.json")

    private val _uiState = MutableStateFlow(ReturnGuardUiState())
    val uiState: StateFlow<ReturnGuardUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadItemsFromLocalDisk()
    }

    private fun loadPreferences() {
        val isPro = prefs.getBoolean("is_pro_member", false)
        _uiState.update { it.copy(isProUser = isPro) }
    }

    fun upgradeToPro() {
        prefs.edit().putBoolean("is_pro_member", true).apply()
        _uiState.update { it.copy(isProUser = true, showProDialog = false) }
    }

    fun openProDialog() {
        _uiState.update { it.copy(showProDialog = true) }
    }

    fun dismissProDialog() {
        _uiState.update { it.copy(showProDialog = false) }
    }

    fun openAboutDialog() {
        _uiState.update { it.copy(showAboutDialog = true) }
    }

    fun dismissAboutDialog() {
        _uiState.update { it.copy(showAboutDialog = false) }
    }

    fun openAddItemDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun dismissAddItemDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun setFilterMode(mode: FilterMode) {
        _uiState.update { it.copy(filterMode = mode) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openCashierMode(item: ReturnItem) {
        _uiState.update { it.copy(cashierModeItem = item) }
    }

    fun closeCashierMode() {
        _uiState.update { it.copy(cashierModeItem = null) }
    }

    fun openReceiptPreview(item: ReturnItem) {
        _uiState.update { it.copy(previewReceiptItem = item) }
    }

    fun closeReceiptPreview() {
        _uiState.update { it.copy(previewReceiptItem = null) }
    }

    fun addItem(
        itemName: String,
        storeName: String,
        returnDays: Int,
        price: Double,
        category: String,
        barcode: String,
        receiptUri: String?,
        notes: String
    ) {
        val newItem = ReturnItem(
            itemName = itemName.trim(),
            storeName = storeName.trim(),
            purchaseDateMillis = System.currentTimeMillis(),
            returnWindowDays = returnDays.coerceAtLeast(1),
            purchasePrice = price.coerceAtLeast(0.0),
            category = category.trim(),
            barcodeRawValue = barcode.trim(),
            receiptImageUri = receiptUri,
            notes = notes.trim(),
            isReturned = false
        )
        val updatedList = listOf(newItem) + _uiState.value.items
        _uiState.update { it.copy(items = updatedList, showAddDialog = false) }
        saveItemsToLocalDisk(updatedList)
    }

    fun toggleReturnedStatus(itemId: String) {
        val updatedList = _uiState.value.items.map { item ->
            if (item.id == itemId) {
                item.copy(isReturned = !item.isReturned)
            } else {
                item
            }
        }
        _uiState.update { it.copy(items = updatedList) }
        saveItemsToLocalDisk(updatedList)
    }

    fun deleteItem(itemId: String) {
        val updatedList = _uiState.value.items.filterNot { it.id == itemId }
        _uiState.update { it.copy(items = updatedList) }
        saveItemsToLocalDisk(updatedList)
    }

    fun shareReturnSummary(context: Context, item: ReturnItem) {
        val daysRemaining = item.getDaysRemaining(System.currentTimeMillis())
        val shareBody = buildString {
            append("📦 ReturnGuard Notice: ${item.itemName} at ${item.storeName}\n")
            append("• Purchase Price: $${String.format("%.2f", item.purchasePrice)}\n")
            append("• Days Left: $daysRemaining day(s) (${item.returnWindowDays} day window)\n")
            if (item.barcodeRawValue.isNotEmpty()) {
                append("• Barcode / Order ID: ${item.barcodeRawValue}\n")
            }
            if (item.notes.isNotEmpty()) {
                append("• Notes: ${item.notes}\n")
            }
            append("\nManaged with ReturnGuard Offline Protection.")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareBody)
            putExtra(Intent.EXTRA_SUBJECT, "Return Deadline for ${item.itemName}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Return Ticket")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    private fun saveItemsToLocalDisk(items: List<ReturnItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                for (item in items) {
                    val obj = JSONObject()
                    obj.put("id", item.id)
                    obj.put("itemName", item.itemName)
                    obj.put("storeName", item.storeName)
                    obj.put("purchaseDateMillis", item.purchaseDateMillis)
                    obj.put("returnWindowDays", item.returnWindowDays)
                    obj.put("purchasePrice", item.purchasePrice)
                    obj.put("category", item.category)
                    obj.put("barcodeRawValue", item.barcodeRawValue)
                    obj.put("receiptImageUri", item.receiptImageUri ?: "")
                    obj.put("notes", item.notes)
                    obj.put("isReturned", item.isReturned)
                    jsonArray.put(obj)
                }
                dataFile.writeText(jsonArray.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadItemsFromLocalDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!dataFile.exists()) {
                // Populate realistic initial samples for immediate usability
                val initialSamples = createDefaultSampleItems()
                saveItemsToLocalDisk(initialSamples)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(items = initialSamples) }
                }
                return@launch
            }

            try {
                val content = dataFile.readText()
                val jsonArray = JSONArray(content)
                val loadedList = mutableListOf<ReturnItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawUri = obj.optString("receiptImageUri", "")
                    loadedList.add(
                        ReturnItem(
                            id = obj.optString("id"),
                            itemName = obj.optString("itemName"),
                            storeName = obj.optString("storeName"),
                            purchaseDateMillis = obj.optLong("purchaseDateMillis"),
                            returnWindowDays = obj.optInt("returnWindowDays"),
                            purchasePrice = obj.optDouble("purchasePrice"),
                            category = obj.optString("category", "General"),
                            barcodeRawValue = obj.optString("barcodeRawValue", ""),
                            receiptImageUri = if (rawUri.isNotEmpty()) rawUri else null,
                            notes = obj.optString("notes", ""),
                            isReturned = obj.optBoolean("isReturned", false)
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(items = loadedList) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createDefaultSampleItems(): List<ReturnItem> {
        val now = System.currentTimeMillis()
        val oneDay = 24L * 60L * 60L * 1000L

        return listOf(
            ReturnItem(
                itemName = "Nike Air Max 270 (Size 10)",
                storeName = "Nike",
                purchaseDateMillis = now - (56L * oneDay),
                returnWindowDays = 60,
                purchasePrice = 160.00,
                category = "Footwear",
                barcodeRawValue = "886549234891",
                receiptImageUri = null,
                notes = "Keep original box and tags intact"
            ),
            ReturnItem(
                itemName = "Linen Summer Blazer",
                storeName = "Zara",
                purchaseDateMillis = now - (27L * oneDay),
                returnWindowDays = 30,
                purchasePrice = 89.90,
                category = "Apparel",
                barcodeRawValue = "794500123984",
                receiptImageUri = null,
                notes = "Sleeves slightly long, try with shirt first"
            ),
            ReturnItem(
                itemName = "Wireless Noise-Canceling Earbuds",
                storeName = "Target",
                purchaseDateMillis = now - (15L * oneDay),
                returnWindowDays = 90,
                purchasePrice = 79.99,
                category = "Electronics",
                barcodeRawValue = "049000030491",
                receiptImageUri = null,
                notes = "Bought on RedCard for extended 120-day perk"
            ),
            ReturnItem(
                itemName = "Ergonomic Desk Chair",
                storeName = "IKEA",
                purchaseDateMillis = now - (30L * oneDay),
                returnWindowDays = 365,
                purchasePrice = 149.00,
                category = "Home Goods",
                barcodeRawValue = "503411239081",
                receiptImageUri = null,
                notes = "IKEA Family 365-day return policy"
            )
        )
    }
}