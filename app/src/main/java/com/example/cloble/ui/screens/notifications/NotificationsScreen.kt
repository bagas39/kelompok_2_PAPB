package com.example.cloble.ui.screens.notifications

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationType
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReplyInterest: (Long, Long) -> Unit,
    onNavigateToConversation: (Long) -> Unit,
    onNavigateToRateUser: (Long, Long) -> Unit,
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Notifikasi",
                onNavigateBack = onNavigateBack
            )
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
        } else if (uiState.notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = CloBleIcons.Bell,
                    contentDescription = null,
                    tint = CloBleColors.Outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Belum Ada Notifikasi",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CloBleColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tawaran barter baru, verifikasi match, dan aktivitas lainnya akan muncul di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloBleColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("list_notifications"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.notifications, key = { it.id }) { item ->
                    val bgColor = if (!item.isRead) CloBleColors.PrimaryContainer else CloBleColors.Surface
                    val borderColor = if (!item.isRead) CloBleColors.PrimarySoft else CloBleColors.Outline

                    val cardClickModifier = when {
                        item.type == NotificationType.INCOMING_INTEREST &&
                            item.canReplyToInterest &&
                            item.counterpartUserId != null &&
                            item.relatedInterestId != null -> {
                            Modifier.clickable {
                                viewModel.markNotificationRead(item.id)
                                onNavigateToReplyInterest(item.counterpartUserId, item.relatedInterestId)
                            }
                        }
                        item.type == NotificationType.MATCH_VERIFIED &&
                            item.relatedMatchId != null -> {
                            Modifier.clickable {
                                onNavigateToConversation(item.relatedMatchId)
                            }
                        }
                        item.type == NotificationType.EXCHANGE_COMPLETED &&
                            !item.hasReviewedMatch &&
                            item.relatedMatchId != null -> {
                            Modifier.clickable {
                                onNavigateToRateUser(item.relatedMatchId, item.counterpartUserId ?: -1L)
                            }
                        }
                        else -> Modifier
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .then(cardClickModifier)
                            .testTag("notification_item_${item.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CloBleColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = dateFormat.format(Date(item.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }

                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CloBleColors.TextSecondary
                            )

                            when (item.type) {
                                NotificationType.INCOMING_INTEREST -> {
                                    if (item.canReplyToInterest && item.counterpartUserId != null && item.relatedInterestId != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        CloBlePrimaryButton(
                                            text = "Pilih Pakaian Barter",
                                            onClick = {
                                                viewModel.markNotificationRead(item.id)
                                                onNavigateToReplyInterest(item.counterpartUserId, item.relatedInterestId)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("btn_reply_interest_${item.id}")
                                        )
                                    } else if (!item.disabledReasonText.isNullOrEmpty()) {
                                        Text(
                                            text = item.disabledReasonText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = CloBleColors.TextSecondary
                                        )
                                    }
                                }
                                NotificationType.MATCH_VERIFIED -> {
                                    when (item.matchStatus) {
                                        MatchStatus.CANCELLED -> {
                                            Text(
                                                text = "Match telah dibatalkan",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = CloBleColors.DangerText
                                            )
                                        }
                                        MatchStatus.COMPLETED -> {
                                            Text(
                                                text = "Pertukaran telah selesai",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = CloBleColors.SuccessText
                                            )
                                            if (!item.hasReviewedMatch && item.relatedMatchId != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                CloBleOutlinedButton(
                                                    text = "Beri Ulasan",
                                                    onClick = {
                                                        onNavigateToRateUser(item.relatedMatchId, item.counterpartUserId ?: -1L)
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("btn_rate_match_${item.id}")
                                                )
                                            }
                                        }
                                        else -> {
                                            if (item.relatedMatchId != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                CloBlePrimaryButton(
                                                    text = "Buka Ruang Obrolan",
                                                    onClick = {
                                                        onNavigateToConversation(item.relatedMatchId)
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("btn_open_chat_${item.id}")
                                                )
                                            }
                                        }
                                    }
                                }
                                NotificationType.EXCHANGE_COMPLETED -> {
                                    if (item.hasReviewedMatch) {
                                        Text(
                                            text = "Ulasan sudah dikirim",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = CloBleColors.SuccessText
                                        )
                                    } else if (item.relatedMatchId != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        CloBleOutlinedButton(
                                            text = "Beri Ulasan",
                                            onClick = {
                                                onNavigateToRateUser(item.relatedMatchId, item.counterpartUserId ?: -1L)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("btn_rate_user_${item.id}")
                                        )
                                    }
                                }
                                NotificationType.MATCH_CANCELLED -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
