package com.example.cloble.ui.screens.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.components.WardrobeBarterBadge
import com.example.cloble.ui.components.WardrobeItemCard
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun WardrobeScreen(
    onNavigateToAddClothing: () -> Unit,
    onNavigateToEditClothing: (Long) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToChatList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: WardrobeViewModel = viewModel(factory = WardrobeViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Lemari Saya",
                actions = listOf(
                    CloBleTopBarAction(
                        icon = CloBleIcons.Bell,
                        contentDescription = "Notifikasi",
                        onClick = onNavigateToNotifications,
                        testTag = "btn_notifications_from_wardrobe"
                    )
                )
            )
        },
        bottomBar = {
            CloBleBottomNavBar(
                selectedTab = CloBleBottomTab.WARDROBE,
                onTabSelected = { tab ->
                    when (tab) {
                        CloBleBottomTab.DISCOVER -> onNavigateToDiscover()
                        CloBleBottomTab.WARDROBE -> {}
                        CloBleBottomTab.CHAT -> onNavigateToChatList()
                        CloBleBottomTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddClothing,
                containerColor = CloBleColors.Primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_clothing")
            ) {
                Icon(
                    imageVector = CloBleIcons.Plus,
                    contentDescription = "Tambah Pakaian",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Koleksi Lemari",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary
                    )
                    Text(
                        text = "${uiState.eligibleBarterCount} dari ${uiState.items.size} pakaian siap barter",
                        style = MaterialTheme.typography.labelSmall,
                        color = CloBleColors.Primary
                    )
                }
            }

            if (uiState.errorMessage != null) {
                CloBleStatusInfoBox(
                    tone = CloBleInfoBoxTone.HIGHLIGHT,
                    title = "Perhatian",
                    message = uiState.errorMessage!!,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .testTag("wardrobe_error_box")
                )
            }

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = CloBleColors.Primary)
                }
            } else if (uiState.items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = CloBleIcons.ShirtOutline,
                        contentDescription = null,
                        tint = CloBleColors.Outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Lemari Digital Masih Kosong",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CloBleColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tambahkan pakaian preloved Anda untuk mulai melakukan barter dengan pengguna lain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloBleColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("list_wardrobe_items"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.items, key = { it.item.id }) { summary ->
                        val item = summary.item
                        val badge = when {
                            summary.isInActiveMatch -> WardrobeBarterBadge.IN_ACTIVE_MATCH
                            item.isBarterAllowed -> WardrobeBarterBadge.ALLOWED
                            else -> WardrobeBarterBadge.NOT_FOR_BARTER
                        }

                        val statusNote = when {
                            item.status == ItemStatus.EXCHANGED -> "Status: Sudah Ditukar"
                            summary.isInActiveMatch -> "Terkunci dalam Match aktif"
                            summary.interestedSenderNames.isNotEmpty() ->
                                "Diminati oleh: ${summary.interestedSenderNames.joinToString(", ")}"
                            else -> null
                        }

                        WardrobeItemCard(
                            title = item.name,
                            metadata = "${item.category} • Ukuran ${item.size} • ${item.gender} • ${item.condition}",
                            barterBadge = badge,
                            statusNote = statusNote,
                            imageModel = item.mainPhotoUri,
                            photoPlaceholderLabel = item.name,
                            actionButtonText = if (summary.isInActiveMatch) "Terkunci" else "Kelola / Ubah",
                            onActionClick = {
                                onNavigateToEditClothing(item.id)
                            },
                            onCardClick = {
                                onNavigateToEditClothing(item.id)
                            },
                            modifier = Modifier.testTag("wardrobe_card_${item.id}")
                        )
                    }
                }
            }
        }

        if (uiState.itemPendingDelete != null) {
            DeleteClothingConfirmDialog(
                clothingName = uiState.itemPendingDelete!!.name,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::dismissDeleteDialog
            )
        }
    }
}
