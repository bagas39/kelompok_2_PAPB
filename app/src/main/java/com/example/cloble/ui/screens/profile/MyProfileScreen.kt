package com.example.cloble.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.BuildConfig
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToWardrobe: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToChatList: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    viewModel: MyProfileViewModel = viewModel(factory = MyProfileViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var viewerPhotos by remember { mutableStateOf<List<LabeledPhoto>>(emptyList()) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Profil Saya",
                actions = listOf(
                    CloBleTopBarAction(
                        icon = CloBleIcons.Bell,
                        contentDescription = "Notifikasi",
                        onClick = onNavigateToNotifications,
                        showBadgeDot = uiState.unreadNotificationCount > 0,
                        testTag = "btn_notifications_from_profile"
                    )
                )
            )
        },
        bottomBar = {
            CloBleBottomNavBar(
                selectedTab = CloBleBottomTab.PROFILE,
                onTabSelected = { tab ->
                    when (tab) {
                        CloBleBottomTab.DISCOVER -> onNavigateToDiscover()
                        CloBleBottomTab.WARDROBE -> onNavigateToWardrobe()
                        CloBleBottomTab.CHAT -> onNavigateToChatList()
                        CloBleBottomTab.PROFILE -> {}
                    }
                }
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
        } else {
            val user = uiState.userProfile
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.passwordSuccessMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CloBleColors.SuccessContainer,
                        border = BorderStroke(1.dp, CloBleColors.SuccessText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_password_success_box")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Password Diperbarui",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CloBleColors.SuccessText
                                )
                                Text(
                                    text = uiState.passwordSuccessMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CloBleColors.SuccessText
                                )
                            }
                            IconButton(
                                onClick = viewModel::clearPasswordSuccessMessage,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("btn_dismiss_password_success")
                            ) {
                                Icon(
                                    imageVector = CloBleIcons.XClose,
                                    contentDescription = "Tutup pesan sukses",
                                    tint = CloBleColors.SuccessText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (user != null) {
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
                            UserSummaryHeader(
                                name = user.name,
                                city = user.city,
                                averageRating = user.averageRating,
                                reviewCount = user.reviewCount,
                                isLowCredibility = user.isLowCredibility,
                                photoUri = user.avatarUri,
                                onAvatarClick = if (!user.avatarUri.isNullOrBlank()) {
                                    { viewerPhotos = listOf(LabeledPhoto(user.avatarUri, user.name)) }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("user_summary_header")
                            )

                            if (user.bio.isNotBlank()) {
                                Text(
                                    text = user.bio,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CloBleColors.TextSecondary,
                                    modifier = Modifier.testTag("text_display_bio")
                                )
                            } else {
                                Text(
                                    text = "Belum ada bio. Lengkapi profilmu agar rekan barter lebih percaya.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary,
                                    modifier = Modifier.testTag("text_display_bio")
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProfilePrefChip(
                                    if (user.preferredSize.equals("Semua", ignoreCase = true)) {
                                        "Semua Ukuran"
                                    } else {
                                        "Ukuran ${user.preferredSize}"
                                    }
                                )
                                ProfilePrefChip(user.preferredGender)
                                ProfilePrefChip("Radius ${user.preferredRadiusKm} km")
                            }

                            CloBleOutlinedButton(
                                text = "Edit Profil",
                                leadingIcon = CloBleIcons.EditPencil,
                                onClick = onNavigateToEditProfile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_edit_profile")
                            )
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Koleksi Lemari",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = CloBleColors.TextPrimary
                                )
                                TextButton(
                                    onClick = onNavigateToWardrobe,
                                    modifier = Modifier.testTag("btn_go_to_wardrobe")
                                ) {
                                    Text(
                                        text = "Lihat Semua",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CloBleColors.Primary
                                    )
                                }
                            }

                            if (uiState.wardrobePreview.isEmpty()) {
                                Text(
                                    text = "Belum ada pakaian di lemari. Tambahkan pakaian agar bisa mulai barter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary
                                )
                            } else {
                                uiState.wardrobePreview.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowItems.forEach { summary ->
                                            val item = summary.item
                                            val thumbUri = item.mainPhotoUri ?: item.detailPhotoUri ?: item.extraPhotoUri
                                            val itemPhotos = remember(item.mainPhotoUri, item.detailPhotoUri, item.extraPhotoUri, item.name) {
                                                buildProfileWardrobePhotos(item)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(CloBleColors.SecondarySurface)
                                                    .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                                                    .clickable(role = Role.Button) {
                                                        if (itemPhotos.isNotEmpty()) {
                                                            viewerPhotos = itemPhotos
                                                        } else {
                                                            onNavigateToWardrobe()
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!thumbUri.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = thumbUri,
                                                        contentDescription = item.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = CloBleIcons.ShirtOutline,
                                                        contentDescription = null,
                                                        tint = CloBleColors.Primary,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Ulasan Pengguna (${uiState.reviews.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary
                            )

                            if (uiState.reviews.isEmpty()) {
                                Text(
                                    text = "Belum ada ulasan dari barter sebelumnya.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary
                                )
                            } else {
                                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                                uiState.reviews.forEach { review ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = CloBleColors.SecondarySurface,
                                        border = BorderStroke(1.dp, CloBleColors.Outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = review.reviewerName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = CloBleColors.TextPrimary
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = CloBleIcons.StarFilled,
                                                        contentDescription = null,
                                                        tint = CloBleColors.StarRating,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "${review.rating}.0",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = CloBleColors.TextPrimary
                                                    )
                                                }
                                            }
                                            if (review.comment.isNotBlank()) {
                                                Text(
                                                    text = review.comment,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CloBleColors.TextSecondary
                                                )
                                            }
                                            Text(
                                                text = dateFormat.format(Date(review.createdAt)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CloBleColors.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
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
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.WARNING,
                                title = "Profil Tidak Tersedia",
                                message = uiState.errorMessage
                                    ?: "Profil Anda tidak dapat dimuat. Periksa koneksi internet Anda dan coba lagi.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("my_profile_error_box")
                            )
                            CloBlePrimaryButton(
                                text = "Muat Ulang",
                                onClick = viewModel::retryLoadProfile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_retry_my_profile")
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileMenuRow(
                            icon = CloBleIcons.Clock,
                            title = "Riwayat Aktivitas",
                            subtitle = "Kelola minat terkirim & pakaian dilewati",
                            onClick = onNavigateToHistory,
                            modifier = Modifier.testTag("btn_go_to_history")
                        )
                        HorizontalDivider(color = CloBleColors.Outline)
                        ProfileMenuRow(
                            icon = CloBleIcons.FilterFunnel,
                            title = "Preferensi Barter",
                            subtitle = "Atur lokasi, ukuran, dan radius pencarian",
                            onClick = onNavigateToPreferences,
                            modifier = Modifier.testTag("btn_go_to_preferences")
                        )
                        HorizontalDivider(color = CloBleColors.Outline)
                        ProfileMenuRow(
                            icon = CloBleIcons.EditPencil,
                            title = "Ubah Password",
                            subtitle = "Perbarui password masuk untuk akun Anda",
                            onClick = viewModel::openChangePasswordDialog,
                            modifier = Modifier.testTag("btn_change_password_from_profile")
                        )
                        HorizontalDivider(color = CloBleColors.Outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { showLogoutDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("btn_logout"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CloBleColors.DangerContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CloBleIcons.XClose,
                                    contentDescription = null,
                                    tint = CloBleColors.DangerText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Keluar Akun",
                                style = MaterialTheme.typography.labelLarge,
                                color = CloBleColors.DangerText,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = CloBleIcons.ChevronRight,
                                contentDescription = null,
                                tint = CloBleColors.DangerText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "CloBle v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloBleColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("text_app_version")
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = CloBleColors.Surface,
            title = {
                Text(
                    text = "Keluar dari Akun?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CloBleColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "Anda perlu masuk kembali untuk melihat chat, notifikasi, dan lemari Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloBleColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogoutSuccess)
                    },
                    modifier = Modifier.testTag("btn_confirm_logout")
                ) {
                    Text(
                        text = "Keluar",
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.DangerText
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "Batal",
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.TextSecondary
                    )
                }
            }
        )
    }

    if (uiState.showChangePasswordDialog) {
        val dialogFocusManager = LocalFocusManager.current
        Dialog(
            onDismissRequest = {
                if (!uiState.isUpdatingPassword) {
                    viewModel.dismissChangePasswordDialog()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CloBleColors.TextPrimary.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CloBleColors.Surface,
                    border = BorderStroke(1.dp, CloBleColors.Outline),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ubah Password",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CloBleColors.TextPrimary
                        )
                        Text(
                            text = "Verifikasi password saat ini dan masukkan password baru (minimal 8 karakter, huruf & angka).",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                        if (uiState.passwordErrorMessage != null) {
                            CloBleStatusInfoBox(
                                tone = CloBleInfoBoxTone.WARNING,
                                title = "Gagal Mengubah Password",
                                message = uiState.passwordErrorMessage!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("change_password_error_box")
                            )
                        }
                        CloBleLabeledTextField(
                            label = "Password Saat Ini",
                            value = uiState.currentPassword,
                            onValueChange = viewModel::onCurrentPasswordChanged,
                            placeholder = "Masukkan password saat ini",
                            visualTransformation = if (uiState.isCurrentPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = viewModel::toggleCurrentPasswordVisibility,
                                    enabled = !uiState.isUpdatingPassword,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_toggle_profile_current_password")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isCurrentPasswordVisible) CloBleIcons.EyeOff else CloBleIcons.Eye,
                                        contentDescription = if (uiState.isCurrentPasswordVisible) "Sembunyikan password saat ini" else "Tampilkan password saat ini",
                                        tint = CloBleColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingPassword,
                            modifier = Modifier.fillMaxWidth(),
                            inputTestTag = "input_profile_current_password"
                        )
                        CloBleLabeledTextField(
                            label = "Password Baru",
                            value = uiState.newPassword,
                            onValueChange = viewModel::onNewPasswordChanged,
                            placeholder = "Min. 8 karakter (huruf & angka)",
                            visualTransformation = if (uiState.isNewPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = viewModel::toggleNewPasswordVisibility,
                                    enabled = !uiState.isUpdatingPassword,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_toggle_profile_new_password")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isNewPasswordVisible) CloBleIcons.EyeOff else CloBleIcons.Eye,
                                        contentDescription = if (uiState.isNewPasswordVisible) "Sembunyikan password baru" else "Tampilkan password baru",
                                        tint = CloBleColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingPassword,
                            modifier = Modifier.fillMaxWidth(),
                            inputTestTag = "input_profile_new_password"
                        )
                        CloBleLabeledTextField(
                            label = "Konfirmasi Password Baru",
                            value = uiState.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChanged,
                            placeholder = "Ulangi password baru",
                            visualTransformation = if (uiState.isConfirmPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    dialogFocusManager.clearFocus()
                                    if (!uiState.isUpdatingPassword) {
                                        viewModel.submitChangePassword()
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = viewModel::toggleConfirmPasswordVisibility,
                                    enabled = !uiState.isUpdatingPassword,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_toggle_profile_confirm_password")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isConfirmPasswordVisible) CloBleIcons.EyeOff else CloBleIcons.Eye,
                                        contentDescription = if (uiState.isConfirmPasswordVisible) "Sembunyikan konfirmasi password" else "Tampilkan konfirmasi password",
                                        tint = CloBleColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingPassword,
                            modifier = Modifier.fillMaxWidth(),
                            inputTestTag = "input_profile_confirm_password"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = viewModel::dismissChangePasswordDialog,
                                enabled = !uiState.isUpdatingPassword,
                                modifier = Modifier.testTag("btn_cancel_change_password")
                            ) {
                                Text(
                                    text = "Batal",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                            TextButton(
                                onClick = viewModel::submitChangePassword,
                                enabled = !uiState.isUpdatingPassword,
                                modifier = Modifier.testTag("btn_confirm_change_password")
                            ) {
                                Text(
                                    text = if (uiState.isUpdatingPassword) "Menyimpan..." else "Simpan Password",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CloBleColors.Primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewerPhotos.isNotEmpty()) {
        CloBleFullscreenImageViewer(
            photos = viewerPhotos,
            initialIndex = 0,
            onDismiss = { viewerPhotos = emptyList() }
        )
    }
}

@Composable
private fun ProfilePrefChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CloBleColors.PrimaryContainer,
        border = BorderStroke(1.dp, CloBleColors.Outline)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CloBleColors.OnPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CloBleColors.PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CloBleColors.OnPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = CloBleColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CloBleColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = CloBleIcons.ChevronRight,
            contentDescription = null,
            tint = CloBleColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun buildProfileWardrobePhotos(item: ClothingItem): List<LabeledPhoto> {
    val result = mutableListOf<LabeledPhoto>()
    item.mainPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(uri = it, label = "${item.name} · Foto Utama"))
    }
    item.detailPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(uri = it, label = "${item.name} · Foto Belakang"))
    }
    item.extraPhotoUri?.takeIf { it.isNotBlank() }?.let {
        result.add(LabeledPhoto(uri = it, label = "${item.name} · Detail Label"))
    }
    return result
}
