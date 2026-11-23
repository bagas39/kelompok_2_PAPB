package com.example.cloble.ui.screens.wardrobe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.CloBleConstants
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
import java.io.File

@Composable
fun AddEditClothingScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: AddEditClothingViewModel = viewModel(factory = AddEditClothingViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var activeSlotIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showPhotoSourceDialog by rememberSaveable { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            permissionDeniedMessage = null
            viewModel.onPhotoSelected(uri.toString(), activeSlotIndex)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUriString
        pendingCameraUriString = null
        if (success && !capturedUri.isNullOrBlank()) {
            permissionDeniedMessage = null
            viewModel.onPhotoSelected(capturedUri, activeSlotIndex)
        }
    }

    fun launchCameraCaptureNow() {
        val tempUri = createCameraTempUri(context)
        if (tempUri == null) {
            permissionDeniedMessage = "Gagal menyiapkan file kamera sementara."
            return
        }
        pendingCameraUriString = tempUri.toString()
        try {
            cameraLauncher.launch(tempUri)
        } catch (_: Exception) {
            pendingCameraUriString = null
            permissionDeniedMessage = "Aplikasi kamera tidak tersedia di perangkat ini."
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it } || hasStorageReadPermission(context)
        if (granted) {
            permissionDeniedMessage = null
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            permissionDeniedMessage = "Izin akses penyimpanan/galeri ditolak. Izinkan akses foto & media di Pengaturan Aplikasi untuk memilih foto dari galeri."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || hasCameraPermission(context)) {
            permissionDeniedMessage = null
            launchCameraCaptureNow()
        } else {
            permissionDeniedMessage = "Izin akses kamera ditolak. Izinkan akses kamera di Pengaturan Aplikasi untuk mengambil foto pakaian."
        }
    }

    fun requestGalleryWithPermission() {
        if (hasStorageReadPermission(context)) {
            permissionDeniedMessage = null
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            storagePermissionLauncher.launch(requiredStoragePermissions())
        }
    }

    fun requestCameraWithPermission() {
        if (hasCameraPermission(context)) {
            permissionDeniedMessage = null
            launchCameraCaptureNow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun nextAvailableSlotIndex(): Int = when {
        uiState.mainPhotoUri == null -> 0
        uiState.detailPhotoUri == null -> 1
        uiState.extraPhotoUri == null -> 2
        else -> 0
    }

    val categories = CloBleConstants.CATEGORIES
    val sizes = CloBleConstants.SIZES
    val genders = CloBleConstants.GENDERS
    val colors = CloBleConstants.COLORS
    val conditions = CloBleConstants.CONDITIONS

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
                            text = "Ketuk foto terisi untuk memperbesar, atau ketuk slot kosong untuk mengambil foto dari kamera maupun galeri.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val canModifyPhotos = !uiState.isLockedInActiveMatch && !uiState.isUploadingPhoto && !uiState.isSaving
                            ClothingPhotoSlot(
                                label = "Foto Utama",
                                imageModel = uiState.mainPhotoUri,
                                onClick = {
                                    if (!uiState.mainPhotoUri.isNullOrBlank()) {
                                        openFullscreenForSlot(uiState.mainPhotoUri)
                                    } else if (canModifyPhotos) {
                                        activeSlotIndex = 0
                                        showPhotoSourceDialog = true
                                    }
                                },
                                onRemoveClick = if (uiState.mainPhotoUri != null && canModifyPhotos) { { viewModel.onRemovePhoto(0) } } else null,
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
                                    } else if (canModifyPhotos) {
                                        activeSlotIndex = 1
                                        showPhotoSourceDialog = true
                                    }
                                },
                                onRemoveClick = if (uiState.detailPhotoUri != null && canModifyPhotos) { { viewModel.onRemovePhoto(1) } } else null,
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
                                    } else if (canModifyPhotos) {
                                        activeSlotIndex = 2
                                        showPhotoSourceDialog = true
                                    }
                                },
                                onRemoveClick = if (uiState.extraPhotoUri != null && canModifyPhotos) { { viewModel.onRemovePhoto(2) } } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("photo_slot_extra")
                            )
                        }

                        if (uiState.isUploadingPhoto) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("photo_upload_loading"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = CloBleColors.Primary
                                )
                                Text(
                                    text = "Mengunggah foto pakaian...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val canModifyActions = !uiState.isLockedInActiveMatch && !uiState.isUploadingPhoto && !uiState.isSaving
                            CloBleOutlinedButton(
                                text = if (uiState.isUploadingPhoto) "Mengunggah Foto..." else "Ambil Foto Kamera",
                                leadingIcon = CloBleIcons.Camera,
                                enabled = canModifyActions,
                                onClick = {
                                    activeSlotIndex = nextAvailableSlotIndex()
                                    requestCameraWithPermission()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_take_camera_photo")
                            )
                            CloBleOutlinedButton(
                                text = if (uiState.isUploadingPhoto) "Mengunggah Foto..." else "Pilih dari Galeri",
                                leadingIcon = CloBleIcons.ImagePlaceholder,
                                enabled = canModifyActions,
                                onClick = {
                                    activeSlotIndex = nextAvailableSlotIndex()
                                    requestGalleryWithPermission()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_pick_photo")
                            )
                        }

                        if (!permissionDeniedMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CloBleColors.WarningContainer,
                                border = BorderStroke(1.dp, CloBleColors.WarningText.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("photo_permission_warning_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = permissionDeniedMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloBleColors.WarningText
                                    )
                                    CloBleOutlinedButton(
                                        text = "Buka Pengaturan Izin",
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_open_photo_permission_settings")
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
                        enabled = !uiState.isLockedInActiveMatch && !uiState.isUploadingPhoto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_clothing")
                    )

                    if (uiState.isEditMode) {
                        TextButton(
                            onClick = viewModel::requestDelete,
                            enabled = !uiState.isLockedInActiveMatch && !uiState.isUploadingPhoto,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_delete_clothing")
                        ) {
                            Text(
                                text = "Hapus Pakaian Ini",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (uiState.isLockedInActiveMatch || uiState.isUploadingPhoto) CloBleColors.Outline else CloBleColors.DangerText
                            )
                        }
                    }
                }
            }
        }

        if (showPhotoSourceDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoSourceDialog = false },
                modifier = Modifier.testTag("photo_source_dialog"),
                title = {
                    Text(
                        text = "Pilih Sumber Foto",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Pilih cara mengambil foto pakaian untuk slot ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CloBleColors.TextSecondary
                        )
                        CloBleOutlinedButton(
                            text = "Ambil dari Kamera",
                            leadingIcon = CloBleIcons.Camera,
                            onClick = {
                                showPhotoSourceDialog = false
                                requestCameraWithPermission()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_dialog_camera")
                        )
                        CloBleOutlinedButton(
                            text = "Pilih dari Galeri",
                            leadingIcon = CloBleIcons.ImagePlaceholder,
                            onClick = {
                                showPhotoSourceDialog = false
                                requestGalleryWithPermission()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_dialog_gallery")
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPhotoSourceDialog = false }) {
                        Text(
                            text = "Batal",
                            color = CloBleColors.TextSecondary
                        )
                    }
                },
                containerColor = CloBleColors.Surface
            )
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

private fun requiredStoragePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES
    )
    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
}

private fun hasStorageReadPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val fullGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val partialGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        return fullGranted || partialGranted
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

private fun createCameraTempUri(context: Context): Uri? {
    return try {
        val cameraDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    } catch (_: Exception) {
        null
    }
}
