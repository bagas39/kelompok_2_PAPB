package com.example.cloble.ui.screens.auth

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.ui.components.CloBleDropdownField
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cityOptions = CityPreset.entries.map { it.displayName }

    Scaffold(
        topBar = {
            CloBleTopAppBar(
                title = "Daftar Akun Baru",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Mulai Tukar Pakaianmu",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Buat akun untuk mulai menjelajah pakaian di sekitarmu",
                style = MaterialTheme.typography.bodySmall,
                color = CloBleColors.TextSecondary,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.errorMessage != null) {
                CloBleStatusInfoBox(
                    tone = CloBleInfoBoxTone.HIGHLIGHT,
                    title = "Pendaftaran Gagal",
                    message = uiState.errorMessage!!,
                    modifier = Modifier.fillMaxWidth().testTag("register_error_box")
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            CloBleLabeledTextField(
                label = "Nama Lengkap",
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                placeholder = "Nama Anda",
                modifier = Modifier.fillMaxWidth(),
                inputTestTag = "input_register_name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            CloBleLabeledTextField(
                label = "Email",
                value = uiState.email,
                onValueChange = viewModel::onEmailChanged,
                placeholder = "nama@email.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                inputTestTag = "input_register_email"
            )

            Spacer(modifier = Modifier.height(16.dp))

            CloBleDropdownField(
                label = "Kota Domisili",
                selectedValue = uiState.city,
                options = cityOptions,
                onOptionSelected = viewModel::onCityChanged,
                modifier = Modifier.fillMaxWidth().testTag("dropdown_register_city")
            )

            Spacer(modifier = Modifier.height(16.dp))

            CloBleLabeledTextField(
                label = "Password",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                placeholder = "Minimal 6 karakter",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                inputTestTag = "input_register_password"
            )

            Spacer(modifier = Modifier.height(16.dp))

            CloBleLabeledTextField(
                label = "Konfirmasi Password",
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                placeholder = "Ulangi password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                inputTestTag = "input_register_confirm_password"
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (uiState.isSubmitting) {
                CircularProgressIndicator(color = CloBleColors.Primary)
            } else {
                CloBlePrimaryButton(
                    text = "Daftar",
                    onClick = { viewModel.submitRegister(onRegisterSuccess) },
                    modifier = Modifier.fillMaxWidth().testTag("btn_submit_register")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Sudah punya akun?",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloBleColors.TextSecondary
                )
                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("btn_go_to_login")
                ) {
                    Text(
                        text = "Masuk",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CloBleColors.Primary
                    )
                }
            }
        }
    }
}
