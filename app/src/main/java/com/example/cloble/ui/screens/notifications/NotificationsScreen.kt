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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.NotificationType
import com.example.cloble.ui.components.CloBleConstrainedContent
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLoadingState
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.cloBleFocusRing
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class NotificationCardAction {
    NONE,
    REPLY_INTEREST,
    OPEN_CONVERSATION,
    RATE_USER
}

internal fun resolveNotificationCardAction(item: NotificationFeedItem): NotificationCardAction = when {
    item.type == NotificationType.INCOMING_INTEREST &&
        item.canReplyToInterest &&
        item.counterpartUserId != null &&
        item.relatedInterestId != null -> NotificationCardAction.REPLY_INTEREST
    item.type == NotificationType.MATCH_VERIFIED &&
        item.relatedMatchId != null &&
        item.matchStatus == MatchStatus.COMPLETED ->
        if (!item.hasReviewedMatch) NotificationCardAction.RATE_USER else NotificationCardAction.NONE
    item.type == NotificationType.MATCH_VERIFIED &&
        item.relatedMatchId != null &&
        item.matchStatus != MatchStatus.CANCELLED -> NotificationCardAction.OPEN_CONVERSATION
    item.type == NotificationType.EXCHANGE_COMPLETED &&
        !item.hasReviewedMatch &&
        item.relatedMatchId != null -> NotificationCardAction.RATE_USER
    else -> NotificationCardAction.NONE
}

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
        CloBleConstrainedContent(
            maxWidth = 680.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        if (uiState.isLoading) {
            CloBleLoadingState("Memuat notifikasi...")
        } else if (uiState.errorMessage != null && uiState.notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = CloBleColors.Surface,
                    border = BorderStroke(1.dp, CloBleColors.Outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.WARNING,
                            title = "Gagal Memuat",
                            message = uiState.errorMessage!!,
                            modifier = Modifier.testTag("notifications_error_box")
                        )
                        CloBlePrimaryButton(
                            text = "Muat Ulang",
                            onClick = viewModel::retry,
                            modifier = Modifier.testTag("btn_retry_notifications")
                        )
                    }
                }
            }
        } else if (uiState.notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    .testTag("list_notifications"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.notifications, key = { it.id }) { item ->
                    val bgColor = if (!item.isRead) CloBleColors.PrimaryContainer else CloBleColors.Surface
                    val borderColor = if (!item.isRead) CloBleColors.PrimarySoft else CloBleColors.Outline

                    val cardClickModifier = when (resolveNotificationCardAction(item)) {
                        NotificationCardAction.REPLY_INTEREST -> {
                            Modifier.clickable(role = Role.Button) {
                                viewModel.markNotificationRead(item.id)
                                onNavigateToReplyInterest(item.counterpartUserId!!, item.relatedInterestId!!)
                            }
                        }
                        NotificationCardAction.OPEN_CONVERSATION -> {
                            Modifier.clickable(role = Role.Button) {
                                onNavigateToConversation(item.relatedMatchId!!)
                            }
                        }
                        NotificationCardAction.RATE_USER -> {
                            Modifier.clickable(role = Role.Button) {
                                onNavigateToRateUser(item.relatedMatchId!!, item.counterpartUserId ?: -1L)
                            }
                        }
                        NotificationCardAction.NONE -> Modifier
                    }

                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .cloBleFocusRing(MaterialTheme.shapes.large)
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
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = CloBleColors.TextSecondary
                                        )
                                    }
                                }
                                NotificationType.MATCH_VERIFIED -> {
                                    when (item.matchStatus) {
                                        MatchStatus.CANCELLED -> {
                                            Text(
                                                text = "Match telah dibatalkan",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = CloBleColors.DangerText
                                            )
                                        }
                                        MatchStatus.COMPLETED -> {
                                            Text(
                                                text = "Pertukaran telah selesai",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
}
