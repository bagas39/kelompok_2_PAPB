package com.example.cloble.ui.screens.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    onNavigateToConversation: (Long) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToWardrobe: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = viewModel(factory = ChatListViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CloBleTopAppBar(
                title = "Chat",
                actions = listOf(
                    CloBleTopBarAction(
                        icon = CloBleIcons.Bell,
                        contentDescription = "Notifikasi",
                        onClick = onNavigateToNotifications,
                        showBadgeDot = uiState.unreadNotificationCount > 0,
                        testTag = "chat_list_notification_button"
                    )
                )
            )
        },
        bottomBar = {
            CloBleBottomNavBar(
                selectedTab = CloBleBottomTab.CHAT,
                onTabSelected = { tab ->
                    when (tab) {
                        CloBleBottomTab.DISCOVER -> onNavigateToDiscover()
                        CloBleBottomTab.WARDROBE -> onNavigateToWardrobe()
                        CloBleBottomTab.CHAT -> {  }
                        CloBleBottomTab.PROFILE -> onNavigateToProfile()
                    }
                }
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

                uiState.threads.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                            .testTag("chat_list_empty_state"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CloBleColors.PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CloBleIcons.ChatOutline,
                                contentDescription = null,
                                tint = CloBleColors.Primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Belum Ada Percakapan",
                            style = MaterialTheme.typography.titleLarge,
                            color = CloBleColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Mulai jelajah pakaian dan kirim minat barter. Setelah Match terbentuk, percakapan Anda akan muncul di sini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CloBleColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_list_threads_column"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.threads,
                            key = { it.matchId }
                        ) { thread ->
                            ChatThreadCard(
                                thread = thread,
                                onClick = { onNavigateToConversation(thread.matchId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadCard(
    thread: ChatThreadSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = thread.lastMessageSentAt?.let { timeFormat.format(Date(it)) } ?: ""

    val (statusLabel, statusBg, statusTextColor) = when (thread.status) {
        MatchStatus.ACTIVE -> Triple("Aktif", CloBleColors.PrimaryContainer, CloBleColors.Primary)
        MatchStatus.COMPLETED -> Triple("Selesai", Color(0xFFE6F4EA), Color(0xFF137333))
        MatchStatus.CANCELLED -> Triple("Dibatalkan", CloBleColors.SecondarySurface, CloBleColors.TextSecondary)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CloBleColors.Background,
        border = BorderStroke(1.dp, CloBleColors.Outline),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("chat_item_${thread.matchId}")
            .semantics(mergeDescendants = true) {
                contentDescription = "Percakapan dengan ${thread.partnerName}, status $statusLabel"
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(CloBleColors.SecondarySurface)
                        .border(1.dp, CloBleColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CloBleIcons.UserOutline,
                        contentDescription = null,
                        tint = CloBleColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = thread.partnerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CloBleColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusBg,
                            border = BorderStroke(1.dp, if (thread.status == MatchStatus.ACTIVE) CloBleColors.Primary else CloBleColors.Outline)
                        ) {
                            Text(
                                text = statusLabel,
                                style = CloBleTextStyles.BadgeSemiBold.copy(fontSize = 11.sp),
                                color = statusTextColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = thread.partnerCity,
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )

                        if (thread.partnerAverageRating > 0.0) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = CloBleColors.TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = CloBleIcons.StarFilled,
                                    contentDescription = null,
                                    tint = CloBleColors.StarRating,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", thread.partnerAverageRating),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CloBleColors.PrimaryContainer,
                border = BorderStroke(1.dp, CloBleColors.Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✓ Match · ${thread.myItemName} ↔ ${thread.partnerItemName}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = CloBleColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = thread.lastMessageText ?: "Belum ada pesan...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (thread.lastMessageText != null) CloBleColors.TextPrimary else CloBleColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (formattedTime.isNotBlank()) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = CloBleColors.TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (thread.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(CloBleColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${thread.unreadCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CloBleColors.OnPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
