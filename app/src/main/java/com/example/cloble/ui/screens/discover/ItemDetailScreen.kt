package com.example.cloble.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.ClothingPhotoSlot
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOtherUserProfile: (Long) -> Unit,
    onNavigateToWardrobe: () -> Unit,
    viewModel: ItemDetailViewModel = viewModel(factory = ItemDetailViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Detail Pakaian",
                onNavigateBack = onNavigateBack
            )
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
            val item = uiState.item
            val owner = uiState.owner

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item != null) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ClothingPhotoSlot(
                            label = "Foto Utama",
                            imageModel = item.mainPhotoUri,
                            height = 200.dp,
                            modifier = Modifier.weight(1f)
                        )
                        ClothingPhotoSlot(
                            label = "Tampak Belakang",
                            imageModel = item.detailPhotoUri,
                            height = 200.dp,
                            modifier = Modifier.weight(1f)
                        )
                        ClothingPhotoSlot(
                            label = "Detail Label",
                            imageModel = item.extraPhotoUri,
                            height = 200.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary,
                        modifier = Modifier.testTag("detail_clothing_title")
                    )

                    Text(
                        text = "${item.category} • Ukuran ${item.size} • ${item.gender} • Warna ${item.color} • Kondisi ${item.condition}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CloBleColors.TextSecondary
                    )

                    if (item.description.isNotEmpty()) {
                        Text(
                            text = "Deskripsi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CloBleColors.TextPrimary
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CloBleColors.TextSecondary
                        )
                    }

                    if (owner != null) {
                        Text(
                            text = "Pemilik Pakaian",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CloBleColors.TextPrimary
                        )
                        UserSummaryHeader(
                            name = owner.name,
                            city = owner.city,
                            averageRating = owner.averageRating,
                            reviewCount = owner.reviewCount,
                            isLowCredibility = owner.isLowCredibility,
                            onProfileClick = { onNavigateToOtherUserProfile(owner.id) },
                            modifier = Modifier.testTag("detail_owner_summary")
                        )
                    }

                    if (uiState.interestSentSuccess) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.INFO,
                            title = "Tawaran Terkirim",
                            message = "Tawaran interest berhasil disampaikan kepada pemilik pakaian. Anda akan mendapat notifikasi ketika pemilik merespons.",
                            modifier = Modifier.fillMaxWidth().testTag("detail_interest_success_box")
                        )
                    } else if (uiState.errorMessage != null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.HIGHLIGHT,
                            title = "Perhatian",
                            message = uiState.errorMessage!!,
                            modifier = Modifier.fillMaxWidth().testTag("detail_error_box")
                        )
                        if (uiState.requiresBarterItemCta) {
                            CloBlePrimaryButton(
                                text = "Buka Lemari & Aktifkan Barter",
                                onClick = onNavigateToWardrobe,
                                modifier = Modifier.fillMaxWidth().testTag("btn_go_to_wardrobe_from_detail")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.isOwnItem) {
                        CloBlePrimaryButton(
                            text = "Pakaian Ini Milik Anda",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (uiState.interestSentSuccess) {
                        CloBlePrimaryButton(
                            text = "Interest Sudah Terkirim",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CloBlePrimaryButton(
                            text = if (uiState.isSendingInterest) "Mengirim Tawaran..." else "Kirim Tawaran Barter",
                            onClick = viewModel::sendInterest,
                            enabled = !uiState.isSendingInterest,
                            modifier = Modifier.fillMaxWidth().testTag("btn_send_interest_from_detail")
                        )
                    }
                }
            }
        }
    }
}
