package com.example.cloble.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.domain.model.ActivityHistoryTab
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    onNavigateToMatchDetail: (Long) -> Unit,
    viewModel: ActivityHistoryViewModel = viewModel(factory = ActivityHistoryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var interestPendingCancel by remember { mutableStateOf<OutgoingInterestSummary?>(null) }
    var viewerPhoto by remember { mutableStateOf<LabeledPhoto?>(null) }
    var lockedWarningMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ActivityHistoryEvent.MutualMatchCreated -> onNavigateToMatchDetail(event.matchId)
            }
        }
    }

    val selectedTabIndex = if (uiState.selectedTab == ActivityHistoryTab.TERTARIK) 0 else 1

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Riwayat Aktivitas",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.actionMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CloBleStatusInfoBox(
                        tone = CloBleInfoBoxTone.SUCCESS,
                        title = "Berhasil",
                        message = uiState.actionMessage!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_action_banner")
                    )
                }
            }
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CloBleStatusInfoBox(
                        tone = CloBleInfoBoxTone.WARNING,
                        title = "Perhatian",
                        message = uiState.errorMessage!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_error_banner")
                    )
                }
            }
            if (lockedWarningMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CloBleStatusInfoBox(
                        tone = CloBleInfoBoxTone.WARNING,
                        title = "Detail Ditutup",
                        message = lockedWarningMessage!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_locked_warning_banner")
                    )
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = CloBleColors.Surface,
                contentColor = CloBleColors.Primary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false),
                        color = CloBleColors.Primary
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == ActivityHistoryTab.TERTARIK,
                    onClick = {
                        lockedWarningMessage = null
                        viewModel.selectTab(ActivityHistoryTab.TERTARIK)
                    },
                    text = {
                        Text(
                            text = "Tertarik (${uiState.outgoingInterests.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (uiState.selectedTab == ActivityHistoryTab.TERTARIK) CloBleColors.Primary else CloBleColors.TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_history_tertarik")
                )
                Tab(
                    selected = uiState.selectedTab == ActivityHistoryTab.DILEWATI,
                    onClick = {
                        lockedWarningMessage = null
                        viewModel.selectTab(ActivityHistoryTab.DILEWATI)
                    },
                    text = {
                        Text(
                            text = "Dilewati (${uiState.passedItems.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (uiState.selectedTab == ActivityHistoryTab.DILEWATI) CloBleColors.Primary else CloBleColors.TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_history_dilewati")
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CloBleColors.Primary)
                }
            } else {
                when (uiState.selectedTab) {
                    ActivityHistoryTab.TERTARIK -> {
                        OutgoingInterestsList(
                            interests = uiState.outgoingInterests,
                            cancellingInterestId = uiState.cancellingInterestId,
                            onItemClick = { itemId ->
                                lockedWarningMessage = null
                                onNavigateToItemDetail(itemId)
                            },
                            onLockedItemClick = { message ->
                                lockedWarningMessage = message
                            },
                            onViewMatchClick = { matchId ->
                                lockedWarningMessage = null
                                onNavigateToMatchDetail(matchId)
                            },
                            onCancelInterestRequest = {
                                lockedWarningMessage = null
                                interestPendingCancel = it
                            },
                            onPhotoClick = { uri, title -> viewerPhoto = LabeledPhoto(uri, title) },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
                    ActivityHistoryTab.DILEWATI -> {
                        PassedClothingList(
                            passedItems = uiState.passedItems,
                            processingPassItemIds = uiState.processingPassItemIds,
                            onItemClick = { itemId ->
                                lockedWarningMessage = null
                                onNavigateToItemDetail(itemId)
                            },
                            onLockedItemClick = { message ->
                                lockedWarningMessage = message
                            },
                            onConvertToInterest = { itemId ->
                                lockedWarningMessage = null
                                viewModel.convertPassToInterest(itemId)
                            },
                            onRestoreToDiscover = { itemId ->
                                lockedWarningMessage = null
                                viewModel.restorePassToDiscover(itemId)
                            },
                            onPhotoClick = { uri, title -> viewerPhoto = LabeledPhoto(uri, title) },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }

    val pendingCancel = interestPendingCancel
    if (pendingCancel != null) {
        AlertDialog(
            onDismissRequest = { interestPendingCancel = null },
            containerColor = CloBleColors.Surface,
            title = {
                Text(
                    text = "Batalkan Minat?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CloBleColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "Tawaran tertarik pada \"${pendingCancel.targetItemName}\" akan ditarik dan notifikasi ke ${pendingCancel.ownerName} akan dihapus. Pakaian akan muncul kembali di Jelajah.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloBleColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelOutgoingInterest(pendingCancel.interestId)
                        interestPendingCancel = null
                    },
                    modifier = Modifier.testTag("btn_confirm_cancel_interest")
                ) {
                    Text(
                        text = "Ya, Batalkan",
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.DangerText
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { interestPendingCancel = null },
                    modifier = Modifier.testTag("btn_dismiss_cancel_interest")
                ) {
                    Text(
                        text = "Kembali",
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.TextSecondary
                    )
                }
            }
        )
    }

    val activePhoto = viewerPhoto
    if (activePhoto != null) {
        CloBleFullscreenImageViewer(
            photos = listOf(activePhoto),
            initialIndex = 0,
            onDismiss = { viewerPhoto = null }
        )
    }
}

@Composable
private fun OutgoingInterestsList(
    interests: List<OutgoingInterestSummary>,
    cancellingInterestId: Long?,
    onItemClick: (Long) -> Unit,
    onLockedItemClick: (String) -> Unit,
    onViewMatchClick: (Long) -> Unit,
    onCancelInterestRequest: (OutgoingInterestSummary) -> Unit,
    onPhotoClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (interests.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag("empty_history_tertarik"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = CloBleIcons.HeartFilled,
                contentDescription = null,
                tint = CloBleColors.Outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Belum Ada Pakaian Tertarik",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pakaian yang kamu tandai tertarik saat menjelajahi feed Jelajah akan tercatat di sini.",
                style = MaterialTheme.typography.bodySmall,
                color = CloBleColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("list_history_tertarik"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(interests, key = { it.interestId }) { item ->
                OutgoingInterestCard(
                    item = item,
                    dateFormat = dateFormat,
                    isCancelling = cancellingInterestId == item.interestId,
                    onItemClick = onItemClick,
                    onLockedItemClick = onLockedItemClick,
                    onViewMatchClick = onViewMatchClick,
                    onCancelClick = { onCancelInterestRequest(item) },
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
private fun OutgoingInterestCard(
    item: OutgoingInterestSummary,
    dateFormat: SimpleDateFormat,
    isCancelling: Boolean,
    onItemClick: (Long) -> Unit,
    onLockedItemClick: (String) -> Unit,
    onViewMatchClick: (Long) -> Unit,
    onCancelClick: () -> Unit,
    onPhotoClick: (String, String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CloBleColors.Outline, RoundedCornerShape(16.dp))
            .clickable {
                if (item.isLockedByOtherMatch) {
                    onLockedItemClick("Detail pakaian ditutup karena sudah terikat atau selesai di Match pengguna lain.")
                } else {
                    onItemClick(item.targetItemId)
                }
            }
            .testTag("outgoing_interest_card_${item.interestId}"),
        color = CloBleColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CloBleColors.SecondarySurface)
                        .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                        .let { base ->
                            val photo = item.targetItemPhotoUri
                            if (!photo.isNullOrBlank() && !item.isLockedByOtherMatch) {
                                base.clickable { onPhotoClick(photo, item.targetItemName) }
                            } else {
                                base
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.targetItemPhotoUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.targetItemPhotoUri,
                            contentDescription = item.targetItemName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = CloBleIcons.ShirtOutline,
                            contentDescription = null,
                            tint = CloBleColors.Primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.targetItemName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.targetItemCategory} · Milik ${item.ownerName} (${item.ownerCity})",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloBleColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Diajukan: ${dateFormat.format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CloBleColors.TextSecondary
                    )
                    if (item.isLockedByOtherMatch) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detail & foto ditutup karena terikat match lain",
                            style = MaterialTheme.typography.labelSmall,
                            color = CloBleColors.DangerText
                        )
                    } else if (item.interestStatus == InterestStatus.DECLINED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tawaran barter ditolak oleh pemilik pakaian.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CloBleColors.DangerText
                        )
                    } else if (item.interestStatus == InterestStatus.INVALIDATED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pakaian sudah dihapus, ditukar, atau dinonaktifkan untuk barter.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CloBleColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusText, badgeBg, badgeTextColor) = when {
                    item.interestStatus == InterestStatus.PENDING && item.isLockedByOtherMatch ->
                        Triple("Tertahan di Match Lain", CloBleColors.SecondarySurface, CloBleColors.TextSecondary)
                    item.interestStatus == InterestStatus.PENDING ->
                        Triple("Menunggu Respon", CloBleColors.WarningContainer, CloBleColors.WarningText)
                    item.interestStatus == InterestStatus.ACCEPTED_MATCHED -> when (item.matchStatus) {
                        MatchStatus.COMPLETED -> Triple("Pertukaran Selesai", CloBleColors.PrimaryContainer, CloBleColors.OnPrimaryContainer)
                        MatchStatus.CANCELLED -> Triple("Match Dibatalkan", CloBleColors.DangerContainer, CloBleColors.DangerText)
                        else -> Triple("Berhasil Match!", CloBleColors.SuccessContainer, CloBleColors.SuccessText)
                    }
                    item.interestStatus == InterestStatus.RESOLVED_BY_MATCH -> {
                        if (item.matchId != null) {
                            when (item.matchStatus) {
                                MatchStatus.COMPLETED -> Triple("Pertukaran Selesai", CloBleColors.PrimaryContainer, CloBleColors.OnPrimaryContainer)
                                MatchStatus.CANCELLED -> Triple("Match Dibatalkan", CloBleColors.DangerContainer, CloBleColors.DangerText)
                                else -> Triple("Terhubung di Match", CloBleColors.PrimaryContainer, CloBleColors.OnPrimaryContainer)
                            }
                        } else {
                            Triple("Selesai di Match Lain", CloBleColors.SecondarySurface, CloBleColors.TextSecondary)
                        }
                    }
                    item.interestStatus == InterestStatus.DECLINED ->
                        Triple("Ditolak Pemilik", CloBleColors.DangerContainer, CloBleColors.DangerText)
                    item.interestStatus == InterestStatus.INVALIDATED ->
                        Triple("Dibatalkan Sistem", CloBleColors.SecondarySurface, CloBleColors.TextSecondary)
                    else ->
                        Triple("Tidak Tersedia", CloBleColors.SecondarySurface, CloBleColors.TextSecondary)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (item.interestStatus == InterestStatus.PENDING) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        enabled = !isCancelling,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CloBleColors.DangerText),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CloBleColors.DangerText
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("btn_cancel_interest_${item.interestId}")
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = CloBleColors.DangerText
                            )
                        } else {
                            Text(
                                text = "Batalkan Minat",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else if (item.matchId != null) {
                    CloBlePrimaryButton(
                        text = "Lihat Match",
                        onClick = { onViewMatchClick(item.matchId) },
                        modifier = Modifier
                            .width(136.dp)
                            .testTag("btn_view_match_${item.interestId}")
                    )
                }
            }
        }
    }
}

@Composable
private fun PassedClothingList(
    passedItems: List<PassedClothingSummary>,
    processingPassItemIds: Set<Long>,
    onItemClick: (Long) -> Unit,
    onLockedItemClick: (String) -> Unit,
    onConvertToInterest: (Long) -> Unit,
    onRestoreToDiscover: (Long) -> Unit,
    onPhotoClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (passedItems.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag("empty_history_dilewati"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = CloBleIcons.XClose,
                contentDescription = null,
                tint = CloBleColors.Outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Belum Ada Pakaian yang Dilewati",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pakaian yang kamu lewati di feed Jelajah akan tercatat di sini dan dapat dikembalikan ke feed kapan saja.",
                style = MaterialTheme.typography.bodySmall,
                color = CloBleColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("list_history_dilewati"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(passedItems, key = { it.clothingId }) { item ->
                PassedClothingCard(
                    item = item,
                    isProcessing = item.clothingId in processingPassItemIds,
                    dateFormat = dateFormat,
                    onItemClick = onItemClick,
                    onLockedItemClick = onLockedItemClick,
                    onConvertToInterest = onConvertToInterest,
                    onRestoreToDiscover = onRestoreToDiscover,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
private fun PassedClothingCard(
    item: PassedClothingSummary,
    isProcessing: Boolean,
    dateFormat: SimpleDateFormat,
    onItemClick: (Long) -> Unit,
    onLockedItemClick: (String) -> Unit,
    onConvertToInterest: (Long) -> Unit,
    onRestoreToDiscover: (Long) -> Unit,
    onPhotoClick: (String, String) -> Unit
) {
    val isLockedByMatchOrExchanged = item.isInActiveMatch || item.clothingStatus == ItemStatus.EXCHANGED
    val isUnavailable = isLockedByMatchOrExchanged || !item.isBarterAllowed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CloBleColors.Outline, RoundedCornerShape(16.dp))
            .clickable {
                if (isLockedByMatchOrExchanged) {
                    onLockedItemClick("Detail pakaian ditutup karena item sedang dalam Match aktif atau sudah ditukar.")
                } else {
                    onItemClick(item.clothingId)
                }
            }
            .testTag("passed_clothing_card_${item.clothingId}"),
        color = CloBleColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CloBleColors.SecondarySurface)
                        .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                        .let { base ->
                            val photo = item.clothingPhotoUri
                            if (!photo.isNullOrBlank() && !isLockedByMatchOrExchanged) {
                                base.clickable { onPhotoClick(photo, item.clothingName) }
                            } else {
                                base
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.clothingPhotoUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.clothingPhotoUri,
                            contentDescription = item.clothingName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = CloBleIcons.ShirtOutline,
                            contentDescription = null,
                            tint = CloBleColors.Primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.clothingName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.clothingCategory} · Milik ${item.ownerName} (${item.ownerCity})",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloBleColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dilewati: ${dateFormat.format(Date(item.passedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CloBleColors.TextSecondary
                    )
                    if (isUnavailable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLockedByMatchOrExchanged) {
                                "Sudah terikat match lain atau telah ditukar"
                            } else {
                                "Pemilik menonaktifkan opsi barter item ini"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = CloBleColors.DangerText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloBleOutlinedButton(
                    text = if (isUnavailable) "Hapus Riwayat" else "Kembalikan",
                    leadingIcon = if (isUnavailable) CloBleIcons.Trash else CloBleIcons.RotateCcw,
                    enabled = !isProcessing,
                    onClick = { onRestoreToDiscover(item.clothingId) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_restore_to_discover_${item.clothingId}")
                )

                CloBlePrimaryButton(
                    text = if (isUnavailable) "Tidak Tersedia" else "Kirim Tertarik",
                    leadingIcon = CloBleIcons.HeartFilled,
                    enabled = !isUnavailable && !isProcessing,
                    onClick = { onConvertToInterest(item.clothingId) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_convert_to_interest_${item.clothingId}")
                )
            }
        }
    }
}
