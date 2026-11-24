package com.example.cloble.ui.screens.matched

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.ClothingPhotoSlot
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.components.LabeledPhotoListSaver
import com.example.cloble.ui.theme.CloBleColors
import java.util.Locale

@Composable
fun MatchedScreen(
    onNavigateToConversation: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit,
    viewModel: MatchedViewModel = viewModel(factory = MatchedViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var viewerPhotos by rememberSaveable(stateSaver = LabeledPhotoListSaver) {
        mutableStateOf<List<LabeledPhoto>>(emptyList())
    }
    var viewerStartIndex by rememberSaveable { mutableIntStateOf(0) }
    val isCompactHeight = LocalConfiguration.current.screenHeightDp < 520

    Scaffold(
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
            val detail = uiState.matchDetail
            if (detail == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(CloBleColors.SecondarySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CloBleIcons.XClose,
                            contentDescription = "Match tidak tersedia",
                            tint = CloBleColors.TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Data Match Tidak Tersedia",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("matched_error_title")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Detail pertukaran tidak ditemukan atau Anda tidak memiliki akses ke Match ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CloBleColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    CloBlePrimaryButton(
                        text = "Kembali ke Jelajah",
                        onClick = onNavigateToDiscover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_back_to_discover_from_matched")
                    )
                }
            } else {
                val myItemPhoto = detail.myItem.primaryPhotoUri()
                val partnerItemPhoto = detail.partnerItem.primaryPhotoUri()
                val pairPhotos = remember(detail.myItem, detail.partnerItem) {
                    buildList {
                        myItemPhoto?.let {
                            add(LabeledPhoto(it, "Pakaian Anda · ${detail.myItem.name}"))
                        }
                        partnerItemPhoto?.let {
                            add(LabeledPhoto(it, "Milik ${detail.partnerName} · ${detail.partnerItem.name}"))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (isCompactHeight) 20.dp else 24.dp,
                            vertical = if (isCompactHeight) 10.dp else 32.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompactHeight) 48.dp else 80.dp)
                            .clip(CircleShape)
                            .background(CloBleColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CloBleIcons.HeartFilled,
                            contentDescription = "Match",
                            tint = CloBleColors.Primary,
                            modifier = Modifier.size(if (isCompactHeight) 26.dp else 42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 20.dp))

                    val headlineText = when (detail.status) {
                        MatchStatus.COMPLETED -> "Pertukaran Selesai!"
                        MatchStatus.CANCELLED -> "Match Dibatalkan"
                        MatchStatus.ACTIVE -> "Kalian saling tertarik!"
                    }
                    val subtitleText = when (detail.status) {
                        MatchStatus.COMPLETED -> "Pertukaran pakaian antara Anda dan ${detail.partnerName} telah selesai."
                        MatchStatus.CANCELLED -> "Match dengan ${detail.partnerName} ini telah dibatalkan. Anda dapat melihat riwayat obrolan atau menjelajahi pakaian lainnya."
                        MatchStatus.ACTIVE -> "Anda dan ${detail.partnerName} telah menyetujui pertukaran pakaian. Buka obrolan untuk mengatur lokasi dan waktu serah terima fisik."
                    }

                    Text(
                        text = headlineText,
                        style = (if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
                            .copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("matched_title")
                    )

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

                    Text(
                        text = subtitleText,
                        style = if (isCompactHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = CloBleColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CloBleColors.Surface,
                            border = BorderStroke(1.dp, CloBleColors.Outline),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("matched_my_item")
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isCompactHeight) 8.dp else 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Pakaian Anda",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = CloBleColors.OnPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 6.dp))
                                ClothingPhotoSlot(
                                    label = detail.myItem.name,
                                    imageModel = myItemPhoto,
                                    height = if (isCompactHeight) 80.dp else 136.dp,
                                    onClick = if (pairPhotos.isNotEmpty() && myItemPhoto != null) {
                                        {
                                            viewerStartIndex = 0
                                            viewerPhotos = pairPhotos
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                                Text(
                                    text = detail.myItem.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CloBleColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ukuran ${detail.myItem.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CloBleColors.PrimaryContainer,
                            border = BorderStroke(1.dp, CloBleColors.PrimarySoft),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("matched_partner_item")
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isCompactHeight) 8.dp else 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Milik ${detail.partnerName}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = CloBleColors.OnPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 6.dp))
                                ClothingPhotoSlot(
                                    label = detail.partnerItem.name,
                                    imageModel = partnerItemPhoto,
                                    height = if (isCompactHeight) 80.dp else 136.dp,
                                    onClick = if (pairPhotos.isNotEmpty() && partnerItemPhoto != null) {
                                        {
                                            viewerStartIndex = (pairPhotos.size - 1).coerceAtLeast(0)
                                            viewerPhotos = pairPhotos
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                                Text(
                                    text = detail.partnerItem.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CloBleColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ukuran ${detail.partnerItem.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${detail.partnerName} (${detail.partnerCity})",
                            style = (if (isCompactHeight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                                .copy(fontWeight = FontWeight.Medium),
                            color = CloBleColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Icon(
                            imageVector = CloBleIcons.StarFilled,
                            contentDescription = null,
                            tint = CloBleColors.StarRating,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (detail.partnerReviewCount > 0) {
                                String.format(Locale.US, " %.1f (%d)", detail.partnerAverageRating, detail.partnerReviewCount)
                            } else {
                                " Baru"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = CloBleColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 32.dp))

                    val chatButtonText = if (detail.status == MatchStatus.ACTIVE) "Buka Obrolan" else "Lihat Riwayat Obrolan"
                    if (isCompactHeight) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CloBlePrimaryButton(
                                text = chatButtonText,
                                onClick = { onNavigateToConversation(uiState.matchId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_open_chat_from_matched")
                            )
                            CloBleOutlinedButton(
                                text = "Kembali ke Jelajah",
                                onClick = onNavigateToDiscover,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_back_to_discover_from_matched")
                            )
                        }
                    } else {
                        CloBlePrimaryButton(
                            text = chatButtonText,
                            onClick = { onNavigateToConversation(uiState.matchId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_open_chat_from_matched")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        CloBleOutlinedButton(
                            text = "Kembali ke Jelajah",
                            onClick = onNavigateToDiscover,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_back_to_discover_from_matched")
                        )
                    }
                }
            }
        }

        if (viewerPhotos.isNotEmpty()) {
            CloBleFullscreenImageViewer(
                photos = viewerPhotos,
                initialIndex = viewerStartIndex,
                onDismiss = { viewerPhotos = emptyList() }
            )
        }
    }
}

private fun ClothingItem.primaryPhotoUri(): String? =
    mainPhotoUri?.takeIf { it.isNotBlank() }
        ?: detailPhotoUri?.takeIf { it.isNotBlank() }
        ?: extraPhotoUri?.takeIf { it.isNotBlank() }
