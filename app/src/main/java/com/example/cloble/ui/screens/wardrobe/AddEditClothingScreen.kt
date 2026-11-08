package com.example.cloble.ui.screens.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.BarterEligibilitySwitchCard
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.ClothingPhotoSlot
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun AddEditClothingScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: AddEditClothingViewModel = viewModel(factory = AddEditClothingViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var activeSlotIndex by remember { mutableIntStateOf(0) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onPhotoSelected(uri.toString(), activeSlotIndex)
        }
    }

    val categories = listOf("Atasan", "Bawahan", "Luaran", "Terusan")
    val sizes = listOf("S", "M", "L", "XL")
    val genders = listOf("Pria", "Wanita", "Uniseks")
    val colors = listOf("Hitam", "Putih", "Biru", "Krem", "Hijau", "Abu-abu", "Merah")
    val conditions = listOf("Sangat baik", "Baik", "Layak pakai")

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = if (uiState.isEditMode) "Kelola Pakaian" else "Tambah Pakaian",
                onNavigateBack = onNavigateBack
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {

                if (uiState.errorMessage != null) {
                    CloBleStatusInfoBox(
                        tone = CloBleInfoBoxTone.HIGHLIGHT,
                        title = "Perhatian",
                        message = uiState.errorMessage!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("add_edit_error_box")
                    )
                }

                Text(
                    text = "Foto Pakaian",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CloBleColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ketuk slot atau tombol di bawah untuk memilih foto dari galeri",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloBleColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ClothingPhotoSlot(
                        label = "Foto Utama",
                        imageModel = uiState.mainPhotoUri,
                        onClick = {
                            activeSlotIndex = 0
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveClick = if (uiState.mainPhotoUri != null) { { viewModel.onRemovePhoto(0) } } else null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_slot_main")
                    )
                    ClothingPhotoSlot(
                        label = "Tampak Belakang",
                        imageModel = uiState.detailPhotoUri,
                        onClick = {
                            activeSlotIndex = 1
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveClick = if (uiState.detailPhotoUri != null) { { viewModel.onRemovePhoto(1) } } else null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_slot_detail")
                    )
                    ClothingPhotoSlot(
                        label = "Detail Label",
                        imageModel = uiState.extraPhotoUri,
                        onClick = {
                            activeSlotIndex = 2
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveClick = if (uiState.extraPhotoUri != null) { { viewModel.onRemovePhoto(2) } } else null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_slot_extra")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                CloBleOutlinedButton(
                    text = "Ubah / Tambah Foto",
                    onClick = {
                        activeSlotIndex = when {
                            uiState.mainPhotoUri == null -> 0
                            uiState.detailPhotoUri == null -> 1
                            uiState.extraPhotoUri == null -> 2
                            else -> 0
                        }
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_pick_photo")
                )

                Spacer(modifier = Modifier.height(20.dp))

                CloBleLabeledTextField(
                    label = "Nama Pakaian",
                    value = uiState.name,
                    onValueChange = viewModel::onNameChanged,
                    placeholder = "Misal: Kemeja Flanel Hijau",
                    modifier = Modifier.fillMaxWidth().testTag("input_clothing_name")
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloBleLabeledTextField(
                    label = "Deskripsi",
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    placeholder = "Jelaskan bahan, kecocokan ukuran, dan kondisi pakaian...",
                    modifier = Modifier.fillMaxWidth().testTag("input_clothing_description")
                )

                Spacer(modifier = Modifier.height(20.dp))

                CloBleChoiceChipRow(
                    label = "Kategori",
                    options = categories,
                    selectedOptions = setOf(uiState.category),
                    onOptionClick = viewModel::onCategorySelected,
                    modifier = Modifier.fillMaxWidth().testTag("chips_clothing_category")
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloBleChoiceChipRow(
                    label = "Ukuran",
                    options = sizes,
                    selectedOptions = setOf(uiState.size),
                    onOptionClick = viewModel::onSizeSelected,
                    modifier = Modifier.fillMaxWidth().testTag("chips_clothing_size")
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloBleChoiceChipRow(
                    label = "Kategori Pengguna",
                    options = genders,
                    selectedOptions = setOf(uiState.gender),
                    onOptionClick = viewModel::onGenderSelected,
                    modifier = Modifier.fillMaxWidth().testTag("chips_clothing_gender")
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloBleChoiceChipRow(
                    label = "Warna Dominan",
                    options = colors,
                    selectedOptions = setOf(uiState.color),
                    onOptionClick = viewModel::onColorSelected,
                    modifier = Modifier.fillMaxWidth().testTag("chips_clothing_color")
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloBleChoiceChipRow(
                    label = "Kondisi",
                    options = conditions,
                    selectedOptions = setOf(uiState.condition),
                    onOptionClick = viewModel::onConditionSelected,
                    modifier = Modifier.fillMaxWidth().testTag("chips_clothing_condition")
                )

                Spacer(modifier = Modifier.height(24.dp))

                BarterEligibilitySwitchCard(
                    isBarterAllowed = uiState.isBarterAllowed,
                    onBarterAllowedChange = viewModel::onBarterAllowedChanged,
                    enabled = !uiState.isLockedInActiveMatch,
                    modifier = Modifier.fillMaxWidth().testTag("switch_barter_allowed")
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = CloBleColors.Primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    CloBlePrimaryButton(
                        text = if (uiState.isEditMode) "Simpan Perubahan" else "Simpan ke Lemari",
                        onClick = { viewModel.save(onSaveSuccess) },
                        modifier = Modifier.fillMaxWidth().testTag("btn_save_clothing")
                    )

                    if (uiState.isEditMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = viewModel::requestDelete,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier.fillMaxWidth().testTag("btn_delete_clothing")
                        ) {
                            Text(
                                text = "Hapus Pakaian Ini",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (uiState.isLockedInActiveMatch) CloBleColors.Outline else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showDeleteDialog) {
            DeleteClothingConfirmDialog(
                clothingName = uiState.name,
                onConfirm = { viewModel.confirmDelete(onSaveSuccess) },
                onDismiss = viewModel::dismissDeleteDialog
            )
        }
    }
}
