package com.example.cloble.ui.screens.discover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.theme.CloBleColors
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DiscoverScreen(
    onNavigateToFilter: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    onNavigateToWardrobe: () -> Unit,
    onNavigateToChatList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var animatedOffset by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var isSubmitting by remember { mutableStateOf(false) }

    val candidate = uiState.currentCandidate

    LaunchedEffect(candidate?.item?.id) {
        dragOffset = 0f
        animatedOffset = 0f
        isAnimating = false
        isSubmitting = false
    }

    val currentOffset = if (isAnimating) animatedOffset else dragOffset

    fun animateOffsetTo(target: Float, onEnd: () -> Unit = {}) {
        isAnimating = true
        coroutineScope.launch {
            Animatable(dragOffset).animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 180)
            ) {
                animatedOffset = value
            }
            onEnd()
            dragOffset = 0f
            animatedOffset = 0f
            isAnimating = false
        }
    }

    fun springBack() {
        isAnimating = true
        coroutineScope.launch {
            Animatable(dragOffset).animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) {
                animatedOffset = value
            }
            dragOffset = 0f
            animatedOffset = 0f
            isAnimating = false
        }
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "CloBle",
                actions = listOf(
                    CloBleTopBarAction(
                        icon = CloBleIcons.FilterFunnel,
                        contentDescription = "Filter",
                        onClick = onNavigateToFilter,
                        testTag = "btn_filter"
                    ),
                    CloBleTopBarAction(
                        icon = CloBleIcons.Clock,
                        contentDescription = "Riwayat Aktivitas",
                        onClick = onNavigateToHistory,
                        testTag = "btn_history_from_discover"
                    ),
                    CloBleTopBarAction(
                        icon = CloBleIcons.Bell,
                        contentDescription = "Notifikasi",
                        onClick = onNavigateToNotifications,
                        showBadgeDot = uiState.unreadNotificationCount > 0,
                        testTag = "btn_notifications"
                    )
                )
            )
        },
        bottomBar = {
            CloBleBottomNavBar(
                selectedTab = CloBleBottomTab.DISCOVER,
                onTabSelected = { tab ->
                    when (tab) {
                        CloBleBottomTab.DISCOVER -> {}
                        CloBleBottomTab.WARDROBE -> onNavigateToWardrobe()
                        CloBleBottomTab.CHAT -> onNavigateToChatList()
                        CloBleBottomTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (uiState.bannerMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    CloBleStatusInfoBox(
                        tone = CloBleInfoBoxTone.HIGHLIGHT,
                        title = "Informasi",
                        message = uiState.bannerMessage!!,
                        modifier = Modifier.fillMaxWidth().testTag("discover_info_box")
                    )
                    if (uiState.requiresBarterItemCta) {
                        Spacer(modifier = Modifier.height(6.dp))
                        CloBlePrimaryButton(
                            text = "Buka Lemari & Aktifkan Barter",
                            onClick = onNavigateToWardrobe,
                            modifier = Modifier.fillMaxWidth().testTag("btn_go_to_wardrobe_from_banner")
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CloBleColors.Primary)
                }
            } else {
                if (candidate != null) {

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { cardWidthPx = it.width.toFloat() }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = currentOffset
                                    rotationZ = (currentOffset / 60f).coerceIn(-15f, 15f)
                                }
                                .pointerInput(candidate.item.id) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { },
                                        onDragEnd = {
                                            if (isSubmitting || isAnimating) return@detectHorizontalDragGestures
                                            val threshold = if (cardWidthPx > 0f) cardWidthPx * 0.35f else 250f
                                            if (dragOffset > threshold) {
                                                isSubmitting = true
                                                animateOffsetTo(if (cardWidthPx > 0f) cardWidthPx * 1.5f else 1000f) {
                                                    viewModel.likeCurrentCandidate()
                                                    isSubmitting = false
                                                }
                                            } else if (dragOffset < -threshold) {
                                                isSubmitting = true
                                                animateOffsetTo(if (cardWidthPx > 0f) -cardWidthPx * 1.5f else -1000f) {
                                                    viewModel.passCurrentCandidate()
                                                    isSubmitting = false
                                                }
                                            } else {
                                                springBack()
                                            }
                                        },
                                        onDragCancel = {
                                            springBack()
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            if (!isSubmitting && !isAnimating) {
                                                dragOffset += dragAmount
                                            }
                                        }
                                    )
                                }
                                .shadow(6.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, CloBleColors.Outline, RoundedCornerShape(20.dp))
                                .clickable(enabled = !isSubmitting && !isAnimating) { onNavigateToItemDetail(candidate.item.id) }
                                .testTag("discover_candidate_card"),
                            color = Color.White
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(CloBleColors.SecondarySurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!candidate.item.mainPhotoUri.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = candidate.item.mainPhotoUri,
                                                contentDescription = candidate.item.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = CloBleIcons.ShirtOutline,
                                                contentDescription = null,
                                                tint = CloBleColors.Primary,
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color.Black.copy(alpha = 0.65f)
                                        ) {
                                            Text(
                                                text = "${candidate.distanceKm} km",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = candidate.item.name,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = CloBleColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Text(
                                            text = "${candidate.item.category} • Ukuran ${candidate.item.size} • ${candidate.item.gender} • ${candidate.item.condition}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CloBleColors.TextSecondary
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = CloBleIcons.UserOutline,
                                                contentDescription = null,
                                                tint = CloBleColors.TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "${candidate.ownerName} (${candidate.ownerCity})",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = CloBleColors.TextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = CloBleIcons.StarFilled,
                                                contentDescription = null,
                                                tint = CloBleColors.StarRating,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1f", candidate.ownerAverageRating),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = CloBleColors.TextPrimary
                                            )
                                        }
                                    }
                                }

                                val threshold = if (cardWidthPx > 0f) cardWidthPx * 0.35f else 250f
                                val likeAlpha = (currentOffset / (threshold * 0.6f)).coerceIn(0f, 1f)
                                val passAlpha = (-currentOffset / (threshold * 0.6f)).coerceIn(0f, 1f)

                                if (likeAlpha > 0.05f) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(24.dp)
                                            .graphicsLayer {
                                                alpha = likeAlpha
                                                rotationZ = -15f
                                            }
                                            .border(3.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .testTag("badge_tertarik")
                                    ) {
                                        Text(
                                            text = "TERTARIK",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 2.sp
                                            ),
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }

                                if (passAlpha > 0.05f) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(24.dp)
                                            .graphicsLayer {
                                                alpha = passAlpha
                                                rotationZ = 15f
                                            }
                                            .border(3.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .testTag("badge_lewati")
                                    ) {
                                        Text(
                                            text = "LEWATI",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 2.sp
                                            ),
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        IconButton(
                            onClick = {
                                if (!isSubmitting && !isAnimating) {
                                    isSubmitting = true
                                    animateOffsetTo(if (cardWidthPx > 0f) -cardWidthPx * 1.5f else -1000f) {
                                        viewModel.passCurrentCandidate()
                                        isSubmitting = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.5.dp, Color(0xFFE5E7EB), CircleShape)
                                .testTag("btn_swipe_pass")
                        ) {
                            Icon(
                                imageVector = CloBleIcons.XClose,
                                contentDescription = "Lewati pakaian",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (!isSubmitting && !isAnimating) {
                                    isSubmitting = true
                                    animateOffsetTo(if (cardWidthPx > 0f) cardWidthPx * 1.5f else 1000f) {
                                        viewModel.likeCurrentCandidate()
                                        isSubmitting = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(CloBleColors.Primary)
                                .testTag("btn_swipe_like")
                        ) {
                            Icon(
                                imageVector = CloBleIcons.HeartFilled,
                                contentDescription = "Kirim tawaran barter",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = CloBleIcons.ShirtOutline,
                            contentDescription = null,
                            tint = CloBleColors.Outline,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tidak Ada Pakaian Tersedia",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CloBleColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Semua pakaian di sekitarmu sudah habis atau tidak sesuai dengan filter saat ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        CloBlePrimaryButton(
                            text = "Sesuaikan Filter",
                            onClick = onNavigateToFilter,
                            modifier = Modifier.testTag("btn_adjust_filter_from_empty")
                        )
                    }
                }
            }
        }
    }
}
