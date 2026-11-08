package com.example.cloble.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
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
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        CloBlePrimaryButton(
                            text = if (uiState.isSubmitting) "Memproses..." else "Terima & Buat Match",
                            onClick = viewModel::acceptInterestAndCreateMatch,
                            enabled = uiState.selectedItemId != null && !uiState.isSubmitting,
                            modifier = Modifier.fillMaxWidth().testTag("btn_accept_and_create_match")
                        )
                    }
                }
            }
        },
        containerColor = Color.White
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = CloBleColors.Primary)
            }
        } else {
            val user = uiState.profile
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (user != null) {
                    item {
                        UserSummaryHeader(
                            name = user.name,
                            city = user.city,
                            averageRating = user.averageRating,
                            reviewCount = user.reviewCount,
                            isLowCredibility = user.isLowCredibility,
                            modifier = Modifier.testTag("other_user_header")
                        )
                    }

                    if (uiState.errorMessage != null) {
                        item {
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.HIGHLIGHT,
                                title = "Perhatian",
                                message = uiState.errorMessage!!,
                                modifier = Modifier.fillMaxWidth().testTag("other_user_error_box")
                            )
                        }
                    }

                    if (uiState.isReplyingToInterest) {
                        item {
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.HIGHLIGHT,
                                title = "Pilih 1 Pakaian Barter",
                                message = "Pilih salah satu pakaian milik ${user.name} di bawah ini untuk menjadi barang barter dalam Match.",
                                modifier = Modifier.fillMaxWidth().testTag("reply_interest_instruction_box")
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
                            items(uiState.wardrobeItems, key = { it.id }) { item ->
                                val isSelected = uiState.selectedItemId == item.id
                                WardrobeItemCard(
                                    title = item.name,
                                    metadata = "${item.category} • Ukuran ${item.size} • ${item.gender} • ${item.condition}",
                                    barterBadge = WardrobeBarterBadge.ALLOWED,
                                    imageModel = item.mainPhotoUri,
                                    isSelected = isSelected,
                                    actionButtonText = if (isSelected) "✓ Terpilih" else "Pilih Pakaian Ini",
                                    onActionClick = { viewModel.selectItem(item.id) },
                                    onCardClick = { viewModel.selectItem(item.id) },
                                    modifier = Modifier.testTag("reply_candidate_item_${item.id}")
                                )
                            }
                        }
                    } else {

                        if (user.bio.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CloBleColors.SecondarySurface)
                                        .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "Bio",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = CloBleColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = user.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloBleColors.TextSecondary
                                    )
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = CloBleIcons.StarFilled,
                                                contentDescription = null,
                                                tint = CloBleColors.StarRating,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = " ${review.rating}",
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
}
