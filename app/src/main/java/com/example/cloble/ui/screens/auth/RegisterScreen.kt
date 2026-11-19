package com.example.cloble.ui.screens.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleLocationPickerDialog
import com.example.cloble.ui.components.CloBleLogoMark
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.theme.CloBleColors
import java.util.Locale

@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLocationPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Daftar Akun Baru",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CloBleLogoMark(
                    size = 44.dp,
                    showWordmark = false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mulai Tukar Pakaianmu",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = CloBleColors.TextPrimary
                    )
                    Text(
                        text = "Buat akun dan pilih lokasimu secara bebas di peta",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloBleColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                    if (uiState.errorMessage != null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.WARNING,
                            title = "Pendaftaran Gagal",
                            message = uiState.errorMessage!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_error_box")
                        )
                    }

                    CloBleLabeledTextField(
                        label = "Nama Lengkap",
                        value = uiState.name,
                        onValueChange = viewModel::onNameChanged,
                        placeholder = "Nama Anda",
                        modifier = Modifier.fillMaxWidth(),
                        inputTestTag = "input_register_name"
                    )

                    CloBleLabeledTextField(
                        label = "Email",
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        placeholder = "nama@email.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        inputTestTag = "input_register_email"
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Lokasi Domisili",
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
                                .testTag("btn_open_register_map_picker")
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
                                            "%.4f, %.4f · Ketuk untuk ubah di peta",
                                            uiState.latitude,
                                            uiState.longitude
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CloBleColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

                    CloBleLabeledTextField(
                        label = "Password",
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        placeholder = "Minimal 6 karakter",
                        visualTransformation = if (uiState.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = viewModel::togglePasswordVisibility,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPasswordVisible) CloBleIcons.EyeOff else CloBleIcons.Eye,
                                    contentDescription = if (uiState.isPasswordVisible) "Sembunyikan password" else "Tampilkan password",
                                    tint = CloBleColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        inputTestTag = "input_register_password"
                    )

                    CloBleLabeledTextField(
                        label = "Konfirmasi Password",
                        value = uiState.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChanged,
                        placeholder = "Ulangi password",
                        visualTransformation = if (uiState.isConfirmPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = viewModel::toggleConfirmPasswordVisibility,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isConfirmPasswordVisible) CloBleIcons.EyeOff else CloBleIcons.Eye,
                                    contentDescription = if (uiState.isConfirmPasswordVisible) "Sembunyikan konfirmasi password" else "Tampilkan konfirmasi password",
                                    tint = CloBleColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        inputTestTag = "input_register_confirm_password"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (uiState.isSubmitting) {
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
                            text = "Daftar",
                            onClick = { viewModel.submitRegister(onRegisterSuccess) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_submit_register")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Sudah punya akun?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloBleColors.TextSecondary
                )
                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("btn_go_to_login")
                ) {
                    Text(
                        text = "Masuk",
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.Primary
                    )
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
}
