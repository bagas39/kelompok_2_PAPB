package com.example.cloble.ui.screens.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.StarRatingSelector
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles

@Composable
fun RateUserScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOtherUserProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RateUserViewModel = viewModel(factory = RateUserViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CloBleTopAppBar(
                title = "Beri Rating & Ulasan",
                onNavigateBack = onNavigateBack,
                backContentDescription = "Kembali ke percakapan"
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CloBleColors.Background)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CloBleColors.Primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                uiState.matchDetail == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Data pertukaran tidak ditemukan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CloBleColors.TextSecondary
                        )
                    }
                }

                else -> {
                    val detail = uiState.matchDetail!!

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                            .testTag("rate_user_screen_content"),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {

                        UserSummaryHeader(
                            name = detail.partnerName,
                            city = detail.partnerCity,
                            averageRating = detail.partnerAverageRating,
                            reviewCount = detail.partnerReviewCount,
                            isLowCredibility = detail.partnerAverageRating in 0.1..<3.0 && detail.partnerReviewCount > 0,
                            onProfileClick = { onNavigateToOtherUserProfile(detail.partnerUserId) }
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CloBleColors.PrimaryContainer,
                            border = BorderStroke(1.dp, CloBleColors.Outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Pertukaran: ${detail.myItem.name} ↔ ${detail.partnerItem.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CloBleColors.Primary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        if (uiState.isSuccess) {

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE6F4EA),
                                border = BorderStroke(1.dp, Color(0xFF137333)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rate_success_box")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "✓ Ulasan Berhasil Dikirim!",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF137333)
                                    )
                                    Text(
                                        text = "Terima kasih telah memberikan ulasan. Ulasan Anda membantu membangun reputasi dan keamanan komunitas CloBle.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF137333)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            CloBlePrimaryButton(
                                text = "Lihat Profil ${detail.partnerName}",
                                onClick = { onNavigateToOtherUserProfile(detail.partnerUserId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("view_partner_profile_button")
                            )

                            CloBleOutlinedButton(
                                text = "Kembali ke Percakapan",
                                onClick = onNavigateBack,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CloBleColors.Background,
                                border = BorderStroke(1.dp, CloBleColors.Outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Bagaimana Pengalaman Barter Anda?",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = CloBleColors.TextPrimary,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = "Beri penilaian untuk ${detail.partnerName} berdasarkan kebersihan pakaian, ketepatan waktu, dan kelancaran komunikasi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloBleColors.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )

                                    StarRatingSelector(
                                        rating = uiState.selectedRating,
                                        onRatingSelected = viewModel::selectRating,
                                        enabled = !uiState.alreadyReviewed && !uiState.isSubmitting,
                                        modifier = Modifier.testTag("star_rating_selector")
                                    )

                                    val ratingDesc = when (uiState.selectedRating) {
                                        5 -> "Sangat Puas (5/5)"
                                        4 -> "Puas (4/5)"
                                        3 -> "Cukup (3/5)"
                                        2 -> "Kurang Memuaskan (2/5)"
                                        1 -> "Sangat Buruk (1/5)"
                                        else -> "Ketuk bintang untuk memberi nilai"
                                    }

                                    Text(
                                        text = ratingDesc,
                                        style = CloBleTextStyles.BadgeSemiBold,
                                        color = if (uiState.selectedRating > 0) CloBleColors.Primary else CloBleColors.TextSecondary
                                    )
                                }
                            }

                            CloBleLabeledTextField(
                                label = "Tulis Ulasan (Opsional)",
                                value = uiState.comment,
                                onValueChange = viewModel::onCommentChanged,
                                placeholder = "Ceritakan kondisi pakaian yang Anda terima dan pengalaman barter...",
                                singleLine = false,
                                minHeight = 100.dp,
                                enabled = !uiState.alreadyReviewed && !uiState.isSubmitting,
                                modifier = Modifier.testTag("review_comment_input")
                            )

                            if (uiState.alreadyReviewed) {
                                CloBleStatusInfoBox(
                                    title = "Sudah Diulas",
                                    message = "Anda sudah mengirimkan ulasan untuk pertukaran ini. Setiap pengguna hanya dapat memberikan tepat 1 ulasan per Match.",
                                    tone = CloBleInfoBoxTone.NEUTRAL,
                                    modifier = Modifier.testTag("already_reviewed_info_box")
                                )
                            }

                            if (uiState.errorMessage != null) {
                                CloBleStatusInfoBox(
                                    title = "Peringatan",
                                    message = uiState.errorMessage!!,
                                    tone = CloBleInfoBoxTone.HIGHLIGHT,
                                    modifier = Modifier.testTag("rate_user_error_banner")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val canSubmit = uiState.selectedRating in 1..5 && !uiState.alreadyReviewed && !uiState.isSubmitting

                            CloBlePrimaryButton(
                                text = if (uiState.isSubmitting) "Mengirim Ulasan..." else "Kirim Ulasan",
                                onClick = viewModel::submitReview,
                                enabled = canSubmit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("submit_review_button")
                            )
                        }
                    }
                }
            }
        }
    }
}
