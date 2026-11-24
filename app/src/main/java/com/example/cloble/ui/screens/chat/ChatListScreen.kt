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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleConstrainedContent
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLoadingState
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.components.cloBleFocusRing
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                        CloBleBottomTab.CHAT -> {}
                        CloBleBottomTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        },
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        CloBleConstrainedContent(
            maxWidth = 680.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CloBleColors.Background)
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CloBleLoadingState("Memuat percakapan...")
                }

                uiState.errorMessage != null && uiState.threads.isEmpty() -> {
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
                                    modifier = Modifier.testTag("chat_list_error_box")
                                )
                                CloBlePrimaryButton(
                                    text = "Muat Ulang",
                                    onClick = viewModel::retry,
                                    modifier = Modifier.testTag("btn_retry_chat_list")
                                )
                            }
                        }
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
                                tint = CloBleColors.OnPrimaryContainer,
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
                            key = { it.partnerUserId }
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
}

@Composable
private fun ChatThreadCard(
    thread: ChatThreadSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(thread.lastMessageSentAt) {
        formatChatThreadTimestamp(thread.lastMessageSentAt)
    }

    val (statusLabel, statusBg, statusTextColor) = when (thread.status) {
        MatchStatus.ACTIVE -> Triple("Aktif", CloBleColors.PrimaryContainer, CloBleColors.OnPrimaryContainer)
        MatchStatus.COMPLETED -> Triple("Selesai", CloBleColors.SuccessContainer, CloBleColors.SuccessText)
        MatchStatus.CANCELLED -> Triple("Dibatalkan", CloBleColors.SecondarySurface, CloBleColors.TextSecondary)
    }

    val statusBorderColor = when (thread.status) {
        MatchStatus.ACTIVE -> CloBleColors.PrimarySoft
        MatchStatus.COMPLETED -> CloBleColors.SuccessText
        MatchStatus.CANCELLED -> CloBleColors.Outline
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CloBleColors.Surface,
        border = BorderStroke(1.dp, CloBleColors.Outline),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .cloBleFocusRing(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CloBleColors.PrimaryContainer)
                        .border(1.dp, CloBleColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!thread.partnerAvatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = thread.partnerAvatarUri,
                            contentDescription = "Foto profil ${thread.partnerName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val initials = computeChatInitials(thread.partnerName)
                        if (initials != null) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = CloBleColors.OnPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = CloBleIcons.UserOutline,
                                contentDescription = "Avatar pengguna",
                                tint = CloBleColors.OnPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            border = BorderStroke(1.dp, statusBorderColor)
                        ) {
                            Text(
                                text = statusLabel,
                                style = CloBleTextStyles.BadgeSemiBold.copy(fontSize = 11.sp),
                                color = statusTextColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
                            color = CloBleColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (thread.partnerAverageRating > 0.0) {
                            Text(
                                text = "·",
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

                        if (thread.totalMatchesWithPartner > 1) {
                            Text(
                                text = "· ${thread.totalMatchesWithPartner} riwayat",
                                style = MaterialTheme.typography.labelSmall,
                                color = CloBleColors.Primary
                            )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = CloBleIcons.SwapHorizontal,
                        contentDescription = null,
                        tint = CloBleColors.OnPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${thread.myItemName} - ${thread.partnerItemName}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CloBleColors.OnPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
                            .size(22.dp)
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

private fun computeChatInitials(name: String): String? {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> null
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

internal fun formatChatThreadTimestamp(
    timestampMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (timestampMillis == null || timestampMillis <= 0L) return ""
    val messageCal = Calendar.getInstance(timeZone, locale).apply {
        timeInMillis = timestampMillis
    }
    val nowCal = Calendar.getInstance(timeZone, locale).apply {
        timeInMillis = nowMillis
    }
    val sameYear = messageCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
    val sameDay = sameYear && messageCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "dd MMM"
        else -> "dd/MM/yy"
    }
    return SimpleDateFormat(pattern, locale).apply {
        this.timeZone = timeZone
    }.format(Date(timestampMillis))
}
