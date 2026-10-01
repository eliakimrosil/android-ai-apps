package com.aistudio.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Default.Add
import androidx.compose.material.icons.Icons.Default.Check
import androidx.compose.material.icons.Icons.Default.Close
import androidx.compose.material.icons.Icons.Default.Info
import androidx.compose.material.icons.Icons.Default.Settings
import androidx.compose.material.icons.Icons.Default.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SizeVaultTheme {
                SizeVaultMainScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Modern Material You Dark Color Scheme for SizeVault
 */
@Composable
fun SizeVaultTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF81D4FA),
        onPrimary = Color(0xFF003547),
        primaryContainer = Color(0xFF004D65),
        onPrimaryContainer = Color(0xFFBEE9FF),
        secondary = Color(0xFFFFB74D),
        onSecondary = Color(0xFF452B00),
        secondaryContainer = Color(0xFF633F00),
        onSecondaryContainer = Color(0xFFFFDDB3),
        surface = Color(0xFF121417),
        onSurface = Color(0xFFE2E2E6),
        surfaceVariant = Color(0xFF1E232A),
        onSurfaceVariant = Color(0xFFC3C7CF),
        outline = Color(0xFF4B535E),
        background = Color(0xFF0C0E11),
        onBackground = Color(0xFFE2E2E6)
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeVaultMainScreen(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val filteredProfiles = remember(uiState.profiles, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.profiles
        } else {
            uiState.profiles.filter {
                it.name.contains(uiState.searchQuery, ignoreCase = true) ||
                        it.relationship.displayName.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    val selectedProfile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SizeVault",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (uiState.isProUnlocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (uiState.isProUnlocked) "PRO" else "FREE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isProUnlocked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setProPassDialogVisible(true)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pro Pass & Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setAboutDialogVisible(true)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About App",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openAddProfileDialog()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Family Member")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search family member or sizes...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )

            // Family Profiles Grid / Carousel
            Text(
                text = "FAMILY PROFILES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Start
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProfiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == uiState.selectedProfileId
                    ProfileAvatarChip(
                        profile = profile,
                        isSelected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectProfile(profile.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Visual Size Card Details
            if (selectedProfile != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item {
                        ProfileHeaderSummary(
                            profile = selectedProfile,
                            onEditClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openEditProfileDialog(selectedProfile)
                            },
                            onShareClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val shareText = viewModel.generateShareCardText(selectedProfile)
                                shareTextViaSystemSheet(context, shareText)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "OFFICIAL MEASUREMENTS & BADGES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            textAlign = TextAlign.Start
                        )
                    }

                    item {
                        VisualSizeCardsGrid(profile = selectedProfile)
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BRAND FIT QUIRKS & NOTES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openBrandNoteDialog()
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Brand Note", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (selectedProfile.brandNotes.isEmpty()) {
                        item {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No brand notes yet",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add quirks like 'Runs small in Nike (US 10.5)' or 'Prefers loose fit'",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(selectedProfile.brandNotes, key = { it.id }) { note ->
                            BrandNoteItem(
                                note = note,
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.removeBrandNote(note.id)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteProfile(selectedProfile.id)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete ${selectedProfile.name}'s Profile")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("No profiles found", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap the '+' button below to add your first family member's sizing guide.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet: Add or Edit Profile
    if (uiState.showAddEditDialog) {
        ProfileEditBottomSheet(
            initialProfile = uiState.editingProfile,
            onDismiss = { viewModel.dismissAddEditDialog() },
            onSave = { updatedProfile ->
                viewModel.saveProfile(updatedProfile)
            }
        )
    }

    // Dialog: Add Brand Note
    if (uiState.showBrandNoteDialog) {
        AddBrandNoteDialog(
            onDismiss = { viewModel.dismissBrandNoteDialog() },
            onAdd = { brand, note ->
                viewModel.addBrandNoteToSelectedProfile(brand, note)
            }
        )
    }

    // Commercial Pro Pass Dialog
    if (uiState.showProPassDialog) {
        LifetimeProPassDialog(
            isPro = uiState.isProUnlocked,
            onDismiss = { viewModel.setProPassDialogVisible(false) },
            onUnlockPro = { viewModel.unlockProPass() }
        )
    }

    // About Dialog
    if (uiState.showAboutDialog) {
        AboutSizeVaultDialog(
            onDismiss = { viewModel.setAboutDialogVisible(false) }
        )
    }
}

/**
 * Family Profile Avatar Chip
 */
@Composable
fun ProfileAvatarChip(
    profile: FamilyMemberProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(2.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.avatarEmoji,
                fontSize = 28.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = profile.name,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = profile.relationship.displayName,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Top summary card for the selected profile with share & edit buttons
 */
@Composable
fun ProfileHeaderSummary(
    profile: FamilyMemberProfile,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(profile.avatarEmoji, fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = profile.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = profile.relationship.displayName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Edit Sizes")
                }
                Button(
                    onClick = onShareClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Share Card ↗")
                }
            }
        }
    }
}

/**
 * Grid of Material 3 Visual Sizing Badges
 */
@Composable
fun VisualSizeCardsGrid(profile: FamilyMemberProfile) {
    val s = profile.sizes

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Shoe Badge (Combined US, EU, CM)
        SizeDetailBadge(
            iconEmoji = "👟",
            category = "Shoe Sizes",
            primaryValue = if (s.shoeUs.isNotEmpty()) "US ${s.shoeUs}" else "Not Set",
            secondaryValue = listOfNotNull(
                s.shoeEu.takeIf { it.isNotEmpty() }?.let { "EU $it" },
                s.shoeCm.takeIf { it.isNotEmpty() }?.let { "$it cm" }
            ).joinToString("  •  ")
        )

        // Tops & Shirts
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "👕",
                    label = "Shirt / Top",
                    value = s.shirtSize.ifEmpty { "--" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "📐",
                    label = "Chest / Bust",
                    value = s.chestBust.ifEmpty { "--" }
                )
            }
        }

        // Pants: Waist & Inseam
        SizeDetailBadge(
            iconEmoji = "👖",
            category = "Pants & Jeans",
            primaryValue = if (s.pantsWaist.isNotEmpty() || s.pantsInseam.isNotEmpty()) {
                "${s.pantsWaist.ifEmpty { "?" }}W  ×  ${s.pantsInseam.ifEmpty { "?" }}L"
            } else "Not Set",
            secondaryValue = "Waist / Inseam measurements"
        )

        // Dress & Jacket
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "👗",
                    label = "Dress",
                    value = s.dressSize.ifEmpty { "--" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "🧥",
                    label = "Jacket / Suit",
                    value = s.jacketSuit.ifEmpty { "--" }
                )
            }
        }

        // Accessories: Ring, Hat, Belt
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "💍",
                    label = "Ring",
                    value = s.ringSize.ifEmpty { "--" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "🧢",
                    label = "Hat",
                    value = s.hatSize.ifEmpty { "--" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                CompactSizeBadge(
                    iconEmoji = "🧣",
                    label = "Belt",
                    value = s.beltSize.ifEmpty { "--" }
                )
            }
        }
    }
}

@Composable
fun SizeDetailBadge(
    iconEmoji: String,
    category: String,
    primaryValue: String,
    secondaryValue: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(iconEmoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = category,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = primaryValue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                if (secondaryValue.isNotEmpty()) {
                    Text(
                        text = secondaryValue,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun CompactSizeBadge(
    iconEmoji: String,
    label: String,
    value: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(iconEmoji, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Brand Note Item Card with Quick Delete
 */
@Composable
fun BrandNoteItem(
    note: BrandFitNote,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = note.brandName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = note.note,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove Brand Note",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Profile Edit BottomSheet with all category inputs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditBottomSheet(
    initialProfile: FamilyMemberProfile?,
    onDismiss: () -> Unit,
    onSave: (FamilyMemberProfile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var relationship by remember { mutableStateOf(initialProfile?.relationship ?: RelationshipCategory.CHILD) }
    var emoji by remember { mutableStateOf(initialProfile?.avatarEmoji ?: "👤") }

    val initialSizes = initialProfile?.sizes ?: SizeMeasurements()
    var shoeUs by remember { mutableStateOf(initialSizes.shoeUs) }
    var shoeEu by remember { mutableStateOf(initialSizes.shoeEu) }
    var shoeCm by remember { mutableStateOf(initialSizes.shoeCm) }
    var shirtSize by remember { mutableStateOf(initialSizes.shirtSize) }
    var chestBust by remember { mutableStateOf(initialSizes.chestBust) }
    var pantsWaist by remember { mutableStateOf(initialSizes.pantsWaist) }
    var pantsInseam by remember { mutableStateOf(initialSizes.pantsInseam) }
    var dressSize by remember { mutableStateOf(initialSizes.dressSize) }
    var jacketSuit by remember { mutableStateOf(initialSizes.jacketSuit) }
    var ringSize by remember { mutableStateOf(initialSizes.ringSize) }
    var hatSize by remember { mutableStateOf(initialSizes.hatSize) }
    var beltSize by remember { mutableStateOf(initialSizes.beltSize) }

    val emojiChoices = listOf("🧑‍💻", "👩", "👦", "👧", "👶", "👴", "👵", "🐶", "👤")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (initialProfile != null) "Edit ${initialProfile.name}" else "New Family Member",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Emoji selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emojiChoices.forEach { em ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (emoji == em) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                emoji = em
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(em, fontSize = 22.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name or Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Relationship Pills
            Text(
                text = "Relationship",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RelationshipCategory.values().forEach { rel ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (relationship == rel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            relationship = rel
                        }
                    ) {
                        Text(
                            text = rel.displayName,
                            color = if (relationship == rel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Footwear & Shoes",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = shoeUs,
                    onValueChange = { shoeUs = it },
                    label = { Text("US Shoe") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = shoeEu,
                    onValueChange = { shoeEu = it },
                    label = { Text("EU Shoe") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = shoeCm,
                    onValueChange = { shoeCm = it },
                    label = { Text("CM") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Tops & Outerwear",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = shirtSize,
                    onValueChange = { shirtSize = it },
                    label = { Text("Shirt / Top (S/M/L)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = chestBust,
                    onValueChange = { chestBust = it },
                    label = { Text("Chest / Bust") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = jacketSuit,
                    onValueChange = { jacketSuit = it },
                    label = { Text("Jacket / Suit") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = dressSize,
                    onValueChange = { dressSize = it },
                    label = { Text("Dress Size") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Pants & Accessories",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pantsWaist,
                    onValueChange = { pantsWaist = it },
                    label = { Text("Waist") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = pantsInseam,
                    onValueChange = { pantsInseam = it },
                    label = { Text("Inseam") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ringSize,
                    onValueChange = { ringSize = it },
                    label = { Text("Ring") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = hatSize,
                    onValueChange = { hatSize = it },
                    label = { Text("Hat") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = beltSize,
                    onValueChange = { beltSize = it },
                    label = { Text("Belt") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val base = initialProfile ?: FamilyMemberProfile(name = name.ifEmpty { "Family Member" }, relationship = relationship)
                    val updated = base.copy(
                        name = name.ifEmpty { "Family Member" },
                        relationship = relationship,
                        avatarEmoji = emoji,
                        sizes = SizeMeasurements(
                            shoeUs = shoeUs.trim(),
                            shoeEu = shoeEu.trim(),
                            shoeCm = shoeCm.trim(),
                            shirtSize = shirtSize.trim(),
                            chestBust = chestBust.trim(),
                            pantsWaist = pantsWaist.trim(),
                            pantsInseam = pantsInseam.trim(),
                            dressSize = dressSize.trim(),
                            jacketSuit = jacketSuit.trim(),
                            ringSize = ringSize.trim(),
                            hatSize = hatSize.trim(),
                            beltSize = beltSize.trim()
                        )
                    )
                    coroutineScope.launch {
                        sheetState.hide()
                        onSave(updated)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Profile", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Add Brand Fit Quirk Dialog
 */
@Composable
fun AddBrandNoteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var brand by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val suggestions = listOf("Nike", "Adidas", "Zara", "Levi's", "Lululemon", "H&M", "Birkenstock")

    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add Brand Fit Note",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Suggestion chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { sug ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { brand = sug }
                        ) {
                            Text(
                                text = sug,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Fit Quirk / Note") },
                    placeholder = { Text("e.g. Runs half-size small (buy 10.5)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (brand.isNotBlank() && note.isNotBlank()) {
                                onAdd(brand, note)
                            }
                        },
                        enabled = brand.isNotBlank() && note.isNotBlank()
                    ) {
                        Text("Save Note")
                    }
                }
            }
        }
    }
}

/**
 * Commercial Lifetime Pro Pass Dialog
 */
@Composable
fun LifetimeProPassDialog(
    isPro: Boolean,
    onDismiss: () -> Unit,
    onUnlockPro: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👑 SizeVault Pro Pass",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Never buy the wrong gift or holiday outfit size again.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                ProPerkRow(icon = "♾️", title = "Unlimited Family Profiles", desc = "Track kids, in-laws, friends & colleagues")
                ProPerkRow(icon = "🏷️", title = "Brand Fit Vault", desc = "Unlimited store & designer fit quirks")
                ProPerkRow(icon = "📲", title = "One-Tap Instant Card Export", desc = "Formatted WhatsApp & SMS size cards")
                ProPerkRow(icon = "🔒", title = "Zero-Cloud Private Vault", desc = "100% offline, private, and encrypted on-device")

                Spacer(modifier = Modifier.height(20.dp))

                if (isPro) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Pro Pass Active (Lifetime License)",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onUnlockPro,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text("Unlock Lifetime Pro • $4.99", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ProPerkRow(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * About Dialog showcasing version & offline guarantee
 */
@Composable
fun AboutSizeVaultDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SizeVault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v2.4.0 • Build 34",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SizeVault is built for shoppers, parents, and thoughtful gift-givers. Never guess shoe or pants sizes during Black Friday or surprise birthdays.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛡️ 100% Offline & Private",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your measurements are never sent to any external server. Data remains on your device.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
                    context.startActivity(intent)
                }) {
                    Text("Privacy Policy & Terms", fontSize = 12.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

/**
 * System OS Intent Integration: Real Android Sharesheet
 */
fun shareTextViaSystemSheet(context: Context, text: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share SizeVault Card via...")
    context.startActivity(shareIntent)
}