package com.example.cloble.ui.screens.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditClothingUiState(
    val isEditMode: Boolean = false,
    val itemId: Long = -1L,
    val currentUserId: Long? = null,
    val name: String = "",
    val description: String = "",
    val category: String = "Atasan",
    val size: String = "M",
    val gender: String = "Uniseks",
    val color: String = "Hitam",
    val condition: String = "Baik",
    val mainPhotoUri: String? = null,
    val detailPhotoUri: String? = null,
    val extraPhotoUri: String? = null,
    val isBarterAllowed: Boolean = false,
    val isLockedInActiveMatch: Boolean = false,
    val isItemLoadFailed: Boolean = false,
    val isLoading: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val errorMessage: String? = null,
    val errorScrollTrigger: Int = 0
)

class AddEditClothingViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ViewModel() {

    private val rawItemId: Long = savedStateHandle.get<Long>("itemId") ?: -1L
    private val isEdit: Boolean = rawItemId > 0L
    private val uploadedDraftPhotoUris = LinkedHashSet<String>()

    private val _uiState = MutableStateFlow(
        AddEditClothingUiState(
            isEditMode = isEdit,
            itemId = rawItemId,
            isLoading = isEdit
        )
    )
    val uiState: StateFlow<AddEditClothingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            _uiState.update { it.copy(currentUserId = userId) }

            if (isEdit) {
                val item = wardrobeRepository.getClothingItem(rawItemId)
                if (item != null) {
                    val locked = wardrobeRepository.isItemLocked(rawItemId)
                    _uiState.update {
                        it.copy(
                            name = item.name,
                            description = item.description,
                            category = item.category,
                            size = item.size,
                            gender = item.gender,
                            color = item.color,
                            condition = item.condition,
                            mainPhotoUri = item.mainPhotoUri,
                            detailPhotoUri = item.detailPhotoUri,
                            extraPhotoUri = item.extraPhotoUri,
                            isBarterAllowed = item.isBarterAllowed,
                            isLockedInActiveMatch = locked,
                            isLoading = false,
                            errorMessage = if (locked) {
                                "Pakaian ini sedang terikat Match aktif atau sudah ditukar sehingga seluruh datanya terkunci."
                            } else {
                                null
                            },
                            errorScrollTrigger = if (locked) it.errorScrollTrigger + 1 else it.errorScrollTrigger
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isItemLoadFailed = true,
                            errorMessage = "Pakaian tidak ditemukan atau gagal dimuat. Silakan kembali dan coba lagi.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                }
            }
        }
    }

    fun onNameChanged(newName: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(name = newName, errorMessage = null) }
    }

