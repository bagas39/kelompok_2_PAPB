package com.example.cloble.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
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
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CloBle",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.Primary,
                modifier = Modifier.testTag("app_brand_title")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tukar pakaian preloved mudah dan ramah lingkungan",
                style = MaterialTheme.typography.bodyLarge,
                color = CloBleColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.errorMessage != null) {
                CloBleStatusInfoBox(
                    tone = CloBleInfoBoxTone.HIGHLIGHT,
                    title = "Gagal Masuk",
                    message = uiState.errorMessage!!,
                    modifier = Modifier.fillMaxWidth().testTag("login_error_box")
                )
                Spacer(modifier = Modifier.height(16.dp))
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

            Spacer(modifier = Modifier.height(16.dp))

            CloBleLabeledTextField(
                label = "Password",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                placeholder = "Masukkan password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                inputTestTag = "input_login_password"
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(color = CloBleColors.Primary)
            } else {
                CloBlePrimaryButton(
                    text = "Masuk",
                    onClick = { viewModel.submitLogin(onLoginSuccess) },
                    modifier = Modifier.fillMaxWidth().testTag("btn_submit_login")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Belum punya akun?",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloBleColors.TextSecondary
                )
                TextButton(
                    onClick = onNavigateToRegister,
                    modifier = Modifier.testTag("btn_go_to_register")
                ) {
                    Text(
                        text = "Daftar sekarang",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CloBleColors.Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.demoAccounts.isNotEmpty()) {
                Text(
                    text = "Masuk Cepat Akun Demo",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CloBleColors.TextPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(12.dp))

                uiState.demoAccounts.forEach { demo ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CloBleColors.Outline, RoundedCornerShape(12.dp))
                            .background(CloBleColors.SecondarySurface)
                            .clickable { viewModel.quickLoginDemo(demo.id, onLoginSuccess) }
                            .padding(12.dp)
                            .testTag("demo_account_${demo.name.lowercase().replace(" ", "_")}")
                    ) {
                        Text(
                            text = demo.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = CloBleColors.TextPrimary
                        )
                        Text(
                            text = demo.roleDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
