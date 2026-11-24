package com.example.cloble.ui.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.util.DistanceCalculator
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleConstrainedContent
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLoadingState
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.components.LabeledPhotoListSaver
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOtherUserProfile: (Long) -> Unit,
    onNavigateToWardrobe: () -> Unit,
    onNavigateToMatch: (Long) -> Unit = {},
    viewModel: ItemDetailViewModel = viewModel(factory = ItemDetailViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var viewerPhotos by rememberSaveable(stateSaver = LabeledPhotoListSaver) {
        mutableStateOf<List<LabeledPhoto>>(emptyList())
    }
    var viewerInitialIndex by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Detail Pakaian",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        CloBleConstrainedContent(
            maxWidth = 640.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        if (uiState.isLoading) {
            CloBleLoadingState("Memuat detail pakaian...")
        } else {
            val item = uiState.item
            val owner = uiState.owner

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item != null && !uiState.isOwnItem && uiState.isLockedByOtherMatch) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CloBleColors.Surface,
                        border = BorderStroke(1.dp, CloBleColors.Outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_locked_privacy_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.WARNING,
                                title = "Detail Pakaian Tidak Tersedia",
                                message = "Pakaian ini sudah terikat atau selesai ditukar dalam match pengguna lain, sehingga detail item dan profil pemilik tidak lagi ditampilkan.",
                                modifier = Modifier.fillMaxWidth()
                            )
                            CloBleOutlinedButton(
                                text = "Kembali",
                                onClick = onNavigateBack,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else if (item != null) {
                    val itemPhotos = remember(item.mainPhotoUri, item.detailPhotoUri, item.extraPhotoUri, item.name) {
                        buildList {
                            item.mainPhotoUri?.takeIf { it.isNotBlank() }?.let {
                                add(LabeledPhoto(it, "${item.name} · Foto Utama"))
                            }
                            item.detailPhotoUri?.takeIf { it.isNotBlank() }?.let {
                                add(LabeledPhoto(it, "${item.name} · Tampak Belakang"))
                            }
                            item.extraPhotoUri?.takeIf { it.isNotBlank() }?.let {
                                add(LabeledPhoto(it, "${item.name} · Detail Label"))
                            }
                        }
                    }

                    if (itemPhotos.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { itemPhotos.size })

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CloBleColors.SecondarySurface)
                                    .border(1.dp, CloBleColors.Outline, RoundedCornerShape(16.dp))
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    AsyncImage(
                                        model = itemPhotos[page].uri,
                                        contentDescription = itemPhotos[page].label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(role = Role.Button) {
                                                viewerInitialIndex = page
                                                viewerPhotos = itemPhotos
                                            }
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = CloBleColors.DarkScrim.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${itemPhotos.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(7.dp)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable(role = Role.Button) {
                                            viewerInitialIndex = pagerState.currentPage
                                            viewerPhotos = itemPhotos
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(CloBleColors.DarkScrim.copy(alpha = 0.72f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CloBleIcons.ZoomIn,
                                            contentDescription = "Perbesar foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (itemPhotos.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    repeat(itemPhotos.size) { index ->
                                        val isActive = pagerState.currentPage == index
                                        Box(
                                            modifier = Modifier
                                                .size(width = if (isActive) 18.dp else 7.dp, height = 7.dp)
                                                .clip(CircleShape)
                                                .background(if (isActive) CloBleColors.Primary else CloBleColors.Outline)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CloBleColors.SecondarySurface)
                                .border(1.dp, CloBleColors.Outline, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = CloBleIcons.ShirtOutline,
                                    contentDescription = null,
                                    tint = CloBleColors.Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Belum ada foto pakaian",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CloBleColors.Surface,
                        border = BorderStroke(1.dp, CloBleColors.Outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = CloBleColors.TextPrimary,
                                modifier = Modifier.testTag("detail_clothing_title")
                            )

                            val distanceKm = uiState.distanceKm
                            if (!uiState.isOwnItem && distanceKm != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = CloBleIcons.MapPin,
                                        contentDescription = null,
                                        tint = CloBleColors.Primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Sekitar ${DistanceCalculator.formatDistanceLabel(distanceKm)} dari lokasi Anda",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CloBleColors.TextSecondary,
                                        modifier = Modifier.testTag("detail_distance_label")
                                    )
                                }
                            }

                            val specs = listOf(
                                "Kategori" to item.category,
                                "Ukuran" to item.size,
                                "Pengguna" to item.gender,
                                "Warna" to item.color,
                                "Kondisi" to item.condition,
                                "Status Barter" to if (item.isBarterAllowed) "Siap Barter" else "Koleksi Pribadi"
                            )

                            specs.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { (label, value) ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = CloBleColors.SecondarySurface,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CloBleColors.TextSecondary
                                                )
                                                Text(
                                                    text = value,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = CloBleColors.TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (item.description.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Deskripsi",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = CloBleColors.TextPrimary
                                    )
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CloBleColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (owner != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CloBleColors.Surface,
                            border = BorderStroke(1.dp, CloBleColors.Outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Pemilik Pakaian",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = CloBleColors.TextPrimary
                                )
                                UserSummaryHeader(
                                    name = owner.name,
                                    city = owner.city,
                                    averageRating = owner.averageRating,
                                    reviewCount = owner.reviewCount,
                                    isLowCredibility = owner.isLowCredibility,
                                    photoUri = owner.avatarUri,
                                    onAvatarClick = if (!owner.avatarUri.isNullOrBlank()) {
                                        {
                                            viewerInitialIndex = 0
                                            viewerPhotos = listOf(LabeledPhoto(owner.avatarUri, owner.name))
                                        }
                                    } else {
                                        null
                                    },
                                    onProfileClick = { onNavigateToOtherUserProfile(owner.id) },
                                    modifier = Modifier.testTag("detail_owner_summary")
                                )
                            }
                        }
                    }

                    if (uiState.actionMessage != null && uiState.errorMessage == null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.SUCCESS,
                            title = "Informasi",
                            message = uiState.actionMessage!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_action_box")
                        )
                    } else if (uiState.interestSentSuccess && uiState.errorMessage == null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.SUCCESS,
                            title = "Tawaran Terkirim",
                            message = "Tawaran interest berhasil disampaikan kepada pemilik pakaian. Anda akan mendapat notifikasi ketika pemilik merespons.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_interest_success_box")
                        )
                    } else if (uiState.errorMessage != null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.WARNING,
                            title = "Perhatian",
                            message = uiState.errorMessage!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_error_box")
                        )
                        if (uiState.requiresBarterItemCta) {
                            CloBlePrimaryButton(
                                text = "Buka Lemari & Aktifkan Barter",
                                onClick = onNavigateToWardrobe,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_go_to_wardrobe_from_detail")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val existingStatus = uiState.existingInterestStatus
                    val matchId = uiState.relatedMatchId
                    if (uiState.isOwnItem) {
                        CloBlePrimaryButton(
                            text = "Pakaian Ini Milik Anda",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!uiState.isItemUnavailable && (uiState.canReofferAfterCancelled || uiState.canReofferAfterInvalidated)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CloBlePrimaryButton(
                                text = if (uiState.isSendingInterest) "Mengirim Tawaran..." else "Tawar Ulang Barter",
                                onClick = viewModel::sendInterest,
                                enabled = !uiState.isSendingInterest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_send_interest_from_detail")
                            )
                            if (matchId != null) {
                                CloBleOutlinedButton(
                                    text = "Lihat Riwayat Match Dibatalkan",
                                    onClick = { onNavigateToMatch(matchId) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_view_match_from_detail")
                                )
                            }
                        }
                    } else if (matchId != null) {
                        CloBlePrimaryButton(
                            text = "Lihat Match",
                            onClick = { onNavigateToMatch(matchId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_view_match_from_detail")
                        )
                    } else if (existingStatus == InterestStatus.PENDING || uiState.interestSentSuccess) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CloBlePrimaryButton(
                                text = "Menunggu Respon Pemilik",
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_pending_interest_status")
                            )
                            if (uiState.existingInterestId != null) {
                                CloBleOutlinedButton(
                                    text = if (uiState.isCancellingInterest) "Membatalkan..." else "Batalkan Minat",
                                    onClick = viewModel::cancelPendingInterest,
                                    enabled = !uiState.isCancellingInterest,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_cancel_interest_from_detail")
                                )
                            }
                        }
                    } else if (existingStatus == InterestStatus.DECLINED) {
                        CloBlePrimaryButton(
                            text = "Tawaran Sebelumnya Ditolak",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_declined_interest_from_detail")
                        )
                    } else if (uiState.isItemUnavailable ||
                        existingStatus == InterestStatus.RESOLVED_BY_MATCH ||
                        (existingStatus == InterestStatus.INVALIDATED && !uiState.canReofferAfterInvalidated)
                    ) {
                        CloBlePrimaryButton(
                            text = "Pakaian Tidak Tersedia",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_unavailable_from_detail")
                        )
                    } else {
                        CloBlePrimaryButton(
                            text = if (uiState.isSendingInterest) "Mengirim Tawaran..." else "Kirim Tawaran Barter",
                            onClick = viewModel::sendInterest,
                            enabled = !uiState.isSendingInterest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_send_interest_from_detail")
                        )
                    }
                } else {
                    CloBleStatusInfoBox(
                        tone = CloBleInfoBoxTone.WARNING,
                        title = "Pakaian Tidak Tersedia",
                        message = uiState.errorMessage ?: "Pakaian tidak dapat dimuat. Silakan coba lagi.",
                        modifier = Modifier.fillMaxWidth().testTag("detail_empty_error_box")
                    )
                    CloBleOutlinedButton(
                        text = "Kembali",
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth().testTag("btn_back_from_detail_error")
                    )
                }
            }
        }
        }

        if (viewerPhotos.isNotEmpty()) {
            CloBleFullscreenImageViewer(
                photos = viewerPhotos,
                initialIndex = viewerInitialIndex,
                onDismiss = { viewerPhotos = emptyList() }
            )
        }
    }
}