    fun onDescriptionChanged(newDesc: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(description = newDesc, errorMessage = null) }
    }

    fun onCategorySelected(cat: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(category = cat) }
    }

    fun onSizeSelected(s: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(size = s) }
    }

    fun onGenderSelected(g: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(gender = g) }
    }

    fun onColorSelected(c: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(color = c) }
    }

    fun onConditionSelected(cond: String) {
        if (_uiState.value.isLockedInActiveMatch || _uiState.value.isItemLoadFailed) return
        _uiState.update { it.copy(condition = cond) }
    }

    fun onPhotoSelected(photoUri: String, slotIndex: Int = 0) {
        val current = _uiState.value
        if (current.isLockedInActiveMatch || current.isItemLoadFailed || current.isUploadingPhoto || current.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            val isRemoteInput = photoUri.startsWith("http://") || photoUri.startsWith("https://")
            val persistedUri = wardrobeRepository.persistLocalPhoto(photoUri)
            if (persistedUri != null) {
                if (!isRemoteInput) {
                    synchronized(uploadedDraftPhotoUris) {
                        uploadedDraftPhotoUris.add(persistedUri)
                    }
                }
                _uiState.update {
                    when (slotIndex) {
                        0 -> it.copy(mainPhotoUri = persistedUri, isUploadingPhoto = false)
                        1 -> it.copy(detailPhotoUri = persistedUri, isUploadingPhoto = false)
                        else -> it.copy(extraPhotoUri = persistedUri, isUploadingPhoto = false)
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isUploadingPhoto = false,
                        errorMessage = "Gagal mengunggah foto pakaian. Pastikan format JPG/PNG/WebP dan ukuran maksimal 5 MB.",
                        errorScrollTrigger = it.errorScrollTrigger + 1
                    )
                }
            }
        }
    }

    fun onRemovePhoto(slotIndex: Int) {
        val current = _uiState.value
        if (current.isLockedInActiveMatch || current.isItemLoadFailed || current.isUploadingPhoto || current.isSaving) return
        _uiState.update {
            val rawMain = if (slotIndex == 0) null else it.mainPhotoUri
            val rawDetail = if (slotIndex == 1) null else it.detailPhotoUri
            val rawExtra = if (slotIndex != 0 && slotIndex != 1) null else it.extraPhotoUri
            val (normalizedMain, normalizedDetail, normalizedExtra) = normalizePhotoSlots(
                rawMain,
                rawDetail,
                rawExtra
            )
            it.copy(
                mainPhotoUri = normalizedMain,
                detailPhotoUri = normalizedDetail,
                extraPhotoUri = normalizedExtra
            )
        }
    }

    fun onBarterAllowedChanged(allowed: Boolean) {
        if (_uiState.value.isItemLoadFailed) return
        if (_uiState.value.isLockedInActiveMatch) {
            _uiState.update {
                it.copy(
                    errorMessage = "Pakaian sedang terikat Match aktif.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        _uiState.update { it.copy(isBarterAllowed = allowed) }
    }

    fun requestDelete() {
        if (_uiState.value.isItemLoadFailed) {
            _uiState.update {
                it.copy(
                    errorMessage = "Pakaian gagal dimuat dan tidak dapat dihapus.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (_uiState.value.isLockedInActiveMatch) {
            _uiState.update {
                it.copy(
                    errorMessage = "Tidak dapat menghapus pakaian yang sedang terikat Match aktif.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isDeleting || s.isSaving || s.isItemLoadFailed) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            try {
                val userId = s.currentUserId ?: authRepository.observeLoggedInUserId().firstOrNull()
                if (userId == null) {
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            isDeleting = false,
                            errorMessage = "Sesi pengguna tidak ditemukan. Silakan masuk kembali.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(currentUserId = userId) }
                when (val result = wardrobeRepository.softDeleteClothingItem(userId, s.itemId)) {
                    is WardrobeMutationResult.Success -> {
                        _uiState.update { it.copy(showDeleteDialog = false, isDeleting = false) }
                        onSuccess()
                    }
                    is WardrobeMutationResult.LockedInActiveMatch -> {
                        _uiState.update {
                            it.copy(
                                showDeleteDialog = false,
                                isDeleting = false,
                                errorMessage = "Pakaian sedang dalam Match aktif atau sudah ditukar dan tidak dapat dihapus.",
                                errorScrollTrigger = it.errorScrollTrigger + 1
                            )
                        }
                    }
                    is WardrobeMutationResult.InvalidOwnership -> {
                        _uiState.update {
                            it.copy(
                                showDeleteDialog = false,
                                isDeleting = false,
                                errorMessage = "Anda tidak memiliki izin untuk menghapus pakaian ini.",
                                errorScrollTrigger = it.errorScrollTrigger + 1
                            )
                        }
                    }
                    is WardrobeMutationResult.ItemNotFound -> {
                        _uiState.update {
                            it.copy(
                                showDeleteDialog = false,
                                isDeleting = false,
                                errorMessage = "Pakaian tidak ditemukan atau sudah dihapus.",
                                errorScrollTrigger = it.errorScrollTrigger + 1
                            )
                        }
                    }
                    is WardrobeMutationResult.ValidationFailed -> {
                        _uiState.update {
                            it.copy(
                                showDeleteDialog = false,
                                isDeleting = false,
                                errorMessage = result.message,
                                errorScrollTrigger = it.errorScrollTrigger + 1
                            )
                        }
                    }
                    is WardrobeMutationResult.DatabaseError -> {
                        _uiState.update {
                            it.copy(
                                showDeleteDialog = false,
                                isDeleting = false,
                                errorMessage = result.failure.message ?: "Gagal menghapus pakaian.",
                                errorScrollTrigger = it.errorScrollTrigger + 1
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isSaving || s.isDeleting) return
        if (s.isItemLoadFailed) {
            _uiState.update {
                it.copy(
                    errorMessage = "Data pakaian gagal dimuat. Silakan kembali dan muat ulang.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (s.isLockedInActiveMatch) {
            _uiState.update {
                it.copy(
                    errorMessage = "Pakaian sedang dalam Match aktif dan tidak dapat dimodifikasi.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (s.isUploadingPhoto) {
            _uiState.update {
                it.copy(
                    errorMessage = "Tunggu hingga proses unggah foto selesai.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        val name = s.name.trim()
        val description = s.description.trim()

        if (name.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Nama pakaian wajib diisi",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (name.length > 120) {
            _uiState.update {
                it.copy(
                    errorMessage = "Nama pakaian maksimal 120 karakter.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (description.length > 2000) {
            _uiState.update {
                it.copy(
                    errorMessage = "Deskripsi pakaian maksimal 2000 karakter.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }

        val (normalizedMain, normalizedDetail, normalizedExtra) = normalizePhotoSlots(
            s.mainPhotoUri,
            s.detailPhotoUri,
            s.extraPhotoUri
        )

        val draft = ClothingDraft(
            name = name,
            description = description,
            category = s.category,
            size = s.size,
            gender = s.gender,
            color = s.color,
            condition = s.condition,
            mainPhotoUri = normalizedMain,
            detailPhotoUri = normalizedDetail,
            extraPhotoUri = normalizedExtra,
            isBarterAllowed = s.isBarterAllowed
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mainPhotoUri = normalizedMain,
                    detailPhotoUri = normalizedDetail,
                    extraPhotoUri = normalizedExtra,
                    isSaving = true,
                    errorMessage = null
                )
            }
            val userId = s.currentUserId ?: authRepository.observeLoggedInUserId().firstOrNull()
            if (userId == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Sesi pengguna tidak ditemukan. Silakan masuk kembali.",
                        errorScrollTrigger = it.errorScrollTrigger + 1
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(currentUserId = userId) }
            val result = if (s.isEditMode) {
                wardrobeRepository.updateClothingItem(userId, s.itemId, draft)
            } else {
                wardrobeRepository.addClothingItem(userId, draft)
            }

            when (result) {
                is WardrobeMutationResult.Success -> {
                    val keptUris = setOfNotNull(normalizedMain, normalizedDetail, normalizedExtra)
                    val unusedDrafts = synchronized(uploadedDraftPhotoUris) {
                        val stale = uploadedDraftPhotoUris.filter { it !in keptUris }
                        uploadedDraftPhotoUris.clear()
                        stale
                    }
                    if (unusedDrafts.isNotEmpty()) {
                        cleanupScope.launch {
                            wardrobeRepository.deleteUploadedPhotos(unusedDrafts)
                        }
                    }
                    _uiState.update { it.copy(isSaving = false) }
                    onSuccess()
                }
                is WardrobeMutationResult.LockedInActiveMatch -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Pakaian sedang dalam Match aktif atau sudah ditukar dan tidak dapat dimodifikasi.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                }
                is WardrobeMutationResult.InvalidOwnership -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Anda tidak memiliki izin untuk mengubah pakaian ini.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                }
                is WardrobeMutationResult.ItemNotFound -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Pakaian tidak ditemukan atau sudah dihapus.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                }
                is WardrobeMutationResult.ValidationFailed -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.message,
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                }
                is WardrobeMutationResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.failure.message ?: "Gagal menyimpan pakaian.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val staleDrafts = synchronized(uploadedDraftPhotoUris) {
            val copy = uploadedDraftPhotoUris.toList()
            uploadedDraftPhotoUris.clear()
            copy
        }
        if (staleDrafts.isNotEmpty()) {
            cleanupScope.launch {
                wardrobeRepository.deleteUploadedPhotos(staleDrafts)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                val savedStateHandle = createSavedStateHandle()
                AddEditClothingViewModel(
                    savedStateHandle = savedStateHandle,
                    authRepository = app.container.authRepository,
                    wardrobeRepository = app.container.wardrobeRepository,
                    cleanupScope = app.container.cleanupScope
                )
            }
        }
    }
}

internal fun normalizePhotoSlots(
    mainPhotoUri: String?,
    detailPhotoUri: String?,
    extraPhotoUri: String?
): Triple<String?, String?, String?> {
    val cleanMain = mainPhotoUri?.takeIf { it.isNotBlank() }
    val cleanDetail = detailPhotoUri?.takeIf { it.isNotBlank() }
    val cleanExtra = extraPhotoUri?.takeIf { it.isNotBlank() }
    return when {
        cleanMain != null -> Triple(cleanMain, cleanDetail, cleanExtra)
        cleanDetail != null -> Triple(cleanDetail, cleanExtra, null)
        cleanExtra != null -> Triple(cleanExtra, null, null)
        else -> Triple(null, null, null)
    }
}
