package com.aistudio.app

import java.util.UUID

/**
 * Common retailer presets with default return windows in days.
 */
enum class RetailerPreset(
    val storeName: String,
    val defaultReturnDays: Int,
    val defaultCategory: String
) {
    TARGET("Target", 90, "General"),
    AMAZON("Amazon", 30, "Online"),
    ZARA("Zara", 30, "Apparel"),
    NIKE("Nike", 60, "Footwear"),
    IKEA("IKEA", 365, "Home Goods"),
    BEST_BUY("Best Buy", 15, "Electronics"),
    WALMART("Walmart", 90, "General"),
    COSTCO("Costco", 90, "General"),
    CUSTOM("Custom Store", 30, "Other")
}

/**
 * Return policy status urgency classification.
 */
enum class UrgencyTier {
    CRITICAL, // <= 3 days left
    WARNING,  // <= 7 days left
    SAFE,     // > 7 days left
    EXPIRED   // < 0 days left
}

/**
 * Core domain item representing a purchased product with a return deadline.
 */
data class ReturnItem(
    val id: String = UUID.randomUUID().toString(),
    val itemName: String,
    val storeName: String,
    val purchaseDateMillis: Long,
    val returnWindowDays: Int,
    val purchasePrice: Double,
    val category: String = "General",
    val barcodeRawValue: String = "",
    val receiptImageUri: String? = null,
    val notes: String = "",
    val isReturned: Boolean = false
) {
    val deadlineMillis: Long
        get() = purchaseDateMillis + (returnWindowDays.toLong() * 24L * 60L * 60L * 1000L)

    fun getDaysRemaining(currentTimeMillis: Long): Int {
        val diffMillis = deadlineMillis - currentTimeMillis
        return if (diffMillis <= 0L) {
            0
        } else {
            (diffMillis / (24L * 60L * 60L * 1000L)).toInt()
        }
    }

    fun isOverdue(currentTimeMillis: Long): Boolean {
        return currentTimeMillis > deadlineMillis
    }

    fun getUrgencyTier(currentTimeMillis: Long): UrgencyTier {
        if (isReturned) return UrgencyTier.SAFE
        if (isOverdue(currentTimeMillis)) return UrgencyTier.EXPIRED
        val days = getDaysRemaining(currentTimeMillis)
        return when {
            days <= 3 -> UrgencyTier.CRITICAL
            days <= 7 -> UrgencyTier.WARNING
            else -> UrgencyTier.SAFE
        }
    }

    fun getProgressRatio(currentTimeMillis: Long): Float {
        val totalDuration = (returnWindowDays.toLong() * 24L * 60L * 60L * 1000L).toFloat()
        if (totalDuration <= 0f) return 1f
        val elapsed = (currentTimeMillis - purchaseDateMillis).coerceAtLeast(0L).toFloat()
        return (elapsed / totalDuration).coerceIn(0f, 1f)
    }
}

/**
 * Filter mode for the Returns Feed.
 */
enum class FilterMode {
    ACTIVE,
    URGENT_ONLY,
    COMPLETED
}

/**
 * UI State container for ReturnGuard.
 */
data class ReturnGuardUiState(
    val items: List<ReturnItem> = emptyList(),
    val filterMode: FilterMode = FilterMode.ACTIVE,
    val searchQuery: String = "",
    val isProUser: Boolean = false,
    val showAddDialog: Boolean = false,
    val showProDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val cashierModeItem: ReturnItem? = null,
    val previewReceiptItem: ReturnItem? = null
) {
    val totalMoneyAtRisk: Double
        get() = items
            .filter { !it.isReturned && !it.isOverdue(System.currentTimeMillis()) }
            .sumOf { it.purchasePrice }

    val activeReturnsCount: Int
        get() = items.count { !it.isReturned && !it.isOverdue(System.currentTimeMillis()) }

    val urgentReturnsCount: Int
        get() {
            val now = System.currentTimeMillis()
            return items.count { !it.isReturned && it.getUrgencyTier(now) == UrgencyTier.CRITICAL }
        }
}