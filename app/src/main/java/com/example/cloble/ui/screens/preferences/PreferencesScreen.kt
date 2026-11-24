package com.example.cloble.ui.screens.preferences

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.CloBleConstants
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleConstrainedContent
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleLoadingState
import com.example.cloble.ui.components.CloBleLocationPickerDialog
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.theme.CloBleColors
import java.util.Locale

@Composable
fun PreferencesScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PreferencesViewModel = viewModel(factory = PreferencesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val radiusOptions = listOf("5 km", "10 km", "25 km", "50 km", "100 km", "Kustom")
    var showLocationPicker by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var lastScrolledErrorTrigger by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(uiState.errorMessage, uiState.errorScrollTrigger) {
        if (uiState.errorMessage != null && uiState.errorScrollTrigger != lastScrolledErrorTrigger) {
            lastScrolledErrorTrigger = uiState.errorScrollTrigger
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = if (uiState.isOnboarding) "Atur Preferensi Awal" else "Preferensi Barter",
                onNavigateBack = if (uiState.isOnboarding) null else onNavigateBack
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
            CloBleLoadingState("Memuat preferensi barter...")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (uiState.isProfileLoadFailed) {
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
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.WARNING,
                            title = "Preferensi Gagal Dimuat",
                            message = "Preferensi lama Anda gagal dimuat dari server. Muat ulang sebelum menyimpan agar tidak tertimpa nilai bawaan.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preferences_load_error_box")
                        )
                        CloBleOutlinedButton(
                            text = "Muat Ulang",
                            onClick = viewModel::retryLoadPreferences,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_retry_preferences")
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                CloBleStatusInfoBox(
                    tone = CloBleInfoBoxTone.WARNING,
                    title = "Perhatian",
                    message = uiState.errorMessage!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preferences_error_box")
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Sesuaikan Rekomendasi Barter",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CloBleColors.TextPrimary
                        )
                        Text(
                            text = "Pakaian yang muncul di menu Jelajah akan disaring berdasarkan preferensi ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Lokasi Barter",
                            style = MaterialTheme.typography.labelLarge,
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
                                .testTag("btn_pref_map_picker")
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
                                        text = uiState.selectedCity,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CloBleColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%.4f, %.4f",
                                            uiState.selectedLatitude,
                                            uiState.selectedLongitude
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

                    CloBleChoiceChipRow(
                        label = "Radius Pencarian Maksimum",
                        options = radiusOptions,
                        selectedOptions = if (uiState.isCustomRadius) {
                            setOf("Kustom")
                        } else {
                            setOf("${uiState.selectedRadiusKm} km")
                        },
                        onOptionClick = { opt ->
                            if (opt == "Kustom") {
                                viewModel.onCustomRadiusModeSelected()
                            } else {
                                val km = opt.replace(" km", "").toIntOrNull() ?: 10
                                viewModel.onRadiusSelected(km)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chips_pref_radius")
                    )

                    if (uiState.isCustomRadius) {
                        CloBleLabeledTextField(
                            label = "Radius Kustom (km)",
                            value = uiState.customRadiusText,
                            onValueChange = viewModel::onCustomRadiusTextChanged,
                            placeholder = "Contoh: 600",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            inputTestTag = "input_pref_custom_radius_km"
                        )
                    }

                    CloBleChoiceChipRow(
                        label = "Ukuran Pakaian Pilihan",
                        options = CloBleConstants.SIZES + "Semua",
                        selectedOptions = if (uiState.selectedSize.isNotEmpty()) setOf(uiState.selectedSize) else emptySet(),
                        onOptionClick = viewModel::onSizeSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chips_pref_size")
                    )

                    CloBleChoiceChipRow(
                        label = "Kategori Pengguna",
                        options = CloBleConstants.GENDERS,
                        selectedOptions = if (uiState.selectedGender.isNotEmpty()) setOf(uiState.selectedGender) else emptySet(),
                        onOptionClick = viewModel::onGenderSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chips_pref_gender")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.isSaving) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CloBleColors.Primary)
                }
            } else {
                CloBlePrimaryButton(
                    text = if (uiState.isOnboarding) "Lanjut ke Jelajah" else "Simpan Preferensi",
                    enabled = !uiState.isProfileLoadFailed,
                    onClick = { viewModel.savePreferences(onSaveSuccess) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_preferences")
                )
            }
            }
        }
        }
    }

    if (showLocationPicker) {
        CloBleLocationPickerDialog(
            initialCity = uiState.selectedCity,
            initialLatitude = uiState.selectedLatitude,
            initialLongitude = uiState.selectedLongitude,
            onDismiss = { showLocationPicker = false },
            onLocationConfirmed = { selectedCity, lat, lon ->
                viewModel.onLocationSelected(selectedCity, lat, lon)
                showLocationPicker = false
            }
        )
    }
}
