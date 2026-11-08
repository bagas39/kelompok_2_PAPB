package com.example.cloble.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.components.UserSummaryHeader
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyProfileScreen(
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

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Profil Saya",
                actions = listOf(
                    CloBleTopBarAction(
                        icon = CloBleIcons.Bell,
                        contentDescription = "Notifikasi",
                        onClick = onNavigateToNotifications,
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
            val user = uiState.userProfile
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (user != null) {
                    UserSummaryHeader(
                        name = user.name,
                        city = user.city,
                        averageRating = user.averageRating,
                        reviewCount = user.reviewCount,
                        isLowCredibility = user.isLowCredibility,
                        modifier = Modifier.testTag("user_summary_header")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                            .background(CloBleColors.SecondarySurface)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary
                            )
                            TextButton(
                                onClick = {
                                    if (uiState.isEditingBio) viewModel.saveBio()
                                    else viewModel.setEditingBio(true)
                                },
                                modifier = Modifier.testTag("btn_toggle_edit_bio")
                            ) {
                                Text(
                                    text = if (uiState.isEditingBio) "Simpan" else "Ubah Bio",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = CloBleColors.Primary
                                )
                            }
                        }

                        if (uiState.isEditingBio) {
                            CloBleLabeledTextField(
                                label = "",
                                value = uiState.bioInput,
                                onValueChange = viewModel::onBioInputChanged,
                                placeholder = "Tulis bio singkat profilmu...",
                                modifier = Modifier.fillMaxWidth().testTag("input_edit_bio")
                            )
                        } else {
                            Text(
                                text = if (user.bio.isNotEmpty()) user.bio else "Belum ada bio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CloBleColors.TextSecondary,
                                modifier = Modifier.testTag("text_display_bio")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    CloBleOutlinedButton(
                        text = "Ubah Preferensi Pakaian",
                        onClick = onNavigateToPreferences,
                        modifier = Modifier.fillMaxWidth().testTag("btn_go_to_preferences")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CloBlePrimaryButton(
                        text = "Kelola Lemari Digital",
                        onClick = onNavigateToWardrobe,
                        modifier = Modifier.fillMaxWidth().testTag("btn_go_to_wardrobe")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CloBleOutlinedButton(
                        text = "Riwayat Aktivitas",
                        onClick = onNavigateToHistory,
                        modifier = Modifier.fillMaxWidth().testTag("btn_go_to_history")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { viewModel.logout(onLogoutSuccess) },
                        modifier = Modifier.fillMaxWidth().testTag("btn_logout")
                    ) {
                        Text(
                            text = "Keluar Akun",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFDC2626)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Ulasan Pengguna (${uiState.reviews.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CloBleColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.reviews.isEmpty()) {
                        Text(
                            text = "Belum ada ulasan dari barter sebelumnya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                    } else {
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        uiState.reviews.forEach { review ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = review.reviewerName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = CloBleColors.TextPrimary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = CloBleIcons.StarFilled,
                                            contentDescription = null,
                                            tint = CloBleColors.StarRating,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = " ${review.rating}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = CloBleColors.TextPrimary
                                        )
                                    }
                                }
                                Text(
                                    text = review.comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = dateFormat.format(Date(review.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloBleColors.TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
