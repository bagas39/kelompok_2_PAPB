package com.example.cloble.ui.screens.profile

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.components.WardrobeBarterBadge
import com.example.cloble.ui.components.WardrobeItemCard
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OtherUserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMatched: (Long) -> Unit,
    viewModel: OtherUserProfileViewModel = viewModel(factory = OtherUserProfileViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var viewerPhotos by remember { mutableStateOf<List<LabeledPhoto>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OtherUserProfileEvent.MatchCreated -> onNavigateToMatched(event.matchId)
                is OtherUserProfileEvent.ReplyRejected -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = if (uiState.isReplyingToInterest) "Pilih Pakaian Barter" else "Profil Pengguna",
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            if (uiState.isReplyingToInterest) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CloBleColors.Outline),
                    color = CloBleColors.Surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        CloBlePrimaryButton(
                            text = if (uiState.isSubmitting) "Memproses..." else "Terima & Buat Match",
                            onClick = viewModel::acceptInterestAndCreateMatch,
                            enabled = uiState.selectedItemId != null && !uiState.isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_accept_and_create_match")
                        )
                    }
                }
            }
        },
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CloBleColors.Primary)
            }
        } else {
            val user = uiState.profile
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (user != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CloBleColors.Surface,
                            border = BorderStroke(1.dp, CloBleColors.Outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                UserSummaryHeader(
                                    name = user.name,
                                    city = user.city,
                                    averageRating = user.averageRating,
                                    reviewCount = user.reviewCount,
                                    isLowCredibility = user.isLowCredibility,
                                    photoUri = user.avatarUri,
                                    onAvatarClick = if (!user.avatarUri.isNullOrBlank()) {
                                        { viewerPhotos = listOf(LabeledPhoto(user.avatarUri, user.name)) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.testTag("other_user_header")
                                )
                            }
                        }
                    }

                    if (uiState.errorMessage != null) {
                        item {
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.WARNING,
                                title = "Perhatian",
                                message = uiState.errorMessage!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("other_user_error_box")
                            )
                        }
                    }

                    if (uiState.isReplyingToInterest) {
                        item {
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.HIGHLIGHT,
                                title = "Pilih 1 Pakaian Barter",
                                message = "Ketuk kartu untuk memilih pakaian milik ${user.name}, atau ketuk ikon zoom foto untuk melihat detail.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reply_interest_instruction_box")
                            )
                        }

                        item {
                            Text(
                                text = "Kandidat Pakaian Barter (${uiState.wardrobeItems.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CloBleColors.TextPrimary
                            )
                        }

                        if (uiState.wardrobeItems.isEmpty()) {
                            item {
                                Text(
                                    text = "Pengguna ini saat ini tidak memiliki pakaian lain yang siap untuk dibarter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        } else {
                            val pairs = uiState.wardrobeItems.chunked(2)
                            items(pairs) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        val isSelected = uiState.selectedItemId == item.id
                                        val itemPhotos = buildOtherUserItemPhotos(item)
                                        WardrobeItemCard(
                                            title = item.name,
                                            metadata = "${item.category} · ${item.size} · ${item.condition}",
                                            barterBadge = WardrobeBarterBadge.ALLOWED,
                                            imageModel = item.mainPhotoUri ?: item.detailPhotoUri ?: item.extraPhotoUri,
                                            compactGridMode = true,
                                            isSelected = isSelected,
                                            actionButtonText = if (isSelected) "Terpilih" else "Pilih",
                                            onPhotoClick = if (itemPhotos.isNotEmpty()) {
                                                { viewerPhotos = itemPhotos }
                                            } else {
                                                null
                                            },
                                            onActionClick = { viewModel.selectItem(item.id) },
                                            onCardClick = { viewModel.selectItem(item.id) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("reply_candidate_item_${item.id}")
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        if (user.bio.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = CloBleColors.Surface,
                                    border = BorderStroke(1.dp, CloBleColors.Outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Bio",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = CloBleColors.TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = user.bio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CloBleColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Ulasan (${uiState.reviews.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CloBleColors.TextPrimary
                            )
                        }

                        if (uiState.reviews.isEmpty()) {
                            item {
                                Text(
                                    text = "Belum ada ulasan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        } else {
                            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            items(uiState.reviews, key = { it.id }) { review ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = CloBleColors.Surface,
                                    border = BorderStroke(1.dp, CloBleColors.Outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = review.reviewerName,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = CloBleColors.TextPrimary
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = CloBleIcons.StarFilled,
                                                    contentDescription = null,
                                                    tint = CloBleColors.StarRating,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "${review.rating}.0",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = CloBleColors.TextPrimary
                                                )
                                            }
                                        }
                                        Text(
                                            text = review.comment,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CloBleColors.TextSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Text(
                                            text = dateFormat.format(Date(review.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CloBleColors.TextSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

private fun buildOtherUserItemPhotos(item: ClothingItem): List<LabeledPhoto> {
    val result = mutableListOf<LabeledPhoto>()
    item.mainPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(it, "${item.name} · Foto Utama"))
    }
    item.detailPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(it, "${item.name} · Tampak Belakang"))
    }
    item.extraPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(it, "${item.name} · Detail Label"))
    }
    return result
}
