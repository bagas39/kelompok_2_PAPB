package com.example.cloble.ui.screens.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleLogoMark
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = CloBleColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            CloBleLogoMark(
                size = 68.dp,
                showWordmark = false
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CloBle",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.Primary,
                modifier = Modifier.testTag("app_brand_title")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tukar pakaian preloved dengan aman di sekitarmu",
                style = MaterialTheme.typography.bodyMedium,
                color = CloBleColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CloBleColors.Surface,
                border = BorderStroke(1.dp, CloBleColors.Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.errorMessage != null) {
                        CloBleStatusInfoBox(
                            tone = CloBleInfoBoxTone.WARNING,
                            title = "Gagal Masuk",
                            message = uiState.errorMessage!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_error_box")
                        )
                    }

                    CloBleLabeledTextField(
                        label = "Email",
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        placeholder = "nama@email.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        inputTestTag = "input_login_email"
                    )

                    CloBleLabeledTextField(
                        label = "Password",
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        placeholder = "Masukkan password",
                        visualTransformation = if (uiState.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = viewModel::togglePasswordVisibility,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("btn_toggle_login_password")
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
                        inputTestTag = "input_login_password"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (uiState.isLoading) {
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
                            text = "Masuk",
                            onClick = { viewModel.submitLogin(onLoginSuccess) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_submit_login")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Belum punya akun?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloBleColors.TextSecondary
                )
                TextButton(
                    onClick = onNavigateToRegister,
                    shape = CircleShape,
                    modifier = Modifier.testTag("btn_go_to_register")
                ) {
                    Text(
                        text = "Daftar sekarang",
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.Primary
                    )
                }
            }
        }
    }
}
