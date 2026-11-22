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
    val isLoading: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val isSaving: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val errorMessage: String? = null
)

class AddEditClothingViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val rawItemId: Long = savedStateHandle.get<Long>("itemId") ?: -1L
    private val isEdit: Boolean = rawItemId > 0L

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
                            }
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Pakaian tidak ditemukan") }
                }
            }
        }
    }

    fun onNameChanged(newName: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(name = newName, errorMessage = null) }
    }

    fun onDescriptionChanged(newDesc: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(description = newDesc, errorMessage = null) }
    }

    fun onCategorySelected(cat: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(category = cat) }
    }

    fun onSizeSelected(s: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(size = s) }
    }

    fun onGenderSelected(g: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(gender = g) }
    }

    fun onColorSelected(c: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(color = c) }
    }

    fun onConditionSelected(cond: String) {
        if (_uiState.value.isLockedInActiveMatch) return
        _uiState.update { it.copy(condition = cond) }
    }

    fun onPhotoSelected(photoUri: String, slotIndex: Int = 0) {
        val current = _uiState.value
        if (current.isLockedInActiveMatch || current.isUploadingPhoto || current.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            val persistedUri = wardrobeRepository.persistLocalPhoto(photoUri)
            if (persistedUri != null) {
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
                        errorMessage = "Gagal mengunggah foto pakaian. Periksa koneksi internet atau pilih foto lain."
                    )
                }
            }
        }
    }

    fun onRemovePhoto(slotIndex: Int) {
        val current = _uiState.value
        if (current.isLockedInActiveMatch || current.isUploadingPhoto || current.isSaving) return
        _uiState.update {
            when (slotIndex) {
                0 -> it.copy(mainPhotoUri = null)
                1 -> it.copy(detailPhotoUri = null)
                else -> it.copy(extraPhotoUri = null)
            }
        }
    }

    fun onBarterAllowedChanged(allowed: Boolean) {
        if (_uiState.value.isLockedInActiveMatch) {
            _uiState.update { it.copy(errorMessage = "Pakaian sedang terikat Match aktif.") }
            return
        }
        _uiState.update { it.copy(isBarterAllowed = allowed) }
    }

    fun requestDelete() {
        if (_uiState.value.isLockedInActiveMatch) {
            _uiState.update { it.copy(errorMessage = "Tidak dapat menghapus pakaian yang sedang terikat Match aktif.") }
            return
        }
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete(onSuccess: () -> Unit) {
        val s = _uiState.value
        val userId = s.currentUserId ?: return
        viewModelScope.launch {
            when (val res = wardrobeRepository.softDeleteClothingItem(userId, s.itemId)) {
                is WardrobeMutationResult.Success -> {
                    _uiState.update { it.copy(showDeleteDialog = false) }
                    onSuccess()
                }
                is WardrobeMutationResult.LockedInActiveMatch -> {
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            errorMessage = "Pakaian sedang dalam Match aktif dan tidak dapat dihapus."
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            errorMessage = "Gagal menghapus pakaian."
                        )
                    }
                }
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isLockedInActiveMatch) {
            _uiState.update { it.copy(errorMessage = "Pakaian sedang dalam Match aktif dan tidak dapat dimodifikasi.") }
            return
        }
        if (s.isUploadingPhoto) {
            _uiState.update { it.copy(errorMessage = "Tunggu hingga proses unggah foto selesai.") }
            return
        }
        val userId = s.currentUserId ?: return
        val name = s.name.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nama pakaian wajib diisi") }
            return
        }

        val draft = ClothingDraft(
            name = name,
            description = s.description.trim(),
            category = s.category,
            size = s.size,
            gender = s.gender,
            color = s.color,
            condition = s.condition,
            mainPhotoUri = s.mainPhotoUri,
            detailPhotoUri = s.detailPhotoUri,
            extraPhotoUri = s.extraPhotoUri,
            isBarterAllowed = s.isBarterAllowed
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = if (s.isEditMode) {
                wardrobeRepository.updateClothingItem(userId, s.itemId, draft)
            } else {
                wardrobeRepository.addClothingItem(userId, draft)
            }

            when (result) {
                is WardrobeMutationResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    onSuccess()
                }
                is WardrobeMutationResult.LockedInActiveMatch -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Pakaian sedang dalam Match aktif dan tidak dapat dimodifikasi."
                        )
                    }
                }
                is WardrobeMutationResult.ValidationFailed -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
                }
                else -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "Gagal menyimpan pakaian.") }
                }
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
                    wardrobeRepository = app.container.wardrobeRepository
                )
            }
        }
    }
}
