package com.example.cloble.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloble.domain.model.CloBleConstants
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleLocationPickerDialog
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.theme.CloBleColors
import java.util.Locale

@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLocationPicker by remember { mutableStateOf(false) }
    var showAvatarFullscreen by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onAvatarPicked(uri.toString())
        }
    }

    val sizeOptions = listOf("XS", "S", "M", "L", "XL", "XXL", "Semua")
    val presetRadii = setOf(5, 10, 25, 50, 100)
    val radiusOptions = listOf("5 km", "10 km", "25 km", "50 km", "100 km", "Kustom")
    var customRadiusMode by remember { mutableStateOf(false) }
    var customRadiusText by remember { mutableStateOf(uiState.preferredRadiusKm.toString()) }

    LaunchedEffect(uiState.preferredRadiusKm) {
        if (uiState.preferredRadiusKm !in presetRadii) {
            customRadiusMode = true
            customRadiusText = uiState.preferredRadiusKm.toString()
        } else if (!customRadiusMode) {
            customRadiusText = uiState.preferredRadiusKm.toString()
        }
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Edit Profil",
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.errorMessage != null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.WARNING,
                            title = "Perhatian",
                            message = uiState.errorMessage!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_profile_error_box")
                        )
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Foto Profil",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(CloBleColors.PrimaryContainer)
                                    .border(2.dp, CloBleColors.PrimarySoft, CircleShape)
                                    .let { base ->
                                        if (!uiState.avatarUri.isNullOrBlank()) {
                                            base.clickable(role = Role.Button) { showAvatarFullscreen = true }
                                        } else {
                                            base
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    uiState.isUploadingAvatar -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp,
                                            color = CloBleColors.Primary
                                        )
                                    }
                                    !uiState.avatarUri.isNullOrBlank() -> {
                                        AsyncImage(
                                            model = uiState.avatarUri,
                                            contentDescription = "Foto profil ${uiState.name}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = computeEditInitials(uiState.name),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = CloBleColors.OnPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CloBleOutlinedButton(
                                    text = if (uiState.avatarUri.isNullOrBlank()) "Pilih Foto" else "Ganti Foto",
                                    leadingIcon = CloBleIcons.Camera,
                                    enabled = !uiState.isUploadingAvatar && !uiState.isSaving,
                                    onClick = {
                                        avatarPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_pick_avatar")
                                )

                                if (!uiState.avatarUri.isNullOrBlank()) {
                                    TextButton(
                                        onClick = viewModel::onRemoveAvatar,
                                        enabled = !uiState.isUploadingAvatar && !uiState.isSaving,
                                        modifier = Modifier.testTag("btn_remove_avatar")
                                    ) {
                                        Icon(
                                            imageVector = CloBleIcons.Trash,
                                            contentDescription = null,
                                            tint = CloBleColors.DangerText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(
                                            text = "Hapus",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CloBleColors.DangerText
                                        )
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
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Informasi Dasar",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary
                            )

                            CloBleLabeledTextField(
                                label = "Nama Lengkap",
                                value = uiState.name,
                                onValueChange = viewModel::onNameChanged,
                                placeholder = "Nama tampil di profil",
                                inputTestTag = "input_edit_profile_name"
                            )

                            CloBleLabeledTextField(
                                label = "Bio",
                                value = uiState.bio,
                                onValueChange = viewModel::onBioChanged,
                                placeholder = "Ceritakan gaya berpakaian atau preferensi bartermu...",
                                singleLine = false,
                                minHeight = 96.dp,
                                inputTestTag = "input_edit_profile_bio"
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Lokasi Domisili",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CloBleColors.SecondarySurface,
                                border = BorderStroke(1.dp, CloBleColors.Outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) { showLocationPicker = true }
                                    .testTag("btn_edit_profile_location")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CloBleColors.PrimaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = CloBleIcons.MapPin,
                                                contentDescription = null,
                                                tint = CloBleColors.OnPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = uiState.city,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CloBleColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "%.4f, %.4f",
                                                uiState.latitude,
                                                uiState.longitude
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CloBleColors.TextSecondary
                                        )
                                    }
                                    Text(
                                        text = "Pilih di Peta",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CloBleColors.Primary
                                    )
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
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Preferensi Barter",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CloBleColors.TextPrimary
                            )

                            CloBleChoiceChipRow(
                                label = "Ukuran Pakaian Pilihan",
                                options = sizeOptions,
                                selectedOptions = setOf(uiState.preferredSize),
                                onOptionClick = viewModel::onPreferredSizeSelected,
                                modifier = Modifier.testTag("chips_edit_profile_size")
                            )

                            CloBleChoiceChipRow(
                                label = "Kategori Pengguna",
                                options = CloBleConstants.GENDERS,
                                selectedOptions = setOf(uiState.preferredGender),
                                onOptionClick = viewModel::onPreferredGenderSelected,
                                modifier = Modifier.testTag("chips_edit_profile_gender")
                            )

                            CloBleChoiceChipRow(
                                label = "Radius Pencarian Maksimum",
                                options = radiusOptions,
                                selectedOptions = if (customRadiusMode || uiState.preferredRadiusKm !in presetRadii) {
                                    setOf("Kustom")
                                } else {
                                    setOf("${uiState.preferredRadiusKm} km")
                                },
                                onOptionClick = { label ->
                                    if (label == "Kustom") {
                                        customRadiusMode = true
                                        if (customRadiusText.isBlank()) {
                                            customRadiusText = uiState.preferredRadiusKm.toString()
                                        }
                                    } else {
                                        customRadiusMode = false
                                        val km = label.replace(" km", "").toIntOrNull() ?: 10
                                        customRadiusText = km.toString()
                                        viewModel.onPreferredRadiusSelected(km)
                                    }
                                },
                                modifier = Modifier.testTag("chips_edit_profile_radius")
                            )

                            if (customRadiusMode || uiState.preferredRadiusKm !in presetRadii) {
                                CloBleLabeledTextField(
                                    label = "Radius Kustom (km)",
                                    value = customRadiusText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(5)
                                        customRadiusText = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed > 0) {
                                            viewModel.onPreferredRadiusSelected(parsed)
                                        }
                                    },
                                    placeholder = "Contoh: 600",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    inputTestTag = "input_edit_profile_custom_radius_km"
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = CloBleColors.Surface,
                    border = BorderStroke(1.dp, CloBleColors.Outline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (uiState.isSaving) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CloBleColors.Primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            CloBlePrimaryButton(
                                text = "Simpan Perubahan",
                                enabled = !uiState.isUploadingAvatar,
                                onClick = { viewModel.saveProfile(onSaveSuccess) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_save_edit_profile")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLocationPicker) {
        CloBleLocationPickerDialog(
            initialCity = uiState.city,
            initialLatitude = uiState.latitude,
            initialLongitude = uiState.longitude,
            onDismiss = { showLocationPicker = false },
            onLocationConfirmed = { selectedCity, lat, lon ->
                viewModel.onLocationSelected(selectedCity, lat, lon)
                showLocationPicker = false
            }
        )
    }

    val avatarUri = uiState.avatarUri
    if (showAvatarFullscreen && !avatarUri.isNullOrBlank()) {
        CloBleFullscreenImageViewer(
            photos = listOf(LabeledPhoto(avatarUri, uiState.name.ifBlank { "Foto Profil" })),
            initialIndex = 0,
            onDismiss = { showAvatarFullscreen = false }
        )
    }
}

private fun computeEditInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "CB"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}
