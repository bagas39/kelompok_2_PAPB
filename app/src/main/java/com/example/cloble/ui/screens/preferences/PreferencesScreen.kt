package com.example.cloble.ui.screens.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.CloBleConstants
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleDropdownField
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun PreferencesScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PreferencesViewModel = viewModel(factory = PreferencesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cityOptions = CityPreset.entries.map { it.displayName }
    val radiusOptions = listOf("5 km", "10 km", "25 km", "50 km")

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = if (uiState.isOnboarding) "Atur Preferensi Awal" else "Preferensi Pakaian",
                onNavigateBack = if (uiState.isOnboarding) null else onNavigateBack
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Sesuaikan Rekomendasi Barter",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pakaian yang muncul di menu Jelajah akan disaring berdasarkan preferensi ini.",
                style = MaterialTheme.typography.bodySmall,
                color = CloBleColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            CloBleDropdownField(
                label = "Kota Domisili",
                selectedValue = uiState.selectedCity,
                options = cityOptions,
                onOptionSelected = viewModel::onCitySelected,
                modifier = Modifier.fillMaxWidth().testTag("dropdown_pref_city")
            )

            Spacer(modifier = Modifier.height(20.dp))

            CloBleChoiceChipRow(
                label = "Radius Jarak Maksimum",
                options = radiusOptions,
                selectedOptions = setOf("${uiState.selectedRadiusKm} km"),
                onOptionClick = { opt ->
                    val km = opt.replace(" km", "").toIntOrNull() ?: 10
                    viewModel.onRadiusSelected(km)
                },
                modifier = Modifier.fillMaxWidth().testTag("chips_pref_radius")
            )

            Spacer(modifier = Modifier.height(20.dp))

            CloBleChoiceChipRow(
                label = "Ukuran Pakaian Pilihan",
                options = CloBleConstants.SIZES,
                selectedOptions = if (uiState.selectedSize.isNotEmpty()) setOf(uiState.selectedSize) else emptySet(),
                onOptionClick = viewModel::onSizeSelected,
                modifier = Modifier.fillMaxWidth().testTag("chips_pref_size")
            )

            Spacer(modifier = Modifier.height(20.dp))

            CloBleChoiceChipRow(
                label = "Kategori Gender Pilihan",
                options = CloBleConstants.GENDERS,
                selectedOptions = if (uiState.selectedGender.isNotEmpty()) setOf(uiState.selectedGender) else emptySet(),
                onOptionClick = viewModel::onGenderSelected,
                modifier = Modifier.fillMaxWidth().testTag("chips_pref_gender")
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (uiState.isSaving) {
                CircularProgressIndicator(
                    color = CloBleColors.Primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                CloBlePrimaryButton(
                    text = if (uiState.isOnboarding) "Lanjut ke Jelajah" else "Simpan Preferensi",
                    onClick = { viewModel.savePreferences(onSaveSuccess) },
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_preferences")
                )
            }
        }
    }
}
