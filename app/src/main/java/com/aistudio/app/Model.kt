package com.aistudio.app

import java.util.UUID

enum class ItemCategory(val displayName: String) {
    TOOLS("Tools & Hardware"),
    GARDENING("Lawn & Gardening"),
    ELECTRONICS("Electronics & Tech"),
    OUTDOORS("Camping & Outdoors"),
    BOOKS("Books & Media"),
    BOARD_GAMES("Board Games"),
    OTHER("Other Gear")
}

data class LentItem(
    val id: String = UUID.randomUUID().toString(),
    val itemName: String,
    val borrowerName: String,
    val borrowerContact: String = "",
    val category: ItemCategory = ItemCategory.TOOLS,
    val lentDateEpochMs: Long = System.currentTimeMillis(),
    val expectedReturnDateEpochMs: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000), // Default 7 days
    val notes: String = "",
    val isReturned: Boolean = false,
    val returnedDateEpochMs: Long? = null,
    val photoUriString: String? = null
)

data class AppUiState(
    val items: List<LentItem> = emptyList(),
    val selectedFilter: ItemFilter = ItemFilter.ACTIVE,
    val searchQuery: String = "",
    val isProUser: Boolean = false,
    val showAddDialog: Boolean = false,
    val showProDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val itemToPing: LentItem? = null
)

enum class ItemFilter {
    ACTIVE,
    RETURNED,
    OVERDUE
}