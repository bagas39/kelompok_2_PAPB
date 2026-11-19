package com.example.cloble.ui.screens.wardrobe

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.components.LabeledPhoto
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
    var viewerPhotos by remember { mutableStateOf<List<LabeledPhoto>>(emptyList()) }

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
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

                if (uiState.items.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CloBleColors.PrimaryContainer,
                        border = BorderStroke(1.dp, CloBleColors.Outline)
                    ) {
                        Text(
                            text = "${uiState.items.size} Item",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CloBleColors.OnPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                CloBleStatusInfoBox(
                    tone = CloBleInfoBoxTone.WARNING,
                    title = "Perhatian",
                    message = uiState.errorMessage!!,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("wardrobe_error_box")
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CloBleColors.Primary)
                }
            } else if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CloBleColors.Surface,
                        border = BorderStroke(1.dp, CloBleColors.Outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CloBleColors.PrimaryContainer,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = CloBleIcons.ShirtOutline,
                                        contentDescription = null,
                                        tint = CloBleColors.OnPrimaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Lemari Digital Masih Kosong",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tambahkan pakaian preloved pertamamu untuk mulai mencari teman barter di sekitarmu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CloBleColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            CloBlePrimaryButton(
                                text = "Tambah Pakaian Pertama",
                                leadingIcon = CloBleIcons.Plus,
                                onClick = onNavigateToAddClothing
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("list_wardrobe_items"),
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.items, key = { it.item.id }) { summary ->
                        val item = summary.item
                        val badge = when {
                            summary.isInActiveMatch -> WardrobeBarterBadge.IN_ACTIVE_MATCH
                            item.isBarterAllowed -> WardrobeBarterBadge.ALLOWED
                            else -> WardrobeBarterBadge.NOT_FOR_BARTER
                        }

                        val statusNote = when {
                            item.status == ItemStatus.EXCHANGED -> "Sudah Ditukar"
                            summary.interestedSenderNames.isNotEmpty() ->
                                "Diminati: ${summary.interestedSenderNames.joinToString(", ")}"
                            else -> null
                        }

                        val itemPhotos = remember(item.mainPhotoUri, item.detailPhotoUri, item.extraPhotoUri, item.name) {
                            buildWardrobePhotos(item)
                        }

                        WardrobeItemCard(
                            title = item.name,
                            metadata = "${item.category} · ${item.size} · ${item.condition}",
                            barterBadge = badge,
                            statusNote = statusNote,
                            imageModel = item.mainPhotoUri ?: item.detailPhotoUri ?: item.extraPhotoUri,
                            photoPlaceholderLabel = item.name,
                            compactGridMode = true,
                            actionButtonText = if (summary.isInActiveMatch) "Terkunci" else "Kelola",
                            onPhotoClick = if (itemPhotos.isNotEmpty()) {
                                { viewerPhotos = itemPhotos }
                            } else {
                                null
                            },
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

        if (viewerPhotos.isNotEmpty()) {
            CloBleFullscreenImageViewer(
                photos = viewerPhotos,
                initialIndex = 0,
                onDismiss = { viewerPhotos = emptyList() }
            )
        }
    }
}

private fun buildWardrobePhotos(item: ClothingItem): List<LabeledPhoto> {
    val result = mutableListOf<LabeledPhoto>()
    item.mainPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(uri = it, label = "${item.name} · Foto Utama"))
    }
    item.detailPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(uri = it, label = "${item.name} · Tampak Belakang"))
    }
    item.extraPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(uri = it, label = "${item.name} · Detail Label"))
    }
    return result
}
