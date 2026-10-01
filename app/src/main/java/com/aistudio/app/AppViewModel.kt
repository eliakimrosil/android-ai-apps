package com.aistudio.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("sizevault_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SizeVaultUiState())
    val uiState: StateFlow<SizeVaultUiState> = _uiState.asStateFlow()

    init {
        loadPersistedData()
    }

    /**
     * Load persisted JSON profiles and Pro Pass status from SharedPreferences.
     */
    private fun loadPersistedData() {
        val isPro = prefs.getBoolean("is_pro_unlocked", false)
        val profilesJson = prefs.getString("saved_profiles_json", null)

        val loadedProfiles = if (!profilesJson.isNullOrEmpty()) {
            try {
                deserializeProfiles(profilesJson)
            } catch (e: Exception) {
                getDefaultSeedProfiles()
            }
        } else {
            getDefaultSeedProfiles()
        }

        _uiState.update { current ->
            current.copy(
                profiles = loadedProfiles,
                selectedProfileId = loadedProfiles.firstOrNull()?.id,
                isProUnlocked = isPro
            )
        }
    }

    /**
     * Initial starter profiles to give immediate visual value to the user.
     */
    private fun getDefaultSeedProfiles(): List<FamilyMemberProfile> {
        return listOf(
            FamilyMemberProfile(
                name = "Me (Self)",
                relationship = RelationshipCategory.SELF,
                avatarEmoji = "🧑💻",
                sizes = SizeMeasurements(
                    shoeUs = "10.5",
                    shoeEu = "44.5",
                    shoeCm = "28.5",
                    shirtSize = "Large",
                    chestBust = "41\"",
                    pantsWaist = "32\"",
                    pantsInseam = "32\"",
                    jacketSuit = "40R",
                    ringSize = "9.5",
                    hatSize = "7 3/8",
                    beltSize = "34\""
                ),
                brandNotes = listOf(
                    BrandFitNote(brandName = "Nike", note = "Runs 0.5 size small; always get US 11"),
                    BrandFitNote(brandName = "Zara", note = "Slim fit jackets; size up to 42R"),
                    BrandFitNote(brandName = "Levi's 511", note = "Stick to 32x32 waist stretch")
                )
            ),
            FamilyMemberProfile(
                name = "Sarah (Partner)",
                relationship = RelationshipCategory.PARTNER,
                avatarEmoji = "👩",
                sizes = SizeMeasurements(
                    shoeUs = "7.5",
                    shoeEu = "38",
                    shoeCm = "24.5",
                    shirtSize = "Small",
                    chestBust = "34B",
                    pantsWaist = "27\"",
                    pantsInseam = "30\"",
                    dressSize = "4",
                    jacketSuit = "Small / 36",
                    ringSize = "6.0",
                    beltSize = "Small"
                ),
                brandNotes = listOf(
                    BrandFitNote(brandName = "Lululemon", note = "Align pants size 4; Swiftly tops size 6"),
                    BrandFitNote(brandName = "Birkenstocks", note = "Regular width EU 38 fits perfect")
                )
            ),
            FamilyMemberProfile(
                name = "Leo (Child 1)",
                relationship = RelationshipCategory.CHILD,
                avatarEmoji = "👦",
                sizes = SizeMeasurements(
                    shoeUs = "3Y",
                    shoeEu = "34",
                    shoeCm = "21.5",
                    shirtSize = "Youth Medium (8-10)",
                    pantsWaist = "Youth 8 Reg",
                    pantsInseam = "23\"",
                    hatSize = "Youth Standard"
                ),
                brandNotes = listOf(
                    BrandFitNote(brandName = "Vans Kids", note = "Easy slip-ons; size 3.5Y gives room to grow"),
                    BrandFitNote(brandName = "Under Armour", note = "Youth Medium hoodies fit loose")
                )
            )
        )
    }

    /**
     * Persist current profiles into SharedPreferences as JSON.
     */
    private fun saveProfiles() {
        viewModelScope.launch {
            val serialized = serializeProfiles(_uiState.value.profiles)
            prefs.edit().putString("saved_profiles_json", serialized).apply()
        }
    }

    fun selectProfile(profileId: String) {
        _uiState.update { it.copy(selectedProfileId = profileId) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openAddProfileDialog() {
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingProfile = null
            )
        }
    }

    fun openEditProfileDialog(profile: FamilyMemberProfile) {
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingProfile = profile
            )
        }
    }

    fun dismissAddEditDialog() {
        _uiState.update {
            it.copy(
                showAddEditDialog = false,
                editingProfile = null
            )
        }
    }

    fun saveProfile(profile: FamilyMemberProfile) {
        val currentList = _uiState.value.profiles.toMutableList()
        val index = currentList.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            currentList[index] = profile.copy(updatedAtTimestamp = System.currentTimeMillis())
        } else {
            currentList.add(profile.copy(updatedAtTimestamp = System.currentTimeMillis()))
        }

        _uiState.update {
            it.copy(
                profiles = currentList,
                selectedProfileId = profile.id,
                showAddEditDialog = false,
                editingProfile = null
            )
        }
        saveProfiles()
    }

    fun deleteProfile(profileId: String) {
        val updated = _uiState.value.profiles.filter { it.id != profileId }
        val newSelected = updated.firstOrNull()?.id
        _uiState.update {
            it.copy(
                profiles = updated,
                selectedProfileId = newSelected
            )
        }
        saveProfiles()
    }

    fun openBrandNoteDialog() {
        _uiState.update { it.copy(showBrandNoteDialog = true) }
    }

    fun dismissBrandNoteDialog() {
        _uiState.update { it.copy(showBrandNoteDialog = false) }
    }

    fun addBrandNoteToSelectedProfile(brandName: String, note: String) {
        val selectedId = _uiState.value.selectedProfileId ?: return
        val currentList = _uiState.value.profiles.toMutableList()
        val index = currentList.indexOfFirst { it.id == selectedId }
        if (index >= 0) {
            val target = currentList[index]
            val newNotes = target.brandNotes + BrandFitNote(brandName = brandName.trim(), note = note.trim())
            currentList[index] = target.copy(brandNotes = newNotes, updatedAtTimestamp = System.currentTimeMillis())
            _uiState.update {
                it.copy(
                    profiles = currentList,
                    showBrandNoteDialog = false
                )
            }
            saveProfiles()
        }
    }

    fun removeBrandNote(noteId: String) {
        val selectedId = _uiState.value.selectedProfileId ?: return
        val currentList = _uiState.value.profiles.toMutableList()
        val index = currentList.indexOfFirst { it.id == selectedId }
        if (index >= 0) {
            val target = currentList[index]
            val newNotes = target.brandNotes.filter { it.id != noteId }
            currentList[index] = target.copy(brandNotes = newNotes, updatedAtTimestamp = System.currentTimeMillis())
            _uiState.update { it.copy(profiles = currentList) }
            saveProfiles()
        }
    }

    fun setProPassDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showProPassDialog = visible) }
    }

    fun setAboutDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAboutDialog = visible) }
    }

    fun unlockProPass() {
        prefs.edit().putBoolean("is_pro_unlocked", true).apply()
        _uiState.update {
            it.copy(
                isProUnlocked = true,
                showProPassDialog = false
            )
        }
    }

    /**
     * Build formatted share text suitable for WhatsApp, Telegram, or SMS.
     */
    fun generateShareCardText(profile: FamilyMemberProfile): String {
        val sb = StringBuilder()
        sb.append("📋 SizeVault Guide: ${profile.name} (${profile.relationship.displayName})\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        val s = profile.sizes
        if (s.shoeUs.isNotEmpty() || s.shoeEu.isNotEmpty() || s.shoeCm.isNotEmpty()) {
            val details = listOfNotNull(
                s.shoeUs.takeIf { it.isNotEmpty() }?.let { "US $it" },
                s.shoeEu.takeIf { it.isNotEmpty() }?.let { "EU $it" },
                s.shoeCm.takeIf { it.isNotEmpty() }?.let { "$it cm" }
            ).joinToString(" / ")
            sb.append("👟 Shoes: $details\n")
        }
        if (s.shirtSize.isNotEmpty()) sb.append("👕 Tops/Shirt: ${s.shirtSize}\n")
        if (s.chestBust.isNotEmpty()) sb.append("📐 Chest/Bust: ${s.chestBust}\n")
        if (s.pantsWaist.isNotEmpty() || s.pantsInseam.isNotEmpty()) {
            sb.append("👖 Pants: Waist ${s.pantsWaist.ifEmpty { "N/A" }} | Inseam ${s.pantsInseam.ifEmpty { "N/A" }}\n")
        }
        if (s.dressSize.isNotEmpty()) sb.append("👗 Dress: Size ${s.dressSize}\n")
        if (s.jacketSuit.isNotEmpty()) sb.append("🧥 Jacket/Suit: ${s.jacketSuit}\n")
        if (s.ringSize.isNotEmpty()) sb.append("💍 Ring: ${s.ringSize}\n")
        if (s.hatSize.isNotEmpty()) sb.append("🧢 Hat: ${s.hatSize}\n")
        if (s.beltSize.isNotEmpty()) sb.append("🧣 Belt: ${s.beltSize}\n")

        if (profile.brandNotes.isNotEmpty()) {
            sb.append("\n🏷️ Brand Fit Quirks:\n")
            profile.brandNotes.forEach { note ->
                sb.append(" • ${note.brandName}: ${note.note}\n")
            }
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Saved securely offline in SizeVault.")
        return sb.toString()
    }

    // ----------------------------------------------------
    // JSON Serialization Helpers
    // ----------------------------------------------------
    private fun serializeProfiles(profiles: List<FamilyMemberProfile>): String {
        val jsonArray = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("relationship", p.relationship.name)
            obj.put("avatarEmoji", p.avatarEmoji)
            obj.put("updatedAtTimestamp", p.updatedAtTimestamp)

            val sObj = JSONObject().apply {
                put("shoeUs", p.sizes.shoeUs)
                put("shoeEu", p.sizes.shoeEu)
                put("shoeCm", p.sizes.shoeCm)
                put("shirtSize", p.sizes.shirtSize)
                put("chestBust", p.sizes.chestBust)
                put("pantsWaist", p.sizes.pantsWaist)
                put("pantsInseam", p.sizes.pantsInseam)
                put("dressSize", p.sizes.dressSize)
                put("jacketSuit", p.sizes.jacketSuit)
                put("ringSize", p.sizes.ringSize)
                put("hatSize", p.sizes.hatSize)
                put("beltSize", p.sizes.beltSize)
            }
            obj.put("sizes", sObj)

            val notesArr = JSONArray()
            for (bn in p.brandNotes) {
                val nObj = JSONObject().apply {
                    put("id", bn.id)
                    put("brandName", bn.brandName)
                    put("note", bn.note)
                }
                notesArr.put(nObj)
            }
            obj.put("brandNotes", notesArr)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeProfiles(json: String): List<FamilyMemberProfile> {
        val list = mutableListOf<FamilyMemberProfile>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val relStr = obj.optString("relationship", RelationshipCategory.SELF.name)
            val rel = try {
                RelationshipCategory.valueOf(relStr)
            } catch (e: Exception) {
                RelationshipCategory.SELF
            }

            val sObj = obj.optJSONObject("sizes") ?: JSONObject()
            val sizes = SizeMeasurements(
                shoeUs = sObj.optString("shoeUs", ""),
                shoeEu = sObj.optString("shoeEu", ""),
                shoeCm = sObj.optString("shoeCm", ""),
                shirtSize = sObj.optString("shirtSize", ""),
                chestBust = sObj.optString("chestBust", ""),
                pantsWaist = sObj.optString("pantsWaist", ""),
                pantsInseam = sObj.optString("pantsInseam", ""),
                dressSize = sObj.optString("dressSize", ""),
                jacketSuit = sObj.optString("jacketSuit", ""),
                ringSize = sObj.optString("ringSize", ""),
                hatSize = sObj.optString("hatSize", ""),
                beltSize = sObj.optString("beltSize", "")
            )

            val notesList = mutableListOf<BrandFitNote>()
            val notesArr = obj.optJSONArray("brandNotes")
            if (notesArr != null) {
                for (j in 0 until notesArr.length()) {
                    val nObj = notesArr.getJSONObject(j)
                    notesList.add(
                        BrandFitNote(
                            id = nObj.optString("id", UUID.randomUUID().toString()),
                            brandName = nObj.optString("brandName", ""),
                            note = nObj.optString("note", "")
                        )
                    )
                }
            }

            list.add(
                FamilyMemberProfile(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Unnamed"),
                    relationship = rel,
                    avatarEmoji = obj.optString("avatarEmoji", "👤"),
                    sizes = sizes,
                    brandNotes = notesList,
                    updatedAtTimestamp = obj.optLong("updatedAtTimestamp", System.currentTimeMillis())
                )
            )
        }
        return list
    }
}