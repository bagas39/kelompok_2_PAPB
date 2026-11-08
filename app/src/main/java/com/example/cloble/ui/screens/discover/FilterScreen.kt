package com.example.cloble.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleTopAppBar

@Composable
fun FilterScreen(
    onNavigateBack: () -> Unit,
    onApplyFilter: (DiscoverFilter) -> Unit,
    viewModel: FilterViewModel = viewModel(factory = FilterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val radiusOptions = listOf("5 km", "10 km", "25 km", "50 km")
    val categories = listOf("Atasan", "Bawahan", "Luaran", "Terusan")
    val sizes = listOf("S", "M", "L", "XL")
    val genders = listOf("Pria", "Wanita", "Uniseks")

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Filter Pencarian",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            CloBleChoiceChipRow(
                label = "Radius Jarak Maksimum",
                options = radiusOptions,
                selectedOptions = if (uiState.selectedRadiusKm != null) setOf("${uiState.selectedRadiusKm} km") else emptySet(),
                onOptionClick = { opt ->
                    val km = opt.replace(" km", "").toIntOrNull()
                    if (uiState.selectedRadiusKm == km) {
                        viewModel.setRadius(null)
                    } else {
                        viewModel.setRadius(km)
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("filter_chips_radius")
            )

            CloBleChoiceChipRow(
                label = "Kategori Pakaian",
                options = categories,
                selectedOptions = uiState.selectedCategories,
                onOptionClick = viewModel::toggleCategory,
                modifier = Modifier.fillMaxWidth().testTag("filter_chips_category")
            )

            CloBleChoiceChipRow(
                label = "Ukuran",
                options = sizes,
                selectedOptions = uiState.selectedSizes,
                onOptionClick = viewModel::toggleSize,
                modifier = Modifier.fillMaxWidth().testTag("filter_chips_size")
            )

            CloBleChoiceChipRow(
                label = "Kategori Pengguna",
                options = genders,
                selectedOptions = uiState.selectedGenders,
                onOptionClick = viewModel::toggleGender,
                modifier = Modifier.fillMaxWidth().testTag("filter_chips_gender")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CloBleOutlinedButton(
                    text = "Reset",
                    onClick = viewModel::reset,
                    modifier = Modifier.weight(1f).testTag("btn_reset_filter")
                )
                CloBlePrimaryButton(
                    text = "Terapkan",
                    onClick = {
                        onApplyFilter(viewModel.buildFilter())
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f).testTag("btn_apply_filter")
                )
            }
        }
    }
}
