package com.aistudio.app

import java.util.UUID

/**
 * Standard relationship categories for family profiles.
 */
enum class RelationshipCategory(val displayName: String) {
    SELF("Self"),
    PARTNER("Partner / Spouse"),
    CHILD("Child"),
    PARENT("Parent"),
    SIBLING("Sibling"),
    FRIEND("Friend / Other")
}

/**
 * Common standard clothing / shoe size measurements.
 */
data class SizeMeasurements(
    val shoeUs: String = "",
    val shoeEu: String = "",
    val shoeCm: String = "",
    val shirtSize: String = "",       // e.g. S, M, L, XL, 15.5 34/35
    val chestBust: String = "",       // inches or cm
    val pantsWaist: String = "",      // inches or cm
    val pantsInseam: String = "",     // inches or cm
    val dressSize: String = "",       // e.g. 4, 6, 8, 10
    val jacketSuit: String = "",      // e.g. 40R, 42L
    val ringSize: String = "",        // e.g. 7, 8.5
    val hatSize: String = "",         // e.g. 7 1/4, Medium
    val beltSize: String = ""         // e.g. 34 in
)

/**
 * Brand specific fit quirks / sizing notes.
 */
data class BrandFitNote(
    val id: String = UUID.randomUUID().toString(),
    val brandName: String,
    val note: String
)

/**
 * A family member's complete sizing profile.
 */
data class FamilyMemberProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationship: RelationshipCategory,
    val avatarEmoji: String = "👤",
    val sizes: SizeMeasurements = SizeMeasurements(),
    val brandNotes: List<BrandFitNote> = emptyList(),
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * Complete UI state for SizeVault.
 */
data class SizeVaultUiState(
    val profiles: List<FamilyMemberProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val isProUnlocked: Boolean = false,
    val showAddEditDialog: Boolean = false,
    val editingProfile: FamilyMemberProfile? = null,
    val showBrandNoteDialog: Boolean = false,
    val showProPassDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val searchQuery: String = ""
)