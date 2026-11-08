package com.example.cloble.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.ui.components.ChatBubbleItem
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles

@Composable
fun ConversationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOtherUserProfile: (Long) -> Unit,
    onNavigateToRateUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = viewModel(factory = ConversationViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CloBleTopAppBar(
                title = "Percakapan",
                onNavigateBack = onNavigateBack,
                backContentDescription = "Kembali ke daftar chat"
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
                            text = "Match tidak ditemukan atau Anda bukan peserta.",
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
                            .testTag("conversation_screen_content")
                    ) {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CloBleColors.Background)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("conversation_items_badge")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "✓ Pertukaran Terverifikasi",
                                        style = CloBleTextStyles.BadgeSemiBold,
                                        color = CloBleColors.Primary
                                    )
                                    Text(
                                        text = "${detail.myItem.name} ↔ ${detail.partnerItem.name}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = CloBleColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!detail.meetingLocation.isNullOrBlank()) {
                                        Text(
                                            text = "Lokasi temu: ${detail.meetingLocation}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CloBleColors.TextSecondary
                                        )
                                    }
                                }
                            }

                            when (detail.status) {
                                MatchStatus.ACTIVE -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CloBlePrimaryButton(
                                            text = "Selesaikan Pertukaran",
                                            onClick = viewModel::openCompleteDialog,
                                            enabled = !uiState.isActionInProgress,
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .testTag("complete_match_button")
                                        )
                                        CloBleOutlinedButton(
                                            text = "Batalkan",
                                            onClick = viewModel::openCancelDialog,
                                            enabled = !uiState.isActionInProgress,
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .testTag("cancel_match_button")
                                        )
                                    }
                                }

                                MatchStatus.COMPLETED -> {
                                    CloBleStatusInfoBox(
                                        title = "Pertukaran Selesai",
                                        message = "Pertukaran pakaian telah berhasil diselesaikan. Ruang obrolan ditutup sebagai arsip riwayat (Read-Only).",
                                        tone = CloBleInfoBoxTone.INFO,
                                        modifier = Modifier.testTag("completed_status_info_box")
                                    )

                                    if (uiState.canRatePartner) {
                                        CloBlePrimaryButton(
                                            text = "★ Beri Rating & Ulasan",
                                            onClick = { onNavigateToRateUser(detail.matchId) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("rate_partner_button")
                                        )
                                    } else if (uiState.hasReviewed) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFE6F4EA),
                                            border = BorderStroke(1.dp, Color(0xFF137333)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "✓ Anda sudah memberikan ulasan untuk pertukaran ini.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = Color(0xFF137333),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                            )
                                        }
                                    }
                                }

                                MatchStatus.CANCELLED -> {
                                    CloBleStatusInfoBox(
                                        title = "Pertukaran Dibatalkan",
                                        message = "Pertukaran ini telah dibatalkan oleh salah satu pihak. Pakaian telah dikembalikan ke Lemari dan ruang obrolan ditutup sebagai arsip riwayat (Read-Only).",
                                        tone = CloBleInfoBoxTone.NEUTRAL,
                                        modifier = Modifier.testTag("cancelled_status_info_box")
                                    )
                                }
                            }

                            if (uiState.errorMessage != null) {
                                CloBleStatusInfoBox(
                                    title = "Pemberitahuan",
                                    message = uiState.errorMessage!!,
                                    tone = CloBleInfoBoxTone.HIGHLIGHT,
                                    modifier = Modifier.testTag("conversation_error_banner")
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(CloBleColors.Background)
                                .testTag("conversation_messages_column"),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            if (uiState.messages.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Kalian sudah saling match! Mulai obrolan untuk menentukan waktu dan lokasi barter pakaian.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CloBleColors.TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = uiState.messages,
                                    key = { it.id }
                                ) { msg ->
                                    ChatBubbleItem(
                                        messageText = msg.messageText,
                                        sentAt = msg.sentAt,
                                        isFromMe = msg.isFromMe,
                                        modifier = Modifier.testTag("chat_bubble_${msg.id}")
                                    )
                                }
                            }
                        }

                        if (uiState.isReadOnly) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_readonly_banner"),
                                color = CloBleColors.SecondarySurface,
                                border = BorderStroke(1.dp, CloBleColors.Outline)
                            ) {
                                Text(
                                    text = "Ruang obrolan read-only karena status pertukaran telah selesai/dibatalkan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = CloBleColors.Background,
                                border = BorderStroke(1.dp, CloBleColors.Outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = uiState.draftMessage,
                                        onValueChange = viewModel::onDraftChanged,
                                        placeholder = {
                                            Text(
                                                text = "Ketik pesan...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CloBleColors.TextSecondary
                                            )
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chat_input_text_field")
                                    )

                                    val canSend = uiState.draftMessage.isNotBlank() && !uiState.isActionInProgress

                                    IconButton(
                                        onClick = viewModel::sendMessage,
                                        enabled = canSend,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (canSend) CloBleColors.Primary else CloBleColors.SecondarySurface)
                                            .testTag("chat_send_button")
                                    ) {
                                        Icon(
                                            imageVector = CloBleIcons.Send,
                                            contentDescription = "Kirim pesan",
                                            tint = if (canSend) CloBleColors.OnPrimary else CloBleColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.showCancelDialog) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissCancelDialog,
                    title = {
                        Text(
                            text = "Batalkan Pertukaran?",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Text(
                            text = "Apakah Anda yakin ingin membatalkan pertukaran ini? Kedua pakaian akan dikembalikan ke status bebas di Lemari dan obrolan akan dikunci sebagai read-only.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CloBleColors.TextSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = viewModel::confirmCancelMatch,
                            modifier = Modifier.testTag("dialog_confirm_cancel_button")
                        ) {
                            Text(text = "Ya, Batalkan", color = Color(0xFFD93025))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::dismissCancelDialog,
                            modifier = Modifier.testTag("dialog_dismiss_cancel_button")
                        ) {
                            Text(text = "Kembali", color = CloBleColors.TextPrimary)
                        }
                    }
                )
            }

            if (uiState.showCompleteDialog) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissCompleteDialog,
                    title = {
                        Text(
                            text = "Selesaikan Pertukaran",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Pastikan Anda dan rekan barter telah bertemu dan bertukar pakaian. Status pakaian akan diperbarui menjadi 'Sudah ditukar'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CloBleColors.TextSecondary
                            )
                            CloBleLabeledTextField(
                                label = "Lokasi Pertemuan (Opsional)",
                                value = uiState.meetingLocationInput,
                                onValueChange = viewModel::onMeetingLocationChanged,
                                placeholder = "Contoh: Taman Balai Kota, Stasiun Bandung...",
                                modifier = Modifier.testTag("meeting_location_input")
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = viewModel::confirmCompleteExchange,
                            modifier = Modifier.testTag("dialog_confirm_complete_button")
                        ) {
                            Text(text = "Konfirmasi Selesai", color = CloBleColors.Primary)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::dismissCompleteDialog,
                            modifier = Modifier.testTag("dialog_dismiss_complete_button")
                        ) {
                            Text(text = "Batal", color = CloBleColors.TextPrimary)
                        }
                    }
                )
            }
        }
    }
}
