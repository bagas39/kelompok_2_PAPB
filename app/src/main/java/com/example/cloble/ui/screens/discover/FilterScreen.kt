package com.example.cloble.ui.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.CloBleConstants
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleConstrainedContent
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.theme.CloBleColors
import kotlinx.coroutines.launch

@Composable
fun FilterScreen(
    onNavigateBack: () -> Unit,
    onApplyFilter: (DiscoverFilter) -> Unit,
    viewModel: FilterViewModel = viewModel(factory = FilterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val radiusOptions = listOf("5 km", "10 km", "25 km", "50 km", "100 km", "Kustom")
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Filter Pencarian",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
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
                        .testTag("filter_error_box")
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
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CloBleChoiceChipRow(
                        label = "Radius Jarak Maksimum",
                        options = radiusOptions,
                        selectedOptions = buildSet {
                            if (uiState.isCustomRadius) {
                                add("Kustom")
                            } else if (uiState.selectedRadiusKm != null) {
                                add("${uiState.selectedRadiusKm} km")
                            }
                        },
                        onOptionClick = { opt ->
                            if (opt == "Kustom") {
                                viewModel.toggleCustomRadiusMode()
                            } else {
                                val km = opt.replace(" km", "").toIntOrNull()
                                if (km != null) {
                                    viewModel.selectPresetRadius(km)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("filter_chips_radius")
                    )

                    if (uiState.isCustomRadius) {
                        CloBleLabeledTextField(
                            label = "Radius Kustom (km)",
                            value = uiState.customRadiusText,
                            onValueChange = viewModel::onCustomRadiusTextChanged,
                            placeholder = "Contoh: 600",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            inputTestTag = "input_custom_radius_km"
                        )
                    }

                    CloBleChoiceChipRow(
                        label = "Kategori Pakaian",
                        options = CloBleConstants.CATEGORIES,
                        selectedOptions = uiState.selectedCategories,
                        onOptionClick = viewModel::toggleCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("filter_chips_category")
                    )

                    CloBleChoiceChipRow(
                        label = "Warna",
                        options = CloBleConstants.COLORS,
                        selectedOptions = uiState.selectedColors,
                        onOptionClick = viewModel::toggleColor,
                        modifier = Modifier.fillMaxWidth().testTag("filter_chips_color")
                    )

                    CloBleChoiceChipRow(
                        label = "Ukuran",
                        options = CloBleConstants.SIZES,
                        selectedOptions = uiState.selectedSizes,
                        onOptionClick = viewModel::toggleSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("filter_chips_size")
                    )

                    CloBleChoiceChipRow(
                        label = "Kategori Pengguna",
                        options = CloBleConstants.GENDERS,
                        selectedOptions = uiState.selectedGenders,
                        onOptionClick = viewModel::toggleGender,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("filter_chips_gender")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CloBleOutlinedButton(
                    text = "Reset",
                    onClick = viewModel::reset,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_reset_filter")
                )
                CloBlePrimaryButton(
                    text = "Terapkan",
                    onClick = {
                        val applied = viewModel.applyFilter()
                        if (applied != null) {
                            onApplyFilter(applied)
                            onNavigateBack()
                        } else {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_apply_filter")
                )
            }
        }
        }
    }
}
