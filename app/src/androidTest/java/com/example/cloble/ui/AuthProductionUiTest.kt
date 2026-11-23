package com.example.cloble.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.ui.screens.auth.LoginScreen
import com.example.cloble.ui.screens.auth.LoginViewModel
import com.example.cloble.ui.screens.auth.RegisterScreen
import com.example.cloble.ui.screens.auth.RegisterViewModel
import com.example.cloble.ui.theme.CloBleTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthProductionUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeAuthRepository : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = MutableStateFlow(null)
        override fun observeCurrentUser(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun login(email: String, rawPassword: String) = AuthResult.InvalidCredentials
        override suspend fun requestPasswordResetOtp(email: String) = RequestPasswordResetOtpResult.Success
        override suspend fun verifyOtpAndResetPassword(
            email: String,
            otpCode: String,
            newRawPassword: String
        ) = ResetPasswordResult.Success
        override suspend fun changePassword(currentRawPassword: String, newRawPassword: String) =
            ChangePasswordResult.Success
        override suspend fun register(
            name: String,
            email: String,
            rawPassword: String,
            city: String,
            latitude: Double?,
            longitude: Double?
        ) = AuthResult.Success(1L)
        override suspend fun logout() {}
    }

    @Test
    fun loginScreen_rendersStandardLoginFormWithoutDemoShortcuts() {
        val viewModel = LoginViewModel(FakeAuthRepository())
        composeTestRule.setContent {
            CloBleTheme {
                LoginScreen(
                    onNavigateToRegister = {},
                    onLoginSuccess = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag("input_login_email").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_login_password").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_forgot_password").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_submit_login").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_go_to_register").assertIsDisplayed()
    }

    @Test
    fun registerScreen_showsUnselectedLocationPromptAndRequiresLocationOnSubmit() {
        val viewModel = RegisterViewModel(FakeAuthRepository())
        composeTestRule.setContent {
            CloBleTheme {
                RegisterScreen(
                    onNavigateBack = {},
                    onRegisterSuccess = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Pilih lokasi domisilimu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_register_name").performTextInput("Sinta")
        composeTestRule.onNodeWithTag("input_register_email").performTextInput("sinta@cloble.id")
        composeTestRule.onNodeWithTag("btn_submit_register").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("register_error_box").performScrollTo().assertIsDisplayed()
    }
}
