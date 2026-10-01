package com.aistudio.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("who_has_my_prefs", Context.MODE_PRIVATE)
    private val dataFile = File(application.filesDir, "lent_items.json")

    init {
        loadProStatus()
        loadItems()
    }

    private fun loadProStatus() {
        val isPro = prefs.getBoolean("is_pro_member", false)
        _uiState.update { it.copy(isProUser = isPro) }
    }

    fun upgradeToPro() {
        prefs.edit().putBoolean("is_pro_member", true).apply()
        _uiState.update { it.copy(isProUser = true, showProDialog = false) }
    }

    private fun loadItems() {
        viewModelScope.launch {
            if (!dataFile.exists()) {
                val seedList = getInitialSeedData()
                _uiState.update { it.copy(items = seedList) }
                saveItemsToFile(seedList)
                return@launch
            }

            try {
                val jsonStr = dataFile.readText()
                val jsonArray = JSONArray(jsonStr)
                val loadedList = mutableListOf<LentItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    loadedList.add(
                        LentItem(
                            id = obj.getString("id"),
                            itemName = obj.getString("itemName"),
                            borrowerName = obj.getString("borrowerName"),
                            borrowerContact = obj.optString("borrowerContact", ""),
                            category = try {
                                ItemCategory.valueOf(obj.optString("category", ItemCategory.TOOLS.name))
                            } catch (e: Exception) {
                                ItemCategory.OTHER
                            },
                            lentDateEpochMs = obj.getLong("lentDateEpochMs"),
                            expectedReturnDateEpochMs = obj.getLong("expectedReturnDateEpochMs"),
                            notes = obj.optString("notes", ""),
                            isReturned = obj.getBoolean("isReturned"),
                            returnedDateEpochMs = if (obj.has("returnedDateEpochMs") && !obj.isNull("returnedDateEpochMs")) {
                                obj.getLong("returnedDateEpochMs")
                            } else null,
                            photoUriString = if (obj.has("photoUriString") && !obj.isNull("photoUriString")) {
                                obj.getString("photoUriString")
                            } else null
                        )
                    )
                }
                _uiState.update { it.copy(items = loadedList) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveItemsToFile(items: List<LentItem>) {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray()
                for (item in items) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("itemName", item.itemName)
                        put("borrowerName", item.borrowerName)
                        put("borrowerContact", item.borrowerContact)
                        put("category", item.category.name)
                        put("lentDateEpochMs", item.lentDateEpochMs)
                        put("expectedReturnDateEpochMs", item.expectedReturnDateEpochMs)
                        put("notes", item.notes)
                        put("isReturned", item.isReturned)
                        put("returnedDateEpochMs", item.returnedDateEpochMs ?: JSONObject.NULL)
                        put("photoUriString", item.photoUriString ?: JSONObject.NULL)
                    }
                    jsonArray.put(obj)
                }
                dataFile.writeText(jsonArray.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addItem(
        itemName: String,
        borrowerName: String,
        borrowerContact: String,
        category: ItemCategory,
        daysToReturn: Int,
        notes: String,
        photoUri: String?
    ) {
        val now = System.currentTimeMillis()
        val expectedDate = now + (daysToReturn.toLong() * 24 * 60 * 60 * 1000)
        val newItem = LentItem(
            itemName = itemName.trim(),
            borrowerName = borrowerName.trim(),
            borrowerContact = borrowerContact.trim(),
            category = category,
            lentDateEpochMs = now,
            expectedReturnDateEpochMs = expectedDate,
            notes = notes.trim(),
            isReturned = false,
            photoUriString = photoUri
        )

        val updated = listOf(newItem) + _uiState.value.items
        _uiState.update { it.copy(items = updated, showAddDialog = false) }
        saveItemsToFile(updated)
    }

    fun toggleReturned(itemId: String) {
        val now = System.currentTimeMillis()
        val updated = _uiState.value.items.map { item ->
            if (item.id == itemId) {
                val newStatus = !item.isReturned
                item.copy(
                    isReturned = newStatus,
                    returnedDateEpochMs = if (newStatus) now else null
                )
            } else {
                item
            }
        }
        _uiState.update { it.copy(items = updated) }
        saveItemsToFile(updated)
    }

    fun deleteItem(itemId: String) {
        val updated = _uiState.value.items.filterNot { it.id == itemId }
        _uiState.update { it.copy(items = updated) }
        saveItemsToFile(updated)
    }

    fun setFilter(filter: ItemFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showAddDialog(show: Boolean) {
        _uiState.update { it.copy(showAddDialog = show) }
    }

    fun showProDialog(show: Boolean) {
        _uiState.update { it.copy(showProDialog = show) }
    }

    fun showAboutDialog(show: Boolean) {
        _uiState.update { it.copy(showAboutDialog = show) }
    }

    fun setItemToPing(item: LentItem?) {
        _uiState.update { it.copy(itemToPing = item) }
    }

    private fun getInitialSeedData(): List<LentItem> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        return listOf(
            LentItem(
                itemName = "DeWalt 20V Cordless Drill",
                borrowerName = "Dave Miller",
                borrowerContact = "dave@example.com",
                category = ItemCategory.TOOLS,
                lentDateEpochMs = now - (14 * dayMs),
                expectedReturnDateEpochMs = now - (2 * dayMs), // Overdue
                notes = "Includes two batteries and hard case"
            ),
            LentItem(
                itemName = "Catan: 5-6 Player Extension",
                borrowerName = "Sarah Jenkins",
                borrowerContact = "555-0192",
                category = ItemCategory.BOARD_GAMES,
                lentDateEpochMs = now - (3 * dayMs),
                expectedReturnDateEpochMs = now + (4 * dayMs),
                notes = "Borrowed for game night weekend"
            ),
            LentItem(
                itemName = "Honda Gas Lawnmower",
                borrowerName = "Marcus Vance (Neighbor)",
                borrowerContact = "",
                category = ItemCategory.GARDENING,
                lentDateEpochMs = now - (20 * dayMs),
                expectedReturnDateEpochMs = now - (10 * dayMs),
                notes = "Needs fresh ethanol-free gas"
            )
        )
    }
}