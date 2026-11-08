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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.domain.model.ActivityHistoryTab
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
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

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Riwayat Aktivitas",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = Color.White
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
                        tone = CloBleInfoBoxTone.HIGHLIGHT,
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
                        tone = CloBleInfoBoxTone.NEUTRAL,
                        title = "Perhatian",
                        message = uiState.errorMessage!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_error_banner")
                    )
                }
            }

            TabRow(
                selectedTabIndex = if (uiState.selectedTab == ActivityHistoryTab.TERTARIK) 0 else 1,
                containerColor = Color.White,
                contentColor = CloBleColors.Primary,
                indicator = { tabPositions ->
                    val index = if (uiState.selectedTab == ActivityHistoryTab.TERTARIK) 0 else 1
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[index]),
                        color = CloBleColors.Primary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == ActivityHistoryTab.TERTARIK,
                    onClick = { viewModel.selectTab(ActivityHistoryTab.TERTARIK) },
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
                    onClick = { viewModel.selectTab(ActivityHistoryTab.DILEWATI) },
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
                            onItemClick = onNavigateToItemDetail,
                            onViewMatchClick = onNavigateToMatchDetail,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
                    ActivityHistoryTab.DILEWATI -> {
                        PassedClothingList(
                            passedItems = uiState.passedItems,
                            onItemClick = onNavigateToItemDetail,
                            onConvertToInterest = viewModel::convertPassToInterest,
                            onRestoreToDiscover = viewModel::restorePassToDiscover,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutgoingInterestsList(
    interests: List<OutgoingInterestSummary>,
    onItemClick: (Long) -> Unit,
    onViewMatchClick: (Long) -> Unit,
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
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Belum Ada Pakaian Tertarik",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pakaian yang kamu tandai tertarik saat menjelajahi feed Discover akan tercatat di sini.",
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
                    onItemClick = onItemClick,
                    onViewMatchClick = onViewMatchClick
                )
            }
        }
    }
}

@Composable
private fun OutgoingInterestCard(
    item: OutgoingInterestSummary,
    dateFormat: SimpleDateFormat,
    onItemClick: (Long) -> Unit,
    onViewMatchClick: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CloBleColors.Outline, RoundedCornerShape(16.dp))
            .clickable { onItemClick(item.targetItemId) }
            .testTag("outgoing_interest_card_${item.interestId}"),
        color = Color.White
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
                        .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp)),
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
                            modifier = Modifier.size(32.dp)
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
                        text = "${item.targetItemCategory} • Milik ${item.ownerName} (${item.ownerCity})",
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
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusText, badgeBg, badgeTextColor) = when (item.interestStatus) {
                    InterestStatus.PENDING -> Triple("Menunggu Respon", Color(0xFFFEF3C7), Color(0xFFD97706))
                    InterestStatus.ACCEPTED_MATCHED -> Triple("Berhasil Match!", Color(0xFFD1FAE5), Color(0xFF059669))
                    InterestStatus.RESOLVED_BY_MATCH -> Triple("Selesai di Match Lain", Color(0xFFF3F4F6), Color(0xFF4B5563))
                    InterestStatus.DECLINED -> Triple("Ditolak", Color(0xFFFEE2E2), Color(0xFFDC2626))
                    InterestStatus.INVALIDATED -> Triple("Tidak Tersedia", Color(0xFFF3F4F6), Color(0xFF9CA3AF))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (item.matchId != null) {
                    CloBlePrimaryButton(
                        text = "Lihat Match",
                        onClick = { onViewMatchClick(item.matchId) },
                        modifier = Modifier
                            .width(130.dp)
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
    onItemClick: (Long) -> Unit,
    onConvertToInterest: (Long) -> Unit,
    onRestoreToDiscover: (Long) -> Unit,
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
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Belum Ada Pakaian yang Dilewati",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pakaian yang kamu swipe lewati di feed Discover akan tercatat di sini dan dapat dikembalikan ke feed kapan saja.",
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
                    dateFormat = dateFormat,
                    onItemClick = onItemClick,
                    onConvertToInterest = onConvertToInterest,
                    onRestoreToDiscover = onRestoreToDiscover
                )
            }
        }
    }
}

@Composable
private fun PassedClothingCard(
    item: PassedClothingSummary,
    dateFormat: SimpleDateFormat,
    onItemClick: (Long) -> Unit,
    onConvertToInterest: (Long) -> Unit,
    onRestoreToDiscover: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CloBleColors.Outline, RoundedCornerShape(16.dp))
            .clickable { onItemClick(item.clothingId) }
            .testTag("passed_clothing_card_${item.clothingId}"),
        color = Color.White
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
                        .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp)),
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
                            modifier = Modifier.size(32.dp)
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
                        text = "${item.clothingCategory} • Milik ${item.ownerName} (${item.ownerCity})",
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
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloBleOutlinedButton(
                    text = "Kembalikan",
                    leadingIcon = CloBleIcons.RotateCcw,
                    onClick = { onRestoreToDiscover(item.clothingId) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_restore_to_discover_${item.clothingId}")
                )

                CloBlePrimaryButton(
                    text = "Kirim Tertarik",
                    leadingIcon = CloBleIcons.HeartFilled,
                    onClick = { onConvertToInterest(item.clothingId) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_convert_to_interest_${item.clothingId}")
                )
            }
        }
    }
}
