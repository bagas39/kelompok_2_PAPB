package com.example.cloble.ui.screens.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.BarterEligibilitySwitchCard
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleFullscreenImageViewer
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.ClothingPhotoSlot
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun AddEditClothingScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: AddEditClothingViewModel = viewModel(factory = AddEditClothingViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var activeSlotIndex by remember { mutableIntStateOf(0) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }

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

    val filledPhotos = remember(uiState.mainPhotoUri, uiState.detailPhotoUri, uiState.extraPhotoUri) {
        buildList {
            uiState.mainPhotoUri?.takeIf { it.isNotBlank() }?.let {
                add(LabeledPhoto(it, "Foto Utama"))
            }
            uiState.detailPhotoUri?.takeIf { it.isNotBlank() }?.let {
                add(LabeledPhoto(it, "Tampak Belakang"))
            }
            uiState.extraPhotoUri?.takeIf { it.isNotBlank() }?.let {
                add(LabeledPhoto(it, "Detail Label"))
            }
        }
    }

    fun openFullscreenForSlot(uri: String?) {
        if (uri.isNullOrBlank()) return
        val idx = filledPhotos.indexOfFirst { it.uri == uri }.coerceAtLeast(0)
        viewerStartIndex = idx
    }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = if (uiState.isEditMode) "Kelola Pakaian" else "Tambah Pakaian",
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
                    .imePadding()
                    .navigationBarsPadding()
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
                            .testTag("add_edit_error_box")
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Foto Pakaian",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CloBleColors.TextPrimary
                        )
                        Text(
                            text = "Ketuk foto terisi untuk memperbesar, atau ketuk slot kosong untuk memilih dari galeri.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ClothingPhotoSlot(
                                label = "Foto Utama",
                                imageModel = uiState.mainPhotoUri,
                                onClick = {
                                    if (!uiState.mainPhotoUri.isNullOrBlank()) {
                                        openFullscreenForSlot(uiState.mainPhotoUri)
                                    } else if (!uiState.isLockedInActiveMatch) {
                                        activeSlotIndex = 0
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                onRemoveClick = if (uiState.mainPhotoUri != null && !uiState.isLockedInActiveMatch) { { viewModel.onRemovePhoto(0) } } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("photo_slot_main")
                            )
                            ClothingPhotoSlot(
                                label = "Tampak Belakang",
                                imageModel = uiState.detailPhotoUri,
                                onClick = {
                                    if (!uiState.detailPhotoUri.isNullOrBlank()) {
                                        openFullscreenForSlot(uiState.detailPhotoUri)
                                    } else if (!uiState.isLockedInActiveMatch) {
                                        activeSlotIndex = 1
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                onRemoveClick = if (uiState.detailPhotoUri != null && !uiState.isLockedInActiveMatch) { { viewModel.onRemovePhoto(1) } } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("photo_slot_detail")
                            )
                            ClothingPhotoSlot(
                                label = "Detail Label",
                                imageModel = uiState.extraPhotoUri,
                                onClick = {
                                    if (!uiState.extraPhotoUri.isNullOrBlank()) {
                                        openFullscreenForSlot(uiState.extraPhotoUri)
                                    } else if (!uiState.isLockedInActiveMatch) {
                                        activeSlotIndex = 2
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                onRemoveClick = if (uiState.extraPhotoUri != null && !uiState.isLockedInActiveMatch) { { viewModel.onRemovePhoto(2) } } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("photo_slot_extra")
                            )
                        }

                        CloBleOutlinedButton(
                            text = "Ubah / Tambah Foto",
                            leadingIcon = CloBleIcons.Camera,
                            enabled = !uiState.isLockedInActiveMatch,
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
                        CloBleLabeledTextField(
                            label = "Nama Pakaian",
                            value = uiState.name,
                            onValueChange = viewModel::onNameChanged,
                            placeholder = "Misal: Kemeja Flanel Hijau",
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_clothing_name")
                        )

                        CloBleLabeledTextField(
                            label = "Deskripsi",
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            placeholder = "Jelaskan bahan, kecocokan ukuran, dan kondisi pakaian...",
                            singleLine = false,
                            minHeight = 96.dp,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_clothing_description")
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CloBleChoiceChipRow(
                            label = "Kategori",
                            options = categories,
                            selectedOptions = setOf(uiState.category),
                            onOptionClick = viewModel::onCategorySelected,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chips_clothing_category")
                        )

                        CloBleChoiceChipRow(
                            label = "Ukuran",
                            options = sizes,
                            selectedOptions = setOf(uiState.size),
                            onOptionClick = viewModel::onSizeSelected,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chips_clothing_size")
                        )

                        CloBleChoiceChipRow(
                            label = "Kategori Pengguna",
                            options = genders,
                            selectedOptions = setOf(uiState.gender),
                            onOptionClick = viewModel::onGenderSelected,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chips_clothing_gender")
                        )

                        CloBleChoiceChipRow(
                            label = "Warna Dominan",
                            options = colors,
                            selectedOptions = setOf(uiState.color),
                            onOptionClick = viewModel::onColorSelected,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chips_clothing_color")
                        )

                        CloBleChoiceChipRow(
                            label = "Kondisi",
                            options = conditions,
                            selectedOptions = setOf(uiState.condition),
                            onOptionClick = viewModel::onConditionSelected,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chips_clothing_condition")
                        )
                    }
                }

                BarterEligibilitySwitchCard(
                    isBarterAllowed = uiState.isBarterAllowed,
                    onBarterAllowedChange = viewModel::onBarterAllowedChanged,
                    enabled = !uiState.isLockedInActiveMatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("switch_barter_allowed")
                )

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
                        text = if (uiState.isEditMode) "Simpan Perubahan" else "Simpan ke Lemari",
                        onClick = { viewModel.save(onSaveSuccess) },
                        enabled = !uiState.isLockedInActiveMatch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_clothing")
                    )

                    if (uiState.isEditMode) {
                        TextButton(
                            onClick = viewModel::requestDelete,
                            enabled = !uiState.isLockedInActiveMatch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_delete_clothing")
                        ) {
                            Text(
                                text = "Hapus Pakaian Ini",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (uiState.isLockedInActiveMatch) CloBleColors.Outline else CloBleColors.DangerText
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

        val activeViewerIndex = viewerStartIndex
        if (activeViewerIndex != null && filledPhotos.isNotEmpty()) {
            CloBleFullscreenImageViewer(
                photos = filledPhotos,
                initialIndex = activeViewerIndex,
                onDismiss = { viewerStartIndex = null }
            )
        }
    }
}
